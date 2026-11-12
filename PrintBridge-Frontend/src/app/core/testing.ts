import { AuthResponse, User } from './models/account.model';
import { PrintRequest, PrintRequestSummary } from './models/print-request.model';

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
