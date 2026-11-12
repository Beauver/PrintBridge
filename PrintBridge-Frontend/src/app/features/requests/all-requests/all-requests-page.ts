import { Component, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { catchError, debounceTime, distinctUntilChanged, of, Subject, switchMap } from 'rxjs';
import { errorMessage } from '../../../core/http/api-error';
import {
  PrintRequestSummary,
  RequestStatus,
  StatusFilter,
} from '../../../core/models/print-request.model';
import { statusLabel } from '../../../shared/print-request-display';
import { PrintRequestsService } from '../print-requests.service';
import { RequestList } from '../request-list/request-list';

const STATUSES: RequestStatus[] = ['SUBMITTED', 'IN_REVIEW', 'ACCEPTED', 'DECLINED', 'COMPLETED'];

/** Typing pause before the search runs, so not every keystroke hits the server. */
const SEARCH_DEBOUNCE_MS = 300;

/** Admin overview of everyone's requests, with a status filter and search. */
@Component({
  selector: 'app-all-requests-page',
  imports: [ReactiveFormsModule, RequestList],
  templateUrl: './all-requests-page.html',
})
export class AllRequestsPage implements OnInit {
  private readonly requestsService = inject(PrintRequestsService);
  private readonly fb = inject(FormBuilder);

  protected readonly statusFilter = this.fb.nonNullable.control<StatusFilter>('OPEN');
  protected readonly search = this.fb.nonNullable.control('');

  protected readonly filterOptions: { value: StatusFilter; label: string }[] = [
    { value: 'OPEN', label: 'Open' },
    { value: 'ALL', label: 'All' },
    ...STATUSES.map((status) => {
      return { value: status, label: statusLabel(status) };
    }),
  ];

  protected readonly requests = signal<PrintRequestSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  /** Every (re)load goes through here; switchMap drops responses of outdated searches. */
  private readonly reload = new Subject<void>();

  constructor() {
    this.reload
      .pipe(
        switchMap(() => {
          this.loading.set(true);
          this.error.set(null);
          return this.requestsService.listAll(this.statusFilter.value, this.search.value).pipe(
            catchError((err: unknown) => {
              this.error.set(errorMessage(err, 'Could not load the requests.'));
              return of([]);
            }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((requests) => {
        this.requests.set(requests);
        this.loading.set(false);
      });

    this.statusFilter.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.load();
    });
    this.search.valueChanges
      .pipe(debounceTime(SEARCH_DEBOUNCE_MS), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => {
        this.load();
      });
  }

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.reload.next();
  }
}
