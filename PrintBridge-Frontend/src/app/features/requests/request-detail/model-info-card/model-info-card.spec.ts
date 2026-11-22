import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ModelInfo, PlateInfo, SlicingInfo } from '../../../../core/models/model-info.model';
import { NO_SLICING, STL_INFO } from '../../../../../testing/fixtures';
import { TRIANGLE_MESH } from '../../../../../testing/fixtures';
import { ModelPreviewService } from '../../model-preview.service';
import { FilamentChoice, FilamentChoiceInput } from '../../../../core/models/spoolman.model';
import { RED_FOR_WHITE } from '../../../../../testing/fixtures';
import { SpoolmanFilamentsService } from '../../spoolman-filaments.service';
import { ModelInfoCard } from './model-info-card';

describe('ModelInfoCard', () => {
  const plate: PlateInfo = STL_INFO.plates[0];
  const SLICED: SlicingInfo = {
    ...NO_SLICING,
    enabled: true,
    status: 'DONE',
    printer: 'Bambu Lab P1S',
    nozzle: 0.4,
  };

  const BAMBU: ModelInfo = {
    ...STL_INFO,
    source: 'BAMBU',
    volumeCm3: 63.2,
    totalGrams: 65.25,
    weight: 'EXACT',
    printSeconds: 5720,
    fits: false,
    fitPrinter: 'Bambu Lab P1S',
    slicing: SLICED,
    plates: [
      {
        ...plate,
        name: 'Lid',
        grams: 33.69,
        estimated: false,
        printSeconds: 2963,
        fits: false,
        filaments: [{ type: 'PLA', color: '#FFFFFF', grams: 33.69 }],
      },
      {
        ...plate,
        index: 2,
        width: null,
        depth: null,
        height: null,
        grams: null,
        estimated: null,
      },
      { ...plate, index: 3, grams: 4.4, estimated: true, sliceError: 'Object conflicts' },
    ],
  };

  const previewService = { fileMesh: vi.fn(), thumbnail: vi.fn() };

  beforeEach(() => {
    vi.clearAllMocks();
    previewService.thumbnail.mockReturnValue(
      throwError(() => {
        return new Error('No image');
      }),
    );
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: ModelPreviewService, useValue: previewService },
        {
          provide: SpoolmanFilamentsService,
          useValue: { filaments: signal(null), loading: signal(false), error: signal(null) },
        },
      ],
    });
  });

  function createFixture(
    info: ModelInfo,
    isAdmin = false,
    choices: FilamentChoice[] = [],
    selection = false,
    quantity = 1,
  ) {
    const fixture = TestBed.createComponent(ModelInfoCard);
    fixture.componentRef.setInput('info', info);
    fixture.componentRef.setInput('filamentChoices', choices);
    fixture.componentRef.setInput('filamentSelection', selection);
    fixture.componentRef.setInput('notEnoughMessage', 'Order it');
    fixture.componentRef.setInput('quantity', quantity);
    fixture.componentRef.setInput('isAdmin', isAdmin);
    fixture.componentRef.setInput('requestId', 4);
    fixture.componentRef.setInput('fileId', 7);
    fixture.detectChanges();
    return fixture;
  }

  function create(info: ModelInfo, isAdmin = false): ModelInfoCard {
    return createFixture(info, isAdmin).componentInstance;
  }

  it('shows size, volume and the estimate for an STL', () => {
    const card = create(STL_INFO);

    expect(card['analyzed']()).toBe(true);
    expect(card['showPlates']()).toBe(false);
    expect(card['dimensions']()).toBe('20 × 10 × 5 mm');
    expect(card['volume']()).toBe('1 cm³');
    expect(card['weight']()).toEqual({ text: '~4 g', note: '(estimate)' });
    expect(card['printTime']()).toBeNull();
    expect(card['filaments']()).toEqual([]);
    expect(card['noFitText']()).toBeNull();
    expect(card['slicedFor']()).toBeNull();
  });

  it('shows exact numbers and plates for a sliced project', () => {
    const card = create(BAMBU);

    expect(card['showPlates']()).toBe(true);
    expect(card['plateCount']()).toBe('3 plates');
    expect(card['weight']()).toEqual({ text: '65,25 g', note: null });
    expect(card['printTime']()).toBe('1 h 35 min');
    expect(card['noFitText']()).toBe('Too big for Bambu Lab P1S');
    expect(card['slicedFor']()).toBe('Bambu Lab P1S · 0,4 mm nozzle');
  });

  it('marks partly estimated totals', () => {
    const card = create({ ...BAMBU, weight: 'PARTLY_ESTIMATED', totalGrams: 38.4 });

    expect(card['weight']()).toEqual({ text: '~38 g', note: '(partly estimated)' });
  });

  it('says what a project was made for when it was sliced on something else', () => {
    function card(slicing: Partial<SlicingInfo>): ModelInfoCard {
      return create({ ...BAMBU, slicing: { ...SLICED, ...slicing } });
    }

    expect(card({ madeForNozzle: 0.8 })['madeFor']()).toBe('Bambu Lab P1S · 0,8 mm nozzle');
    expect(card({ madeForPrinter: 'Bambu Lab X1 Carbon' })['madeFor']()).toBe(
      'Bambu Lab X1 Carbon · 0,4 mm nozzle',
    );
    expect(card({ madeForPrinter: 'Bambu Lab A1', madeForNozzle: 0.2 })['madeFor']()).toBe(
      'Bambu Lab A1 · 0,2 mm nozzle',
    );
    expect(card({ printer: null, madeForNozzle: 0.2 })['madeFor']()).toBe('0,2 mm nozzle');
    expect(card({ madeForPrinter: 'Bambu Lab A1' })['slicedFor']()).toBe(
      'Bambu Lab P1S · 0,4 mm nozzle',
    );
    expect(card({})['madeFor']()).toBeNull();
    expect(card({ status: 'QUEUED', madeForNozzle: 0.2 })['madeFor']()).toBeNull();
    expect(card({ printer: null })['slicedFor']()).toBe('0,4 mm nozzle');
  });

  it('shows the profiles of a custom re-slice', () => {
    function slicedFor(slicing: Partial<SlicingInfo>): string | null {
      return create({ ...BAMBU, slicing: { ...SLICED, custom: true, ...slicing } })['slicedFor']();
    }

    expect(slicedFor({ process: '0.12mm Fine @BBL P1S', filament: 'Bambu PLA Basic' })).toBe(
      'Bambu Lab P1S · 0,4 mm nozzle · 0.12mm Fine @BBL P1S · Bambu PLA Basic',
    );
    expect(slicedFor({ process: null, filament: null })).toBe(
      "Bambu Lab P1S · 0,4 mm nozzle · project's settings · project's filaments",
    );
  });

  it('says a model is too big even without knowing the printer', () => {
    expect(create({ ...BAMBU, fitPrinter: null })['noFitText']()).toBe(
      "Doesn't fit the build plate",
    );
  });

  it('describes each plate', () => {
    const card = create(BAMBU);
    const [lid, empty, refused] = BAMBU.plates;

    expect(card['plateLabel'](lid)).toBe('1. Lid');
    expect(card['plateLabel'](empty)).toBe('Plate 2');
    expect(card['plateDimensions'](lid)).toBe('20 × 10 × 5 mm');
    expect(card['plateDimensions'](empty)).toBeNull();
    expect(card['plateGrams'](lid)).toBe('33,69 g');
    expect(card['plateGrams'](empty)).toBeNull();
    expect(card['plateGrams'](refused)).toBe('~4 g');
    expect(card['plateTime'](lid)).toBe('49 min');
    expect(card['plateTime'](empty)).toBeNull();
  });

  it('labels filaments with what is known', () => {
    const card = create(BAMBU);

    expect(card['filamentLabel']({ type: 'PLA', color: '#FFFFFF', grams: 33.69 })).toBe(
      'PLA · #FFFFFF · 33,69 g',
    );
    expect(card['filamentLabel']({ type: null, color: null, grams: null })).toBe(
      'Unknown filament',
    );
  });

  it('handles pending and failed files', () => {
    const pending: ModelInfo = {
      ...STL_INFO,
      status: 'PENDING',
      volumeCm3: null,
      totalGrams: null,
      plates: [],
    };
    const card = create(pending, true);

    expect(card['analyzed']()).toBe(false);
    expect(card['dimensions']()).toBeNull();
    expect(card['filaments']()).toEqual([]);
    expect(card['volume']()).toBeNull();
    expect(card['weight']()).toBeNull();
    expect(card['showPlateSizeHint']()).toBe(false);
  });

  it('hints the admin to set the build plate size', () => {
    expect(create(STL_INFO, true)['showPlateSizeHint']()).toBe(true);
    expect(create(STL_INFO, false)['showPlateSizeHint']()).toBe(false);
    expect(create(BAMBU, true)['showPlateSizeHint']()).toBe(false);
  });

  describe('server slicing', () => {
    it('shows when a file is being sliced', () => {
      const queued = create(
        { ...STL_INFO, slicing: { ...NO_SLICING, enabled: true, status: 'QUEUED' } },
        true,
      );
      const running = create({
        ...STL_INFO,
        slicing: { ...NO_SLICING, enabled: true, status: 'RUNNING' },
      });

      expect(queued['slicingPending']()).toBe(true);
      expect(running['slicingPending']()).toBe(true);
      expect(queued['canReslice']()).toBe(false);
    });

    it('lets the admin re-slice with chosen settings while slicing is on', () => {
      const card = create(BAMBU, true);
      expect(card['canReslice']()).toBe(true);
      const withEmitted = vi.fn();
      card.resliceWith.subscribe(withEmitted);
      card['requestResliceWith']();
      expect(withEmitted).toHaveBeenCalled();
      expect(create(BAMBU, false)['canReslice']()).toBe(false);
      expect(create(STL_INFO, true)['canReslice']()).toBe(false);
    });

    it('shows why slicing failed', () => {
      const failed: SlicingInfo = { ...SLICED, status: 'FAILED', error: 'Slicing timed out' };

      expect(create({ ...STL_INFO, slicing: failed }, true)['slicingError']()).toBe(
        'Slicing timed out',
      );
      expect(create(BAMBU, true)['slicingError']()).toBeNull();
    });
  });

  describe('3D preview', () => {
    beforeEach(() => {
      let count = 0;
      vi.stubGlobal('URL', {
        createObjectURL: vi.fn(() => {
          count++;
          return `blob:plate-${count}`;
        }),
        revokeObjectURL: vi.fn(),
      });
    });

    afterEach(() => {
      vi.unstubAllGlobals();
    });

    it('loads the mesh of this file', () => {
      previewService.fileMesh.mockReturnValue(of(TRIANGLE_MESH));
      const card = create(STL_INFO);

      card['previewLoader']()().subscribe();

      expect(previewService.fileMesh).toHaveBeenCalledWith(4, 7);
    });

    it('shows the plate images of a Bambu file and frees them afterwards', () => {
      previewService.thumbnail.mockImplementation(
        (_request: number, _file: number, plate: number) => {
          if (plate === 2) {
            return throwError(() => {
              return new Error('No image');
            });
          }
          return of(new Blob(['png']));
        },
      );
      const fixture = createFixture(BAMBU);
      const card = fixture.componentInstance;
      const [lid, empty, refused] = BAMBU.plates;

      expect(previewService.thumbnail).toHaveBeenCalledTimes(3);
      expect(card['thumbnail'](lid)).toBe('blob:plate-1');
      expect(card['thumbnail'](empty)).toBeNull();
      expect(card['thumbnail'](refused)).toBe('blob:plate-2');
      expect(card['placeholder']()).toBe('blob:plate-1');

      fixture.destroy();
      expect(URL.revokeObjectURL).toHaveBeenCalledTimes(2);
    });

    it('asks no images for STL files or files that could not be read', () => {
      create(STL_INFO);
      create({ ...BAMBU, status: 'FAILED' });

      expect(previewService.thumbnail).not.toHaveBeenCalled();
    });

    it('has no placeholder without images', () => {
      expect(create(BAMBU)['placeholder']()).toBeNull();
    });
  });

  describe('collapsing (#23)', () => {
    function createClosed(info: ModelInfo) {
      const fixture = TestBed.createComponent(ModelInfoCard);
      fixture.componentRef.setInput('info', info);
      fixture.componentRef.setInput('requestId', 4);
      fixture.componentRef.setInput('fileId', 7);
      fixture.componentRef.setInput('filename', 'lid.3mf');
      fixture.componentRef.setInput('quantity', 3);
      fixture.componentRef.setInput('startOpen', false);
      fixture.detectChanges();
      return fixture.componentInstance;
    }

    it('starts open by default', () => {
      expect(create(STL_INFO)['open']()).toBe(true);
    });

    it('starts closed when asked and opens on click', () => {
      const card = createClosed(STL_INFO);
      expect(card['open']()).toBe(false);
      card['toggle']();
      expect(card['open']()).toBe(true);
      card['toggle']();
      expect(card['open']()).toBe(false);
    });

    it('loads the plate images only once it is first opened', () => {
      previewService.thumbnail.mockReturnValue(of(new Blob(['png'])));
      const card = createClosed(BAMBU);
      expect(previewService.thumbnail).not.toHaveBeenCalled();

      card['toggle']();
      expect(previewService.thumbnail).toHaveBeenCalledTimes(3);
      card['toggle']();
      card['toggle']();
      expect(previewService.thumbnail).toHaveBeenCalledTimes(3);
    });

    it('sums up weight and time for the header', () => {
      expect(create(BAMBU)['headerSummary']()).toBe('65,25 g · 1 h 35 min');
      expect(create(STL_INFO)['headerSummary']()).toBe('~4 g');
      expect(create({ ...STL_INFO, status: 'PENDING', totalGrams: null })['headerSummary']()).toBe(
        '',
      );
    });
  });

  it('flags failed slicing in the header', () => {
    expect(create(STL_INFO)['slicingFailed']()).toBe(false);
    const failed = {
      ...STL_INFO,
      slicing: { ...NO_SLICING, enabled: true, status: 'FAILED' as const },
    };
    expect(create(failed)['slicingFailed']()).toBe(true);
  });

  describe('filaments from Spoolman (#11)', () => {
    const WHOLE: FilamentChoice = {
      ...RED_FOR_WHITE,
      id: 13,
      wholeItem: true,
      plateIndex: null,
      originalType: null,
      originalColor: null,
    };

    it('lets a file with colours choose per colour, by plate', () => {
      const card = createFixture(BAMBU, false, [RED_FOR_WHITE], true, 2).componentInstance;
      const lid = BAMBU.plates[0];
      const white = lid.filaments[0];

      expect(card['hasColours']()).toBe(true);
      expect(card['showWholeItem']()).toBe(false);
      expect(card['slotOf'](lid, white)).toEqual({
        wholeItem: false,
        plateIndex: 1,
        originalType: 'PLA',
        originalColor: '#FFFFFF',
      });
      expect(card['choiceFor'](card['slotOf'](lid, white))).toEqual(RED_FOR_WHITE);
      expect(card['choiceFor'](card['slotOf'](BAMBU.plates[2], white))).toBeNull();
      expect(card['gramsNeeded'](lid, white)).toBeCloseTo(67.38);
      expect(card['hasChoiceOn'](lid)).toBe(true);
      expect(card['hasChoiceOn'](BAMBU.plates[2])).toBe(false);
      expect(card['notEnoughFilament']()).toBeNull();
    });

    it('takes the plate grams for its only colour, else unknown', () => {
      const card = create(BAMBU);
      const unsliced = { type: 'PLA', color: '#000000', grams: null };
      const plate = { ...BAMBU.plates[0], grams: 10, filaments: [unsliced] };
      expect(card['gramsNeeded'](plate, unsliced)).toBe(10);
      const twoColours = { ...plate, filaments: [unsliced, { ...unsliced, color: '#FFFFFF' }] };
      expect(card['gramsNeeded'](twoColours, unsliced)).toBeNull();
    });

    it('gives a file without colours one circle for the whole file', () => {
      const card = createFixture(STL_INFO, false, [], true, 3).componentInstance;
      expect(card['hasColours']()).toBe(false);
      expect(card['showWholeItem']()).toBe(true);
      expect(card['wholeItemChoice']()).toBeNull();
      expect(card['wholeItemGrams']()).toBe(12);

      expect(create(STL_INFO)['showWholeItem']()).toBe(false);
      const chosen = createFixture(STL_INFO, false, [WHOLE]).componentInstance;
      expect(chosen['showWholeItem']()).toBe(true);
      expect(chosen['wholeItemChoice']()).toEqual(WHOLE);
      const pending = { ...STL_INFO, status: 'PENDING' as const, totalGrams: null, plates: [] };
      expect(
        createFixture(pending, false, [], true).componentInstance['wholeItemGrams'](),
      ).toBeNull();
    });

    it('lists choices the file no longer has', () => {
      const gone = { ...RED_FOR_WHITE, id: 14, plateIndex: 2, orphaned: true };
      const card = createFixture(BAMBU, false, [RED_FOR_WHITE, gone], true).componentInstance;
      expect(card['orphanedChoices']()).toEqual([gone]);
      expect(card['orphanLabel'](gone)).toBe('Plate 2 · PLA #FFFFFF');
      expect(card['orphanLabel'](WHOLE)).toBe('Whole file');
      // An orphaned choice never counts for a circle.
      expect(
        card['choiceFor']({
          wholeItem: false,
          plateIndex: 2,
          originalType: 'PLA',
          originalColor: '#FFFFFF',
        }),
      ).toBeNull();
    });

    it('warns when not enough is left', () => {
      const short = { ...RED_FOR_WHITE, notEnough: true };
      expect(createFixture(BAMBU, false, [short]).componentInstance['notEnoughFilament']()).toBe(
        'Order it',
      );
      const goneShort = { ...short, orphaned: true };
      expect(
        createFixture(BAMBU, false, [goneShort]).componentInstance['notEnoughFilament'](),
      ).toBeNull();
    });

    it('colours the 3D preview with the chosen filaments', () => {
      const silk = {
        ...RED_FOR_WHITE,
        id: 15,
        wholeItem: false,
        plateIndex: 3,
        color: null,
        colors: ['#00FF00', '#0000FF'],
      };
      const gone = { ...RED_FOR_WHITE, id: 16, plateIndex: 2, orphaned: true };
      const noColour = { ...RED_FOR_WHITE, id: 17, plateIndex: 4, color: null };
      const card = createFixture(BAMBU, false, [
        RED_FOR_WHITE,
        silk,
        gone,
        noColour,
        WHOLE,
      ]).componentInstance;

      expect(card['previewColors']()).toEqual([
        { wholeFile: false, plate: 1, from: '#FFFFFF', to: '#FF0000' },
        { wholeFile: false, plate: 3, from: '#FFFFFF', to: '#00FF00' },
        { wholeFile: true, plate: null, from: null, to: '#FF0000' },
      ]);
    });

    it('shows the colours of the plate on screen under the preview', () => {
      const card = createFixture(BAMBU, false, [], true).componentInstance;
      expect(card['previewPlateColours']()).toBeNull();

      card['onPreviewPlate'](1);
      expect(card['previewPlateColours']()).toEqual(BAMBU.plates[0]);
      // Plate 2 has no colours.
      card['onPreviewPlate'](2);
      expect(card['previewPlateColours']()).toBeNull();
      card['onPreviewPlate'](9);
      expect(card['previewPlateColours']()).toBeNull();

      // Read-only and nothing chosen: nothing to show; something chosen: shown.
      const readOnly = createFixture(BAMBU).componentInstance;
      readOnly['onPreviewPlate'](1);
      expect(readOnly['previewPlateColours']()).toBeNull();
      const chosen = createFixture(BAMBU, false, [RED_FOR_WHITE]).componentInstance;
      chosen['onPreviewPlate'](1);
      expect(chosen['previewPlateColours']()).toEqual(BAMBU.plates[0]);

      // A file without colours gets its whole-file circle instead.
      const stl = createFixture(STL_INFO, false, [], true).componentInstance;
      stl['onPreviewPlate'](1);
      expect(stl['previewPlateColours']()).toBeNull();
      expect(stl['showWholeItem']()).toBe(true);
    });

    it('marks automatic choices and colours without a close filament (#76)', () => {
      const fixture = createFixture(BAMBU, false, [{ ...RED_FOR_WHITE, source: 'AUTO' }]);
      const card = fixture.componentInstance;
      const lid = BAMBU.plates[0];
      expect(card['isAutomatic'](card['slotOf'](lid, lid.filaments[0]))).toBe(true);
      expect(card['noMatchOn'](lid)).toBeNull();

      fixture.componentRef.setInput('unmatchedColors', [
        { plateIndex: 1, originalType: 'PLA', originalColor: '#ffffff' },
      ]);
      fixture.componentRef.setInput('noMatchMessage', 'Pick another');
      expect(card['noMatchOn'](lid)).toBe('Pick another');
      expect(card['noMatchOn'](BAMBU.plates[2])).toBeNull();
      expect(card['isUnmatched'](card['slotOf'](lid, lid.filaments[0]))).toBe(true);
      const other = {
        wholeItem: false,
        plateIndex: 2,
        originalType: 'PLA',
        originalColor: '#FFFFFF',
      };
      expect(card['isUnmatched'](other)).toBe(false);
      expect(card['isAutomatic'](other)).toBe(false);
    });

    it('passes choices on to the page', () => {
      const card = create(BAMBU);
      const chosen: FilamentChoiceInput[] = [];
      const resets: number[] = [];
      card.filamentChosen.subscribe((choice) => {
        chosen.push(choice);
      });
      card.filamentReset.subscribe((id) => {
        resets.push(id);
      });
      const input = {
        wholeItem: false,
        plateIndex: 1,
        originalType: 'PLA',
        originalColor: '#FFFFFF',
        spoolmanFilamentId: 3,
      };

      card['chooseFilament'](input);
      card['resetFilament'](12);

      expect(chosen).toEqual([input]);
      expect(resets).toEqual([12]);
    });
  });
});
