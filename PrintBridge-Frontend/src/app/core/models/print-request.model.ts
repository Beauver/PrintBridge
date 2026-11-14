import { ModelInfo, WeightKind } from './model-info.model';

export type RequestStatus =
  'SUBMITTED' | 'IN_REVIEW' | 'ACCEPTED' | 'PRINTING' | 'DECLINED' | 'COMPLETED';

/** Admin overview filter: open requests, all, or one status. */
export type StatusFilter = 'OPEN' | 'ALL' | RequestStatus;

export type ModelFileType = 'STL' | 'THREE_MF';

/** A row in a request list. `finalPrice` is null while the price is pending. */
export interface PrintRequestSummary {
  id: number;
  name: string;
  status: RequestStatus;
  finalPrice: number | null;
  /** When it was marked paid; null while not paid. */
  paidAt: string | null;
  ownerUsername: string;
  createdAt: string;
}

export interface RequestFile {
  id: number;
  originalFilename: string;
  type: ModelFileType;
  sizeBytes: number;
  /** How many copies to print (1–100). */
  quantity: number;
  createdAt: string;
  modelInfo: ModelInfo;
}

/** The whole request: every file times its quantity. */
export interface RequestTotals {
  files: number;
  /** Sum of the quantities. */
  copies: number;
  /** Over the files whose weight is known; null when none is. */
  grams: number | null;
  weight: WeightKind | null;
  /** Over the files whose print time is known; null when none is. */
  printSeconds: number | null;
  /** Some files have no print time yet (not sliced), so `printSeconds` is too low. */
  printTimePartial: boolean;
  /** False when any file doesn't fit; null when no file has a fit check. */
  fits: boolean | null;
}

export interface PrintRequest {
  id: number;
  name: string;
  description: string | null;
  status: RequestStatus;
  finalPrice: number | null;
  declineReason: string | null;
  paidAt: string | null;
  ownerId: number;
  ownerUsername: string;
  createdAt: string;
  updatedAt: string;
  files: RequestFile[];
  totals: RequestTotals;
  /** Statuses the admin may move this request to; empty once it is final. */
  nextStatuses: RequestStatus[];
}

export interface UploadConfig {
  /** Largest model file. */
  maxSizeBytes: number;
  /** Most files one request may hold. */
  maxFiles: number;
  allowedExtensions: string[];
}

/** A new request with its first file; the other files follow one by one. */
export interface NewPrintRequest {
  name: string;
  description: string;
  file: File;
  /** Copies of the first file. */
  quantity: number;
}

/** A file that couldn't be added to a request, with the reason. */
export interface FailedUpload {
  filename: string;
  reason: string;
}

/** Navigation state the new request form hands the detail page when extra files failed. */
export interface FailedUploadsState {
  failedUploads?: FailedUpload[];
}
