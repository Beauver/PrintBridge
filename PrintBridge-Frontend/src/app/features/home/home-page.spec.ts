import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ALICE } from '../../core/testing';
import { HomePage } from './home-page';

describe('HomePage', () => {
  it('creates', () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { user: signal(ALICE), isAdmin: signal(false) } },
      ],
    });
    expect(TestBed.createComponent(HomePage).componentInstance).toBeTruthy();
  });
});
