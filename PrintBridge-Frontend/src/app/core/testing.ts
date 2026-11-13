import { AuthResponse, User } from './models/account.model';
import { ModelInfo, SlicingInfo } from './models/model-info.model';
import { PrintRequest, PrintRequestSummary } from './models/print-request.model';
import { Printer, Settings } from './models/settings.model';
import { SlicerPrinter } from './models/slicer.model';

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
  files: [
    {
      id: 7,
      originalFilename: 'benchy.stl',
      type: 'STL',
      sizeBytes: 2048,
      createdAt: '2026-02-01T10:00:00Z',
      modelInfo: {
        status: 'PENDING',
        plates: [],
        slicing: { status: null },
      } as unknown as ModelInfo,
    },
  ],
  nextStatuses: ['IN_REVIEW', 'ACCEPTED', 'DECLINED'],
};

export const REQUEST_SUMMARY: PrintRequestSummary = {
  id: 4,
  name: 'Benchy',
  status: 'SUBMITTED',
  finalPrice: null,
  paidAt: null,
  ownerUsername: 'alice',
  createdAt: '2026-02-01T10:00:00Z',
};

/** Server slicing switched off, file never queued. */
export const NO_SLICING: SlicingInfo = {
  enabled: false,
  status: null,
  printer: null,
  nozzle: null,
  madeForPrinter: null,
  madeForNozzle: null,
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
