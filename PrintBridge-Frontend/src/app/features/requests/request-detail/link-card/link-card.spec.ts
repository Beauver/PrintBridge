import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FilamentChoice, FilamentChoiceInput } from '../../../../core/models/spoolman.model';
import { RED_FOR_WHITE } from '../../../../../testing/fixtures';
import { SpoolmanFilamentsService } from '../../spoolman-filaments.service';
import { LinkCard } from './link-card';

describe('LinkCard', () => {
  const WHOLE_RED: FilamentChoice = {
    ...RED_FOR_WHITE,
    wholeItem: true,
    plateIndex: null,
    originalType: null,
    originalColor: null,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: SpoolmanFilamentsService,
          useValue: { filaments: signal(null), loading: signal(false), error: signal(null) },
        },
      ],
    });
  });

  function create(
    startOpen: boolean,
    linkName: string | null = null,
    choices: FilamentChoice[] = [],
    selection = false,
  ): LinkCard {
    const fixture = TestBed.createComponent(LinkCard);
    fixture.componentRef.setInput('url', 'https://www.printables.com/model/1');
    fixture.componentRef.setInput('linkName', linkName);
    fixture.componentRef.setInput('startOpen', startOpen);
    fixture.componentRef.setInput('filamentChoices', choices);
    fixture.componentRef.setInput('filamentSelection', selection);
    fixture.componentRef.setInput('notEnoughMessage', 'Order it');
    const card = fixture.componentInstance;
    card.ngOnInit();
    fixture.detectChanges();
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

  it('shows the circle while choosing is allowed or something was chosen', () => {
    expect(create(true)['showFilament']()).toBe(false);
    expect(create(true, null, [], true)['showFilament']()).toBe(true);
    const chosen = create(true, null, [WHOLE_RED]);
    expect(chosen['showFilament']()).toBe(true);
    expect(chosen['choice']()).toEqual(WHOLE_RED);
    expect(chosen['notEnoughFilament']()).toBeNull();
    expect(create(true, null, [{ ...WHOLE_RED, notEnough: true }])['notEnoughFilament']()).toBe(
      'Order it',
    );
  });

  it('passes choices on to the page', () => {
    const card = create(true, null, [WHOLE_RED], true);
    const chosen: FilamentChoiceInput[] = [];
    const resets: number[] = [];
    card.filamentChosen.subscribe((choice) => {
      chosen.push(choice);
    });
    card.filamentReset.subscribe((id) => {
      resets.push(id);
    });
    const input = {
      wholeItem: true,
      plateIndex: null,
      originalType: null,
      originalColor: null,
      spoolmanFilamentId: 3,
    };

    card['chooseFilament'](input);
    card['resetFilament'](12);

    expect(chosen).toEqual([input]);
    expect(resets).toEqual([12]);
  });
});
