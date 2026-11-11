import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth/auth.service';
import { Navbar } from './layout/navbar/navbar';
import { ThemeToggle } from './layout/theme-toggle/theme-toggle';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Navbar, ThemeToggle],
  templateUrl: './app.html',
})
export class App {
  protected readonly auth = inject(AuthService);
}
