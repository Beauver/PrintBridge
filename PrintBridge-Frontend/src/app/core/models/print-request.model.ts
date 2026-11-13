import { ModelInfo } from './model-info.model';

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
  createdAt: string;
  modelInfo: ModelInfo;
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
  /** Statuses the admin may move this request to; empty once it is final. */
  nextStatuses: RequestStatus[];
}

export interface UploadConfig {
  maxSizeBytes: number;
  allowedExtensions: string[];
}

export interface NewPrintRequest {
  name: string;
  description: string;
  file: File;
}
