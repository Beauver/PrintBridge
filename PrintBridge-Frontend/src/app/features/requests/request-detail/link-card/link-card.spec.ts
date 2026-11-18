import { TestBed } from '@angular/core/testing';
import { LinkCard } from './link-card';

describe('LinkCard', () => {
  function create(startOpen: boolean, linkName: string | null = null): LinkCard {
    const fixture = TestBed.createComponent(LinkCard);
    fixture.componentRef.setInput('url', 'https://www.printables.com/model/1');
    fixture.componentRef.setInput('linkName', linkName);
    fixture.componentRef.setInput('startOpen', startOpen);
    const card = fixture.componentInstance;
    card.ngOnInit();
    return card;
  }

  it('is labelled with its name, else the host', () => {
    expect(create(true)['label']()).toBe('www.printables.com');
    expect(create(true, 'Lid')['label']()).toBe('Lid');
  });

  it('starts open or closed and toggles', () => {
    const card = create(false);
    expect(card['open']()).toBe(false);
    card['toggle']();
    expect(card['open']()).toBe(true);
    expect(create(true)['open']()).toBe(true);
  });
});
