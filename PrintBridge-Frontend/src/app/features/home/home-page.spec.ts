import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ADMIN, ALICE } from '../../core/testing';
import { byCy } from '../../core/testing-dom';
import { HomePage } from './home-page';

describe('HomePage', () => {
  async function render(user = ALICE) {
    TestBed.configureTestingModule({
      imports: [HomePage],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: { user: signal(user), isAdmin: signal(user.role === 'ADMIN') },
        },
      ],
    });
    const fixture = TestBed.createComponent(HomePage);
    await fixture.whenStable();
    return fixture;
  }

  it('greets the user', async () => {
    const fixture = await render();
    expect(byCy(fixture, 'home-greeting')!.textContent).toContain('alice');
    expect(byCy(fixture, 'home-users-link')).toBeNull();
  });

  it('points the admin to user management', async () => {
    const fixture = await render(ADMIN);
    expect(byCy(fixture, 'home-users-link')).not.toBeNull();
  });
});
