/** Test fixtures shared by the specs; not part of the app build (see tsconfig.app.json). */
import { AuthResponse, User } from '../app/core/models/account.model';
import { ModelInfo, SlicingInfo } from '../app/core/models/model-info.model';
import { PreviewMesh } from '../app/core/models/model-preview.model';
import {
  PrintRequest,
  PrintRequestSummary,
  RequestLink,
  UploadedFile,
} from '../app/core/models/print-request.model';
import { Printer, Settings } from '../app/core/models/settings.model';
import { SlicerPrinter } from '../app/core/models/slicer.model';
import { FilamentChoice, SpoolmanFilament } from '../app/core/models/spoolman.model';

export const ADMIN: User = {
  id: 1,
  username: 'admin',
  email: 'admin@example.com',
  role: 'ADMIN',
  createdAt: '2026-01-01T00:00:00Z',
};
export const ALICE: User = {
  id: 2,
  username: 'alice',
  email: 'alice@example.com',
  role: 'USER',
  createdAt: '2026-01-02T00:00:00Z',
};

export function authResponse(user: User, token = 'access-token'): AuthResponse {
  return { accessToken: token, expiresIn: 900, user };
}

export const UPLOADED_FILE: UploadedFile = {
  id: 7,
  kind: 'FILE',
  originalFilename: 'benchy.stl',
  type: 'STL',
  sizeBytes: 2048,
  url: null,
  linkName: null,
  quantity: 1,
  status: 'PENDING',
  declineReason: null,
  nextStatuses: ['ACCEPTED', 'DECLINED'],
  createdAt: '2026-02-01T10:00:00Z',
  filamentChoices: [],
  modelInfo: {
    status: 'PENDING',
    plates: [],
    slicing: { status: null },
  } as unknown as ModelInfo,
};

/** A link (#59) without a name. */
export const LINK: RequestLink = {
  id: 9,
  kind: 'LINK',
  originalFilename: null,
  type: null,
  sizeBytes: null,
  url: 'https://www.printables.com/model/1',
  linkName: null,
  quantity: 2,
  status: 'PENDING',
  declineReason: null,
  nextStatuses: ['ACCEPTED', 'DECLINED'],
  createdAt: '2026-02-01T10:00:00Z',
  modelInfo: null,
  filamentChoices: [],
};

export const REQUEST: PrintRequest = {
  id: 4,
  name: 'Benchy',
  description: 'Red please',
  status: 'SUBMITTED',
  finalPrice: null,
  declineReason: null,
  paidAt: null,
  ownerId: 2,
  ownerUsername: 'alice',
  createdAt: '2026-02-01T10:00:00Z',
  updatedAt: '2026-02-01T10:00:00Z',
  files: [UPLOADED_FILE],
  totals: {
    files: 1,
    links: 0,
    declined: 0,
    completed: 0,
    copies: 1,
    grams: null,
    weight: null,
    printSeconds: null,
    printTimePartial: false,
    fits: null,
  },
  nextStatuses: ['IN_REVIEW', 'ACCEPTED', 'DECLINED'],
  filamentSelection: false,
  notEnoughFilamentMessage: 'Order it',
};

export const REQUEST_SUMMARY: PrintRequestSummary = {
  id: 4,
  name: 'Benchy',
  status: 'SUBMITTED',
  finalPrice: null,
  paidAt: null,
  ownerUsername: 'alice',
  createdAt: '2026-02-01T10:00:00Z',
  itemsCompleted: 0,
  itemsActive: 1,
};

/** Server slicing switched off, file never queued. */
export const NO_SLICING: SlicingInfo = {
  enabled: false,
  status: null,
  printer: null,
  nozzle: null,
  madeForPrinter: null,
  madeForNozzle: null,
  custom: false,
  process: null,
  filament: null,
  error: null,
};

/** An analysed STL: one plate of 20 × 10 × 5 mm with an estimated weight. */
export const STL_INFO: ModelInfo = {
  status: 'ANALYZED',
  source: 'MESH',
  volumeCm3: 1,
  totalGrams: 4,
  weight: 'ESTIMATED',
  printSeconds: null,
  fits: null,
  fitPrinter: null,
  plates: [
    {
      index: 1,
      name: null,
      width: 20,
      depth: 10,
      height: 5,
      grams: 4,
      estimated: true,
      printSeconds: null,
      fits: null,
      filaments: [],
      sliceError: null,
    },
  ],
  slicing: NO_SLICING,
};

/** No printers, slicing off. */
export const NO_SETTINGS: Settings = {
  slicing: { enabled: false, timeoutMinutes: 10 },
  defaultPrinterId: null,
  printers: [],
  uploads: { maxFiles: 10, maxFileSizeMb: 100, ceilingMb: 100 },
  spoolman: { enabled: false, colorSelection: false, notEnoughMessage: 'Order it' },
};

/** The P1S as the slicer lists it (0.2, 0.4 and 0.6 mm nozzles). */
export const SLICER_P1S: SlicerPrinter = {
  model: 'Bambu Lab P1S',
  vendor: 'BBL',
  nozzles: [0.2, 0.4, 0.6].map((diameter) => {
    return { diameter, machine: `P1S ${diameter}`, bed: { x: 256, y: 256, z: 250 } };
  }),
};

/** A saved P1S, ready for slicing. */
export const SAVED_P1S: Printer = {
  id: 1,
  model: 'Bambu Lab P1S',
  buildPlate: { x: 256, y: 256, z: 250 },
  ownedNozzles: [0.2, 0.4],
  defaultNozzle: 0.4,
  process: '0.20mm Standard',
  filament: 'PLA Basic',
};

/** Two plates: plate 1 "Lid" with one red triangle, plate 2 empty; on a 256 × 256 × 250 bed. */
export const TRIANGLE_MESH: PreviewMesh = {
  bed: { x: 256, y: 256, z: 250 },
  plates: [
    { index: 1, name: 'Lid' },
    { index: 2, name: null },
  ],
  parts: [
    {
      plate: 1,
      color: '#FF0000',
      positions: new Float32Array([0, 0, 0, 10, 0, 0, 0, 10, 5]),
      indices: new Uint32Array([0, 1, 2]),
    },
  ],
};

/** Writes a mesh the way the backend's `MeshEncoder` does (without the gzip). */
export function encodeMesh(mesh: PreviewMesh): ArrayBuffer {
  const header = {
    bed: mesh.bed,
    plates: mesh.plates,
    parts: mesh.parts.map((part) => {
      return {
        plate: part.plate,
        color: part.color,
        vertices: part.positions.length / 3,
        triangles: part.indices.length / 3,
      };
    }),
  };
  let json = JSON.stringify(header);
  while (new TextEncoder().encode(json).length % 4 !== 0) {
    json += ' ';
  }
  const headerBytes = new TextEncoder().encode(json);
  const bodyLength = mesh.parts.reduce((total, part) => {
    return total + part.positions.byteLength + part.indices.byteLength;
  }, 0);
  const buffer = new ArrayBuffer(8 + headerBytes.length + bodyLength);
  const bytes = new Uint8Array(buffer);
  bytes.set(new TextEncoder().encode('PBM1'), 0);
  new DataView(buffer).setUint32(4, headerBytes.length, true);
  bytes.set(headerBytes, 8);
  let offset = 8 + headerBytes.length;
  for (const part of mesh.parts) {
    bytes.set(
      new Uint8Array(part.positions.buffer, part.positions.byteOffset, part.positions.byteLength),
      offset,
    );
    offset += part.positions.byteLength;
    bytes.set(
      new Uint8Array(part.indices.buffer, part.indices.byteOffset, part.indices.byteLength),
      offset,
    );
    offset += part.indices.byteLength;
  }
  return buffer;
}

/** A red PLA in Spoolman with 412 g left (#11). */
export const SPOOLMAN_RED: SpoolmanFilament = {
  id: 3,
  name: 'PLA Basic Red',
  vendor: 'Acme',
  material: 'PLA',
  color: '#FF0000',
  colors: [],
  gramsLeft: 412,
};

/** Red PLA chosen for the white PLA on plate 1, with enough left. */
export const RED_FOR_WHITE: FilamentChoice = {
  id: 12,
  wholeItem: false,
  plateIndex: 1,
  originalType: 'PLA',
  originalColor: '#FFFFFF',
  spoolmanFilamentId: 3,
  name: 'PLA Basic Red',
  vendor: 'Acme',
  material: 'PLA',
  color: '#FF0000',
  colors: [],
  chosenAt: '2026-02-01T10:00:00Z',
  orphaned: false,
  inSpoolman: true,
  gramsLeft: 412,
  gramsNeeded: 33.69,
  notEnough: false,
};
