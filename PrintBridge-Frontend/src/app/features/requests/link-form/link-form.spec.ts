import { TestBed } from '@angular/core/testing';
import { NewLink } from '../../../core/models/print-request.model';
import { LinkForm } from './link-form';

describe('LinkForm', () => {
  let form: LinkForm;
  let added: NewLink[];
  let cancelled: number;

  function type(field: 'setUrl' | 'setName', value: string): void {
    form[field]({ target: { value } } as unknown as Event);
  }

  beforeEach(() => {
    added = [];
    cancelled = 0;
    form = TestBed.createComponent(LinkForm).componentInstance;
    form.added.subscribe((link) => {
      added.push(link);
    });
    form.cancelled.subscribe(() => {
      cancelled++;
    });
  });

  it('hands over a valid link, trimmed, and clears the fields', () => {
    type('setUrl', ' https://www.patreon.com/posts/1 ');
    type('setName', ' Dragon ');
    form['add']();
    expect(added).toEqual([{ url: 'https://www.patreon.com/posts/1', name: 'Dragon' }]);
    expect(form['url']()).toBe('');
    expect(form['name']()).toBe('');
  });

  it('shows why a link is refused until it is changed', () => {
    type('setUrl', 'www.example.com');
    form['add']();
    expect(added).toEqual([]);
    expect(form['error']()).toBe('The link must start with http:// or https://');
    type('setUrl', 'https://www.example.com');
    expect(form['error']()).toBeNull();

    type('setName', 'n'.repeat(101));
    form['add']();
    expect(form['error']()).toBe('The link name must be at most 100 characters');
    type('setName', 'Lid');
    expect(form['error']()).toBeNull();
  });

  it('adds on Enter instead of submitting the surrounding form', () => {
    const event = { preventDefault: vi.fn() } as unknown as Event;
    type('setUrl', 'https://example.com/a');
    form['onEnter'](event);
    expect(event.preventDefault).toHaveBeenCalled();
    expect(added).toEqual([{ url: 'https://example.com/a', name: '' }]);
  });

  it('can be cancelled', () => {
    form['cancel']();
    expect(cancelled).toBe(1);
  });

  it('gives every form its own field ids', () => {
    const other = TestBed.createComponent(LinkForm).componentInstance;
    expect(other['formId']).not.toBe(form['formId']);
  });
});
