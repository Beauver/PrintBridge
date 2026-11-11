import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { SetupStatusService } from '../../core/setup/setup-status.service';
import { ADMIN } from '../../core/testing';
import { byCy, submit, type } from '../../core/testing-dom';
import { SetupPage } from './setup-page';

describe('SetupPage', () => {
  let fixture: ComponentFixture<SetupPage>;
  const auth = { setup: vi.fn() };
  const setupStatus = { markDone: vi.fn() };
  const router = { navigate: vi.fn().mockResolvedValue(true) };

  beforeEach(async () => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      imports: [SetupPage],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: SetupStatusService, useValue: setupStatus },
        { provide: Router, useValue: router },
      ],
    });
    fixture = TestBed.createComponent(SetupPage);
    await fixture.whenStable();
  });

  function fill(confirm = 'password123'): void {
    type(fixture, 'setup-username', 'owner');
    type(fixture, 'setup-email', 'owner@example.com');
    type(fixture, 'setup-password', 'password123');
    type(fixture, 'setup-confirm-password', confirm);
  }

  it('creates the admin and goes to the start page', async () => {
    auth.setup.mockReturnValue(of(ADMIN));
    fill();
    submit(fixture, 'setup-form');
    await fixture.whenStable();

    expect(auth.setup).toHaveBeenCalledWith({
      username: 'owner',
      email: 'owner@example.com',
      password: 'password123',
    });
    expect(setupStatus.markDone).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/']);
  });

  it('shows an error when passwords do not match and sends nothing', async () => {
    fill('different1');
    submit(fixture, 'setup-form');
    await fixture.whenStable();

    expect(auth.setup).not.toHaveBeenCalled();
    expect(byCy(fixture, 'setup-confirm-password')!.classList).toContain('is-invalid');
  });

  it('shows field errors for invalid input', async () => {
    submit(fixture, 'setup-form');
    await fixture.whenStable();

    expect(auth.setup).not.toHaveBeenCalled();
    expect(byCy(fixture, 'setup-username')!.classList).toContain('is-invalid');
    expect(byCy(fixture, 'setup-email')!.classList).toContain('is-invalid');
  });

  it('shows the server error', async () => {
    auth.setup.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { message: 'Setup has already been completed' },
          }),
      ),
    );
    fill();
    submit(fixture, 'setup-form');
    await fixture.whenStable();

    expect(byCy(fixture, 'setup-error')!.textContent).toContain('Setup has already been completed');
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
