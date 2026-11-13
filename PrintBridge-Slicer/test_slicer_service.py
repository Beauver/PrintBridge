"""Unit tests for slicer_service: run with `python3 -m unittest -v` in this folder (no OrcaSlicer needed)."""

import json
import os
import subprocess
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer
from unittest import mock

import slicer_service as service


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as file:
        json.dump(data, file)


def profiles_fixture(root):
    """A tiny profile tree: one vendor, a printer with two nozzles, a process and two filaments."""
    vendor = os.path.join(root, "Acme")
    write_json(os.path.join(vendor, "machine", "common.json"), {
        "name": "common", "printable_area": ["0x0", "250x0", "250x210", "0x210"], "printable_height": "220"})
    write_json(os.path.join(vendor, "machine", "Acme One 0.4 nozzle.json"), {
        "name": "Acme One 0.4 nozzle", "inherits": "common", "instantiation": "true",
        "printer_model": "Acme One", "nozzle_diameter": ["0.4"]})
    write_json(os.path.join(vendor, "machine", "Acme One 0.6 nozzle.json"), {
        "name": "Acme One 0.6 nozzle", "inherits": "common", "instantiation": "true",
        "printer_model": "Acme One", "nozzle_diameter": ["0.6"]})
    write_json(os.path.join(vendor, "machine", "broken.json"), {
        "name": "Broken", "instantiation": "true", "printer_model": "Broken"})
    with open(os.path.join(vendor, "machine", "garbage.json"), "w", encoding="utf-8") as file:
        file.write("{not json")
    write_json(os.path.join(vendor, "process", "base.json"), {"name": "base", "layer_height": "0.2"})
    write_json(os.path.join(vendor, "process", "0.20mm Standard.json"), {
        "name": "0.20mm Standard", "inherits": "base", "instantiation": "true",
        "compatible_printers": ["Acme One 0.4 nozzle"]})
    write_json(os.path.join(vendor, "filament", "nested", "PLA.json"), {
        "name": "Acme PLA", "instantiation": "true", "compatible_printers": ["Acme One 0.4 nozzle"]})
    write_json(os.path.join(vendor, "filament", "Generic.json"), {"name": "Generic PLA", "instantiation": "true"})
    write_json(os.path.join(vendor, "filament", "Conditional.json"), {
        "name": "Conditional", "instantiation": "true", "compatible_printers_condition": "nozzle_diameter[0]==0.6"})


def plate_output(out_dir, plate, code=0, error="", weight=12.5, time=600.4, filaments=None):
    """What OrcaSlicer writes for one sliced plate."""
    write_json(os.path.join(out_dir, "result.json"), {"return_code": code, "error_string": error})
    if code != 0:
        return
    if filaments is None:
        filaments = [{"id": 1, "weight": weight}, {"id": 2, "weight": 0}]
    write_json(os.path.join(out_dir, f"plate_{plate}.statistics.json"),
               {"summary": {"weight": weight, "time": time, "filaments": filaments}})
    with open(os.path.join(out_dir, f"plate_{plate}.gcode"), "w", encoding="utf-8") as file:
        file.write("; HEADER_BLOCK_START\n; CONFIG_BLOCK_START\n"
                   "; filament_type = PLA;PETG\n; filament_colour = \"#FFFFFF\";#FF0000\nG1 X0\n")


def fake_runner(commands, code_by_plate=None, timeout_on=None):
    """Stands in for subprocess.run: records the command and writes OrcaSlicer's output."""
    def run(command, timeout, capture_output, check, cwd):
        commands.append(command)
        plate = int(command[command.index("--slice") + 1])
        if plate == timeout_on:
            raise subprocess.TimeoutExpired(command, timeout)
        out_dir = command[command.index("--outputdir") + 1]
        code = (code_by_plate or {}).get(plate, 0)
        plate_output(out_dir, plate, code=code, error="Refused")
        return subprocess.CompletedProcess(command, code, b"", b"")
    return run


class ProfileIndexTest(unittest.TestCase):

    def setUp(self):
        self.root = tempfile.mkdtemp()
        profiles_fixture(self.root)
        self.index = service.ProfileIndex(self.root)

    def test_resolves_inheritance(self):
        machine = self.index.resolve("machine", "Acme One 0.4 nozzle")
        self.assertEqual("220", machine["printable_height"])
        self.assertEqual("system", machine["from"])
        self.assertNotIn("inherits", machine)
        self.assertIsNone(self.index.resolve("machine", "Unknown"))

    def test_lists_printers_with_nozzles_and_bed(self):
        printers = self.index.printers()
        self.assertEqual(["Acme One"], [printer["model"] for printer in printers])
        self.assertEqual([0.4, 0.6], [nozzle["diameter"] for nozzle in printers[0]["nozzles"]])
        self.assertEqual({"x": 250, "y": 210, "z": 220}, printers[0]["nozzles"][0]["bed"])

    def test_finds_machine_for_printer_and_nozzle(self):
        self.assertEqual("Acme One 0.6 nozzle", self.index.machine_for("Acme One", 0.6))
        with self.assertRaises(service.BadRequest):
            self.index.machine_for("Acme One", 0.8)

    def test_lists_compatible_profiles(self):
        self.assertEqual(["0.20mm Standard"], self.index.compatible("process", "Acme One 0.4 nozzle"))
        self.assertEqual(["Acme PLA", "Generic PLA"], self.index.compatible("filament", "Acme One 0.4 nozzle"))
        self.assertEqual(["Generic PLA"], self.index.compatible("filament", "Acme One 0.6 nozzle"))

    def test_writes_full_profiles(self):
        target = tempfile.mkdtemp()
        count = service.write_full_profiles(self.index, target)
        self.assertEqual(9, count)
        with open(os.path.join(target, "Acme", "process_full", "0.20mm Standard.json"), encoding="utf-8") as file:
            self.assertEqual("0.2", json.load(file)["layer_height"])

    def test_missing_folder_gives_empty_index(self):
        self.assertEqual([], service.ProfileIndex(os.path.join(self.root, "missing")).printers())


class HelperTest(unittest.TestCase):

    def test_numbers_and_beds(self):
        self.assertEqual(0.4, service.first_number(["0.4", "0.6"]))
        self.assertIsNone(service.first_number([]))
        self.assertIsNone(service.first_number("x"))
        self.assertIsNone(service.bed_size({"printable_area": ["nope"]}))

    def test_parses_plates_and_overrides(self):
        self.assertEqual([1, 3], service.parse_plates("1, 3"))
        for invalid in ("", "0", "x", "501"):
            with self.assertRaises(service.BadRequest):
                service.parse_plates(invalid)
        self.assertEqual({}, service.parse_overrides(""))
        self.assertEqual({"wall_loops": "3"}, service.parse_overrides('{"wall_loops": 3}'))
        for invalid in ("{", "[]", '{"machine_start_gcode": "x"}'):
            with self.assertRaises(service.BadRequest):
                service.parse_overrides(invalid)

    def test_number_params(self):
        self.assertEqual(5, service.number_param({}, "timeout", default=5))
        self.assertEqual(0.4, service.number_param({"nozzle": ["0.4"]}, "nozzle"))
        for params in ({}, {"nozzle": ["x"]}, {"nozzle": ["9"]}):
            with self.assertRaises(service.BadRequest):
                service.number_param(params, "nozzle", maximum=5)

    def test_reads_plate_results(self):
        out = tempfile.mkdtemp()
        plate_output(out, 2, weight=12.345, time=600.4)
        self.assertEqual({"index": 2, "grams": 12.35, "seconds": 600,
                          "filaments": [{"type": "PLA", "color": "#FFFFFF", "grams": 12.35}]},
                         service.parse_plate_result(out, 2))

    def test_reads_refusals_and_missing_output(self):
        refused = tempfile.mkdtemp()
        plate_output(refused, 1, code=-50, error="Nothing to slice ")
        self.assertEqual({"index": 1, "error": "Nothing to slice"}, service.parse_plate_result(refused, 1))
        crashed = tempfile.mkdtemp()
        result = service.parse_plate_result(crashed, 1, b"line 1\nterminate called")
        self.assertEqual("OrcaSlicer could not slice this plate", result["error"])
        no_stats = tempfile.mkdtemp()
        write_json(os.path.join(no_stats, "result.json"), {"return_code": 0})
        self.assertIn("no statistics", service.parse_plate_result(no_stats, 1)["error"])

    def test_falls_back_to_other_plate_file_names(self):
        out = tempfile.mkdtemp()
        plate_output(out, 1)
        self.assertEqual(12.5, service.parse_plate_result(out, 4)["grams"])
        self.assertEqual(([], []), service.filament_config(None))
        self.assertEqual([], service.config_list("nothing", "filament_type"))
        self.assertIsNone(service.value_at(["", "PLA"], 0))
        self.assertEqual("a | b", service.last_lines("x\na\nb", 2))


class SlicerTest(unittest.TestCase):

    def setUp(self):
        root = tempfile.mkdtemp()
        profiles_fixture(root)
        self.index = service.ProfileIndex(root)
        self.commands = []

    def runner(self, code_by_plate=None, timeout_on=None):
        return fake_runner(self.commands, code_by_plate, timeout_on)

    def params(self, **values):
        params = {"printer": ["Acme One"], "nozzle": ["0.4"]}
        params.update({key: [value] for key, value in values.items()})
        return params

    def test_slices_each_plate_separately(self):
        slicer = service.Slicer(self.index, self.runner({2: -50}))
        result = slicer.slice("/tmp/model.3mf", self.params(plates="1,2,3"))
        self.assertEqual("Acme One 0.4 nozzle", result["machine"])
        self.assertEqual([1, 2, 3], [plate["index"] for plate in result["plates"]])
        self.assertEqual("Refused", result["plates"][1]["error"])
        self.assertEqual(3, len(self.commands))
        self.assertIn("--load-settings", self.commands[0])
        self.assertNotIn("--arrange", self.commands[0])

    def test_uses_profiles_overrides_and_arrange(self):
        slicer = service.Slicer(self.index, self.runner())
        slicer.slice("/tmp/model.stl", self.params(process="0.20mm Standard", filament="Acme PLA",
                                                   filamentSlots="3", arrange="1",
                                                   overrides='{"wall_loops": "4"}'))
        command = self.commands[0]
        settings = command[command.index("--load-settings") + 1].split(";")
        self.assertEqual(2, len(settings))
        self.assertEqual(3, len(command[command.index("--load-filaments") + 1].split(";")))
        self.assertEqual("1", command[command.index("--arrange") + 1])

    def test_process_profile_replaces_values_orca_rejects(self):
        loaded = []

        def runner(command, timeout, capture_output, check, cwd):
            settings = command[command.index("--load-settings") + 1].split(";")
            with open(settings[1], encoding="utf-8") as file:
                loaded.append(json.load(file))
            return fake_runner(self.commands)(command, timeout, capture_output, check, cwd)

        service.Slicer(self.index, runner).slice("/tmp/model.3mf", self.params(process="0.20mm Standard"))
        self.assertEqual("2", loaded[0]["raft_first_layer_expansion"])

    def test_keeps_the_project_printer(self):
        slicer = service.Slicer(self.index, self.runner())
        slicer.slice("/tmp/model.3mf", self.params(projectPrinter="1"))
        self.assertNotIn("--load-settings", self.commands[0])

    def test_rejects_unknown_profiles(self):
        slicer = service.Slicer(self.index, self.runner())
        with self.assertRaises(service.BadRequest):
            slicer.slice("/tmp/model.stl", self.params(process="Nope"))
        with self.assertRaises(service.BadRequest):
            slicer.slice("/tmp/model.stl", self.params(filament="Nope"))

    def test_times_out(self):
        slicer = service.Slicer(self.index, self.runner(timeout_on=1))
        with self.assertRaises(service.TimedOut):
            slicer.slice("/tmp/model.stl", self.params())
        with mock.patch.object(service.time, "monotonic", side_effect=[0, 1000]):
            with self.assertRaises(service.TimedOut):
                service.Slicer(self.index, self.runner()).slice("/tmp/model.stl", self.params(timeout="10"))

    def test_one_job_at_a_time(self):
        slicer = service.Slicer(self.index, self.runner())
        slicer.lock.acquire()
        with self.assertRaises(service.Busy):
            slicer.slice("/tmp/model.stl", self.params())


class HttpTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        root = tempfile.mkdtemp()
        profiles_fixture(root)
        index = service.ProfileIndex(root)
        cls.slicer = service.Slicer(index)
        service.Handler.index = index
        service.Handler.slicer = cls.slicer
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), service.Handler)
        cls.url = f"http://127.0.0.1:{cls.server.server_address[1]}"
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()

    def call(self, path, body=None):
        request = urllib.request.Request(self.url + path, data=body, method="POST" if body is not None else "GET")
        try:
            with urllib.request.urlopen(request) as response:
                return response.status, json.load(response)
        except urllib.error.HTTPError as error:
            with error:
                return error.code, json.load(error)

    def test_get_endpoints(self):
        self.assertEqual(200, self.call("/health")[0])
        self.assertEqual("Acme One", self.call("/printers")[1][0]["model"])
        status, profiles = self.call("/profiles?printer=Acme%20One&nozzle=0.4")
        self.assertEqual((200, ["0.20mm Standard"]), (status, profiles["processes"]))
        self.assertEqual(400, self.call("/profiles?printer=Nope&nozzle=0.4")[0])
        self.assertEqual(404, self.call("/nope")[0])

    def test_slice_endpoint(self):
        self.slicer.runner = fake_runner([])
        status, result = self.call("/slice?format=stl&printer=Acme%20One&nozzle=0.4", b"solid x")
        self.assertEqual(200, status)
        self.assertEqual(12.5, result["plates"][0]["grams"])

    def test_slice_errors(self):
        self.assertEqual(404, self.call("/other", b"x")[0])
        self.assertEqual(400, self.call("/slice?format=obj&printer=Acme%20One&nozzle=0.4", b"x")[0])
        self.assertEqual(400, self.call("/slice?format=stl&printer=Nope&nozzle=0.4", b"x")[0])
        self.assertEqual(400, self.call("/slice?format=stl", b"")[0])
        with mock.patch.object(service, "MAX_UPLOAD_BYTES", 1):
            self.assertEqual(413, self.call("/slice?format=stl&printer=Acme%20One&nozzle=0.4", b"xx")[0])
        self.slicer.lock.acquire()
        try:
            self.assertEqual(503, self.call("/slice?format=stl&printer=Acme%20One&nozzle=0.4", b"x")[0])
        finally:
            self.slicer.lock.release()
        self.slicer.runner = fake_runner([], timeout_on=1)
        self.assertEqual(504, self.call("/slice?format=stl&printer=Acme%20One&nozzle=0.4", b"x")[0])


if __name__ == "__main__":
    unittest.main()
