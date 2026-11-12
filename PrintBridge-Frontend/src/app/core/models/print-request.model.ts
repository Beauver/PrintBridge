export type RequestStatus = 'SUBMITTED' | 'IN_REVIEW' | 'ACCEPTED' | 'DECLINED' | 'COMPLETED';

export type ModelFileType = 'STL' | 'THREE_MF';

/** A row in a request list. `finalPrice` is null while the price is pending. */
export interface PrintRequestSummary {
  id: number;
  name: string;
  status: RequestStatus;
  finalPrice: number | null;
  ownerUsername: string;
  createdAt: string;
}

export interface RequestFile {
  id: number;
  originalFilename: string;
  type: ModelFileType;
  sizeBytes: number;
  createdAt: string;
}

export interface PrintRequest {
  id: number;
  name: string;
  description: string | null;
  status: RequestStatus;
  finalPrice: number | null;
  declineReason: string | null;
  ownerId: number;
  ownerUsername: string;
  createdAt: string;
  updatedAt: string;
  files: RequestFile[];
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
