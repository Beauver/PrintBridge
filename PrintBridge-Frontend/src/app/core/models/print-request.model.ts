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

/** An uploaded STL/3MF of a request. */
export interface UploadedFile {
  id: number;
  kind: 'FILE';
  originalFilename: string;
  type: ModelFileType;
  sizeBytes: number;
  url: null;
  linkName: null;
  /** How many copies to print (1–100). */
  quantity: number;
  createdAt: string;
  modelInfo: ModelInfo;
}

/** A link to a model elsewhere (#59); the owner downloads it themselves. */
export interface RequestLink {
  id: number;
  kind: 'LINK';
  originalFilename: null;
  type: null;
  sizeBytes: null;
  /** An http(s) URL. */
  url: string;
  /** Optional; the URL's host is shown without one. */
  linkName: string | null;
  quantity: number;
  createdAt: string;
  modelInfo: null;
}

/** An entry of a request: a file or a link. They share the quantity and the per-request limit. */
export type RequestFile = UploadedFile | RequestLink;

/** The whole request: every file and link times its quantity. */
export interface RequestTotals {
  /** Uploaded files. */
  files: number;
  links: number;
  /** Sum of the quantities, links included. */
  copies: number;
  /** Over the files whose weight is known; null when none is. */
  grams: number | null;
  /** Never exact while there are links. */
  weight: WeightKind | null;
  /** Over the files whose print time is known; null when none is. */
  printSeconds: number | null;
  /** Some files or links have no print time, so `printSeconds` is too low. */
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

/** A link as the requester enters it (#59). */
export interface NewLink {
  url: string;
  /** Optional; empty for none. */
  name: string;
}

/** What to add to a request: a model file or a link, with its number of copies. */
export type NewEntry =
  | { kind: 'file'; file: File; quantity: number }
  | { kind: 'link'; link: NewLink; quantity: number };

/** A new request with its first file or link; the others follow one by one. */
export interface NewPrintRequest {
  name: string;
  description: string;
  first: NewEntry;
}

/** A file or link that couldn't be added to a request, with the reason. */
export interface FailedUpload {
  /** The file name, or the link's name or host. */
  filename: string;
  reason: string;
}

/** Navigation state the new request form hands the detail page when extra files failed. */
export interface FailedUploadsState {
  failedUploads?: FailedUpload[];
}
