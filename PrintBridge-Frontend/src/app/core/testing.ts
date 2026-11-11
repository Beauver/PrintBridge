import { AuthResponse, User } from './models';

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
