"""
PrintBridge slicer service: a small HTTP wrapper around the OrcaSlicer command line.

Only the Python standard library is used. The backend decides *what* to slice with which
printer/profiles; this service only knows how to run OrcaSlicer and read its output.

Endpoints:
  GET  /health                          -> {"status": "ok", "orcaVersion": ...}
  GET  /printers                        -> printer models with their nozzle sizes and bed size
  GET  /profiles?printer=..&nozzle=..   -> process and filament profiles for that printer + nozzle
  POST /slice?...                       -> body: the model file; slices plate by plate (see do_slice)
"""

import glob
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import threading
import time
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

ORCA = os.environ.get("ORCA_BINARY", "/opt/orca/AppRun")
ORCA_VERSION = os.environ.get("ORCA_VERSION", "unknown")
PROFILES_DIR = os.environ.get("PROFILES_DIR", "/opt/profiles")
PORT = int(os.environ.get("PORT", "8000"))
MAX_UPLOAD_BYTES = int(os.environ.get("MAX_UPLOAD_MB", "200")) * 1024 * 1024
KINDS = ("machine", "process", "filament")
# Settings the backend may override on a process profile (the designer's choices it keeps).
ALLOWED_OVERRIDES = {"sparse_infill_density", "wall_loops", "enable_support"}
# How much of the start of a G-code file to read for its config block (filament types and colours);
# OrcaSlicer writes it right after the header, before thumbnails and moves.
GCODE_HEAD_BYTES = 4 * 1024 * 1024


class BadRequest(Exception):
    """The request can't be served as asked (unknown profile, invalid parameter, ...)."""


class ProfileIndex:
    """
    OrcaSlicer's system profiles (resources/profiles/<vendor>/<kind>/*.json). Profiles inherit
    from each other ("inherits"); resolve() merges the chain so the CLI gets complete files.
    Only profiles with "instantiation": "true" are offered to users.
    """

    def __init__(self, root):
        self.by_vendor = {}  # (vendor, kind, name) -> raw profile
        self.by_name = {}  # (kind, name) -> raw profile (first vendor wins), for cross-vendor parents
        for vendor in sorted(os.listdir(root)) if os.path.isdir(root) else []:
            for kind in KINDS:
                pattern = os.path.join(root, vendor, kind, "**", "*.json")
                for path in sorted(glob.glob(pattern, recursive=True)):
                    try:
                        with open(path, encoding="utf-8") as file:
                            profile = json.load(file)
                    except (OSError, ValueError):
                        continue
                    if not isinstance(profile, dict):
                        continue
                    name = profile.get("name") or os.path.splitext(os.path.basename(path))[0]
                    profile["_vendor"] = vendor
                    self.by_vendor[(vendor, kind, name)] = profile
                    self.by_name.setdefault((kind, name), profile)
        self._printers = None

    def _raw(self, vendor, kind, name):
        return self.by_vendor.get((vendor, kind, name)) or self.by_name.get((kind, name))

    def resolve(self, kind, name, vendor=None):
        """The profile with all inherited settings merged in; None if unknown."""
        profile = self._raw(vendor, kind, name) if vendor else self.by_name.get((kind, name))
        if profile is None:
            return None
        chain = []
        seen = set()
        while profile is not None and id(profile) not in seen:
            seen.add(id(profile))
            chain.append(profile)
            parent = profile.get("inherits")
            profile = self._raw(profile["_vendor"], kind, parent) if parent else None
        merged = {}
        for item in reversed(chain):
            merged.update(item)
        merged.pop("inherits", None)
        merged.pop("_vendor", None)
        merged["name"] = name
        merged["from"] = "system"
        return merged

    def instantiated(self, kind):
        for (vendor, profile_kind, name), profile in self.by_vendor.items():
            if profile_kind == kind and str(profile.get("instantiation", "")).lower() == "true":
                yield vendor, name

    def printers(self):
        """Printer models, each with the nozzle sizes it has a machine profile for."""
        if self._printers is None:
            models = {}
            for vendor, name in self.instantiated("machine"):
                machine = self.resolve("machine", name, vendor)
                model = machine.get("printer_model")
                nozzle = first_number(machine.get("nozzle_diameter"))
                bed = bed_size(machine)
                if not model or nozzle is None or bed is None:
                    continue
                entry = models.setdefault(model, {"model": model, "vendor": vendor, "nozzles": []})
                entry["nozzles"].append({"diameter": nozzle, "machine": name, "bed": bed})
            for entry in models.values():
                entry["nozzles"].sort(key=lambda nozzle: nozzle["diameter"])
            self._printers = sorted(models.values(), key=lambda entry: entry["model"].lower())
        return self._printers

    def machine_for(self, printer, nozzle):
        for entry in self.printers():
            if entry["model"] == printer:
                for candidate in entry["nozzles"]:
                    if abs(candidate["diameter"] - nozzle) < 1e-6:
                        return candidate["machine"]
        raise BadRequest(f"No profile for {printer} with a {nozzle} mm nozzle")

    def compatible(self, kind, machine):
        """Instantiated profiles of a kind that list this machine as compatible, sorted by name."""
        names = set()
        for vendor, name in self.instantiated(kind):
            profile = self.resolve(kind, name, vendor)
            printers = profile.get("compatible_printers") or []
            condition = profile.get("compatible_printers_condition") or ""
            if machine in printers or (not printers and not condition):
                names.add(name)
        return sorted(names, key=str.lower)


def write_full_profiles(index, target):
    """
    Writes every profile with its inherited settings merged in, as
    <target>/<vendor>/<kind>_full/<name>.json. When OrcaSlicer's command line slices a project
    with another printer, it re-reads the project's process and filament presets from there; the
    AppImage doesn't ship these files. Returns the number written.
    """
    count = 0
    for (vendor, kind, name), _ in index.by_vendor.items():
        path = os.path.join(target, vendor, f"{kind}_full", f"{name}.json")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as file:
            json.dump(index.resolve(kind, name, vendor), file)
        count += 1
    return count


def first_number(value):
    if isinstance(value, list):
        value = value[0] if value else None
    try:
        return round(float(value), 2)
    except (TypeError, ValueError):
        return None


def bed_size(machine):
    """Width/depth from "printable_area" (["0x0", "256x0", ...]) and height, in whole mm."""
    try:
        points = [tuple(float(part) for part in corner.split("x")) for corner in machine["printable_area"]]
        width = max(x for x, _ in points) - min(x for x, _ in points)
        depth = max(y for _, y in points) - min(y for _, y in points)
        return {"x": round(width), "y": round(depth), "z": round(float(machine["printable_height"]))}
    except (KeyError, TypeError, ValueError):
        return None


def parse_plate_result(out_dir, plate, output=""):
    """
    Reads what OrcaSlicer wrote for one plate: result.json (success or the refusal reason),
    plate_N.statistics.json (time and weight per filament) and the G-code's config block
    (filament types and colours, in slot order).
    """
    result_path = os.path.join(out_dir, "result.json")
    try:
        with open(result_path, encoding="utf-8") as file:
            result = json.load(file)
    except (OSError, ValueError):
        result = {}
    code = result.get("return_code", -1)
    if code != 0:
        message = (result.get("error_string") or "").strip() or "OrcaSlicer could not slice this plate"
        if not result and output:
            print(f"Plate {plate}: no result.json; OrcaSlicer said: {last_lines(output)}", flush=True)
        return {"index": plate, "error": message}

    stats_path = os.path.join(out_dir, f"plate_{plate}.statistics.json")
    if not os.path.exists(stats_path):
        candidates = glob.glob(os.path.join(out_dir, "plate_*.statistics.json"))
        if not candidates:
            return {"index": plate, "error": "OrcaSlicer wrote no statistics for this plate"}
        stats_path = candidates[0]
    with open(stats_path, encoding="utf-8") as file:
        summary = json.load(file).get("summary", {})

    types, colors = filament_config(gcode_path(out_dir, plate))
    filaments = []
    for filament in summary.get("filaments", []):
        if float(filament.get("weight", 0)) <= 0:
            continue  # a slot the plate doesn't use
        slot = int(filament.get("id", 1)) - 1
        filaments.append({
            "type": value_at(types, slot),
            "color": value_at(colors, slot),
            "grams": round(float(filament.get("weight", 0)), 2),
        })
    return {
        "index": plate,
        "grams": round(float(summary.get("weight", 0)), 2),
        "seconds": int(round(float(summary.get("time", 0)))),
        "filaments": filaments,
    }


def last_lines(output, count=5):
    if isinstance(output, bytes):
        output = output.decode("utf-8", errors="ignore")
    return " | ".join(output.strip().splitlines()[-count:])


def gcode_path(out_dir, plate):
    path = os.path.join(out_dir, f"plate_{plate}.gcode")
    if os.path.exists(path):
        return path
    candidates = glob.glob(os.path.join(out_dir, "plate_*.gcode"))
    return candidates[0] if candidates else None


def filament_config(path):
    """filament_type and filament_colour from the config block near the start of the G-code."""
    if not path:
        return [], []
    with open(path, "rb") as file:
        head = file.read(GCODE_HEAD_BYTES).decode("utf-8", errors="ignore")
    return config_list(head, "filament_type"), config_list(head, "filament_colour")


def config_list(text, key):
    match = re.search(rf"^; {key} = (.*)$", text, re.MULTILINE)
    if not match:
        return []
    return [part.strip().strip('"') for part in match.group(1).split(";")]


def value_at(values, index):
    if 0 <= index < len(values) and values[index]:
        return values[index]
    return None


def number_param(params, name, default=None, minimum=None, maximum=None):
    raw = params.get(name, [None])[0]
    if raw is None or raw == "":
        if default is None:
            raise BadRequest(f"Missing parameter: {name}")
        return default
    try:
        value = float(raw)
    except ValueError as error:
        raise BadRequest(f"Invalid number for {name}") from error
    if (minimum is not None and value < minimum) or (maximum is not None and value > maximum):
        raise BadRequest(f"{name} out of range")
    return value


class Slicer:
    """Runs OrcaSlicer one plate at a time, so a plate it refuses doesn't lose the others."""

    def __init__(self, index, runner=subprocess.run):
        self.index = index
        self.runner = runner
        self.lock = threading.Lock()

    def slice(self, model_path, params):
        """
        Parameters (query string):
          printer, nozzle      the printer model and nozzle size to slice for (required)
          projectPrinter=1     keep the printer settings saved in the project (same printer and nozzle)
          process              process profile to use instead of the project's (optional)
          overrides            JSON with a few process settings to keep from the project (optional)
          filament             filament profile to use (optional)
          filamentSlots        how many filament slots get that profile (default 1)
          plates               comma-separated plate numbers (default 1)
          arrange=1            re-arrange the objects on the plate (another bed size, STL files)
          timeout              seconds for the whole file (default 600)
        """
        printer = params.get("printer", [""])[0]
        nozzle = number_param(params, "nozzle", minimum=0.05, maximum=5)
        timeout = number_param(params, "timeout", default=600, minimum=10, maximum=3600)
        plates = parse_plates(params.get("plates", ["1"])[0])
        project_printer = params.get("projectPrinter", ["0"])[0] == "1"
        process = params.get("process", [""])[0]
        filament = params.get("filament", [""])[0]
        filament_slots = int(number_param(params, "filamentSlots", default=1, minimum=1, maximum=32))
        overrides = parse_overrides(params.get("overrides", [""])[0])
        arrange = params.get("arrange", ["0"])[0] == "1"
        machine = self.index.machine_for(printer, nozzle)

        if not self.lock.acquire(blocking=False):
            raise Busy()
        try:
            with tempfile.TemporaryDirectory(prefix="slice-") as work:
                options = self._profile_options(work, machine, project_printer, process, overrides,
                                                filament, filament_slots)
                if arrange:
                    options += ["--arrange", "1"]
                results = self._slice_plates(work, model_path, plates, options, time.monotonic() + timeout)
                return {"printer": printer, "nozzle": nozzle, "machine": machine, "plates": results}
        finally:
            self.lock.release()

    def _profile_options(self, work, machine, project_printer, process, overrides, filament, filament_slots):
        settings = []
        if not project_printer:
            settings.append(write_profile(work, "machine", self.index.resolve("machine", machine)))
        if process:
            profile = self.index.resolve("process", process)
            if profile is None:
                raise BadRequest(f"Unknown process profile: {process}")
            profile.update(overrides)
            settings.append(write_profile(work, "process", profile))
        options = []
        if settings:
            options += ["--load-settings", ";".join(settings)]
        if filament:
            profile = self.index.resolve("filament", filament)
            if profile is None:
                raise BadRequest(f"Unknown filament profile: {filament}")
            path = write_profile(work, "filament", profile)
            options += ["--load-filaments", ";".join([path] * filament_slots)]
        return options

    def _slice_plates(self, work, model_path, plates, options, deadline):
        results = []
        for plate in plates:
            remaining = deadline - time.monotonic()
            if remaining <= 1:
                raise TimedOut()
            out_dir = os.path.join(work, f"out-{plate}")
            os.makedirs(out_dir)
            command = [ORCA, "--slice", str(plate), "--export-statistics", "--outputdir", out_dir,
                       "--allow-newer-file", "--mstpp", str(int(remaining))] + options + [model_path]
            try:
                # OrcaSlicer may write log files into the current directory.
                completed = self.runner(command, timeout=remaining, capture_output=True, check=False, cwd=work)
            except subprocess.TimeoutExpired as error:
                raise TimedOut() from error
            output = (completed.stdout or b"") + (completed.stderr or b"")
            results.append(parse_plate_result(out_dir, plate, output))
            shutil.rmtree(out_dir, ignore_errors=True)
        return results


class Busy(Exception):
    pass


class TimedOut(Exception):
    pass


def parse_plates(text):
    try:
        plates = [int(part) for part in text.split(",") if part.strip()]
    except ValueError as error:
        raise BadRequest("Invalid plates") from error
    if not plates or any(plate < 1 or plate > 500 for plate in plates):
        raise BadRequest("Invalid plates")
    return plates


def parse_overrides(text):
    if not text:
        return {}
    try:
        overrides = json.loads(text)
    except ValueError as error:
        raise BadRequest("Invalid overrides") from error
    if not isinstance(overrides, dict) or not set(overrides) <= ALLOWED_OVERRIDES:
        raise BadRequest("Invalid overrides")
    return {key: str(value) for key, value in overrides.items()}


def write_profile(work, kind, profile):
    path = os.path.join(work, f"{kind}.json")
    with open(path, "w", encoding="utf-8") as file:
        json.dump(profile, file)
    return path


class Handler(BaseHTTPRequestHandler):
    index = None
    slicer = None

    def do_GET(self):
        url = urlparse(self.path)
        params = parse_qs(url.query)
        try:
            if url.path == "/health":
                self.send_json({"status": "ok", "orcaVersion": ORCA_VERSION})
            elif url.path == "/printers":
                self.send_json(self.index.printers())
            elif url.path == "/profiles":
                machine = self.index.machine_for(params.get("printer", [""])[0], number_param(params, "nozzle"))
                self.send_json({
                    "machine": machine,
                    "processes": self.index.compatible("process", machine),
                    "filaments": self.index.compatible("filament", machine),
                })
            else:
                self.send_json({"error": "Not found"}, HTTPStatus.NOT_FOUND)
        except BadRequest as error:
            self.send_json({"error": str(error)}, HTTPStatus.BAD_REQUEST)

    def do_POST(self):
        url = urlparse(self.path)
        if url.path != "/slice":
            self.send_json({"error": "Not found"}, HTTPStatus.NOT_FOUND)
            return
        params = parse_qs(url.query)
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0:
            self.send_json({"error": "No file"}, HTTPStatus.BAD_REQUEST)
            return
        if length > MAX_UPLOAD_BYTES:
            self.send_json({"error": "File too large"}, HTTPStatus.REQUEST_ENTITY_TOO_LARGE)
            return
        extension = params.get("format", ["stl"])[0]
        if extension not in ("stl", "3mf"):
            self.send_json({"error": "format must be stl or 3mf"}, HTTPStatus.BAD_REQUEST)
            return
        with tempfile.TemporaryDirectory(prefix="upload-") as upload_dir:
            model_path = os.path.join(upload_dir, f"model.{extension}")
            with open(model_path, "wb") as file:
                remaining = length
                while remaining > 0:
                    chunk = self.rfile.read(min(remaining, 1024 * 1024))
                    if not chunk:
                        break
                    file.write(chunk)
                    remaining -= len(chunk)
            try:
                self.send_json(self.slicer.slice(model_path, params))
            except BadRequest as error:
                self.send_json({"error": str(error)}, HTTPStatus.BAD_REQUEST)
            except Busy:
                self.send_json({"error": "The slicer is busy"}, HTTPStatus.SERVICE_UNAVAILABLE)
            except TimedOut:
                self.send_json({"error": "Slicing timed out"}, HTTPStatus.GATEWAY_TIMEOUT)

    def send_json(self, body, status=HTTPStatus.OK):
        data = json.dumps(body).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, format, *args):  # noqa: A002 - signature from BaseHTTPRequestHandler
        print(f"{self.address_string()} {format % args}", flush=True)


def main():
    index = ProfileIndex(PROFILES_DIR)
    if len(sys.argv) == 3 and sys.argv[1] == "--write-full-profiles":
        print(f"Wrote {write_full_profiles(index, sys.argv[2])} full profiles", flush=True)
        return
    Handler.index = index
    Handler.slicer = Slicer(index)
    print(f"Slicer service on port {PORT}: OrcaSlicer {ORCA_VERSION}, "
          f"{len(index.printers())} printer models", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()


if __name__ == "__main__":
    main()
