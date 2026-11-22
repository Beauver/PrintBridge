export type Role = 'USER' | 'ADMIN';

export interface User {
  id: number;
  username: string;
  email: string;
  role: Role;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  /** Seconds until the access token expires. */
  expiresIn: number;
  user: User;
}

export interface NewAccount {
  username: string;
  email: string;
  password: string;
}

/** An admin updating another user. `newPassword` is only sent when `changePassword` is true. */
export interface UserUpdate {
  username: string;
  email: string;
  changePassword: boolean;
  newPassword?: string;
}

/** A user updating their own account; always confirmed with the current password. */
export interface AccountUpdate extends UserUpdate {
  currentPassword: string;
}
