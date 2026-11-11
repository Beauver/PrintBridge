import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ALICE } from '../../core/testing';
import { UsersService } from './users.service';

describe('UsersService', () => {
  let service: UsersService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(UsersService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('calls the users API', () => {
    service.getAll().subscribe();
    http.expectOne({ method: 'GET', url: '/api/users' }).flush([ALICE]);

    const account = { username: 'bob', email: 'bob@example.com', password: 'password1' };
    service.create(account).subscribe();
    const create = http.expectOne({ method: 'POST', url: '/api/users' });
    expect(create.request.body).toEqual(account);
    create.flush(ALICE);

    service.resetPassword(2, 'new-password').subscribe();
    const reset = http.expectOne({ method: 'PUT', url: '/api/users/2/password' });
    expect(reset.request.body).toEqual({ password: 'new-password' });
    reset.flush(null);

    service.delete(2).subscribe();
    http.expectOne({ method: 'DELETE', url: '/api/users/2' }).flush(null);
  });
});
