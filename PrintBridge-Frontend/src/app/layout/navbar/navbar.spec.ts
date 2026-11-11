import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { ADMIN, ALICE } from '../../core/testing';
import { byCy, click } from '../../core/testing-dom';
import { Navbar } from './navbar';

describe('Navbar', () => {
  const logout = vi.fn();

  async function render(user = ALICE) {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      imports: [Navbar],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: { user: signal(user), isAdmin: signal(user.role === 'ADMIN'), logout },
        },
      ],
    });
    const fixture = TestBed.createComponent(Navbar);
    await fixture.whenStable();
    return fixture;
  }

  it('shows the users link only to the admin', async () => {
    expect(byCy(await render(ALICE), 'nav-users')).toBeNull();
    TestBed.resetTestingModule();
    expect(byCy(await render(ADMIN), 'nav-users')).not.toBeNull();
  });

  it('shows the username', async () => {
    expect(byCy(await render(), 'nav-username')!.textContent).toContain('alice');
  });

  it('opens and closes the mobile menu', async () => {
    const fixture = await render();
    click(fixture, 'nav-toggle');
    await fixture.whenStable();
    expect(byCy(fixture, 'nav-toggle')!.getAttribute('aria-expanded')).toBe('true');
    click(fixture, 'nav-home');
    await fixture.whenStable();
    expect(byCy(fixture, 'nav-toggle')!.getAttribute('aria-expanded')).toBe('false');
  });

  it('logs out and goes to the login page', async () => {
    const fixture = await render();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    logout.mockReturnValue(of(undefined));
    click(fixture, 'logout');
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('goes to the login page even if logout fails', async () => {
    const fixture = await render();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    logout.mockReturnValue(throwError(() => new Error('offline')));
    click(fixture, 'logout');
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });
});
