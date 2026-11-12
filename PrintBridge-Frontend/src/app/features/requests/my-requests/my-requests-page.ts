import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { errorMessage } from '../../../core/http/api-error';
import { PrintRequestSummary } from '../../../core/models/print-request.model';
import { PrintRequestsService } from '../print-requests.service';
import { RequestList } from '../request-list/request-list';

/** Start page after login: the logged-in user's own print requests. */
@Component({
  selector: 'app-my-requests-page',
  imports: [RouterLink, RequestList],
  templateUrl: './my-requests-page.html',
})
export class MyRequestsPage implements OnInit {
  private readonly requestsService = inject(PrintRequestsService);
  protected readonly auth = inject(AuthService);

  protected readonly requests = signal<PrintRequestSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.requestsService.listOwn().subscribe({
      next: (requests) => {
        this.requests.set(requests);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(errorMessage(err, 'Could not load your requests.'));
        this.loading.set(false);
      },
    });
  }
}
