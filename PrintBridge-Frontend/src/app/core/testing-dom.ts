import { ComponentFixture } from '@angular/core/testing';

/** Small DOM helpers for component tests, selecting by data-cy. */
export function byCy<T extends Element = HTMLElement>(
  fixture: ComponentFixture<unknown>,
  cy: string,
): T | null {
  return (fixture.nativeElement as HTMLElement).querySelector<T>(`[data-cy="${cy}"]`) as T | null;
}

export function type(fixture: ComponentFixture<unknown>, cy: string, value: string): void {
  const input = byCy<HTMLInputElement>(fixture, cy)!;
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

export function click(fixture: ComponentFixture<unknown>, cy: string): void {
  byCy<HTMLElement>(fixture, cy)!.click();
}

export function submit(fixture: ComponentFixture<unknown>, cy: string): void {
  byCy<HTMLFormElement>(fixture, cy)!.dispatchEvent(new Event('submit'));
}
