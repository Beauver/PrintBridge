import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { NewAccount, User, UserUpdate } from '../../core/models/account.model';

/** Admin account management API. */
@Injectable({ providedIn: 'root' })
export class UsersService {
  private readonly http = inject(HttpClient);

  getAll(): Observable<User[]> {
    return this.http.get<User[]>('/api/users');
  }

  create(account: NewAccount): Observable<User> {
    return this.http.post<User>('/api/users', account);
  }

  /** Updates another user; a new password logs them out everywhere. */
  update(id: number, update: UserUpdate): Observable<User> {
    return this.http.put<User>(`/api/users/${id}`, update);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/users/${id}`);
  }
}
