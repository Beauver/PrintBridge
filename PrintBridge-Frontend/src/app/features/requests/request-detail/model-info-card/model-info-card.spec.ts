import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ModelInfo, PlateInfo, SlicingInfo } from '../../../../core/models/model-info.model';
import { NO_SLICING, STL_INFO } from '../../../../core/testing';
import { TRIANGLE_MESH } from '../../../../core/testing';
import { ModelPreviewService } from '../../model-preview.service';
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
      providers: [provideRouter([]), { provide: ModelPreviewService, useValue: previewService }],
    });
  });

  function createFixture(info: ModelInfo, isAdmin = false) {
    const fixture = TestBed.createComponent(ModelInfoCard);
    fixture.componentRef.setInput('info', info);
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
    function slicedFor(slicing: Partial<SlicingInfo>): string | null {
      return create({ ...BAMBU, slicing: { ...SLICED, ...slicing } })['slicedFor']();
    }

    expect(slicedFor({ madeForNozzle: 0.8 })).toBe(
      'Made for a 0,8 mm nozzle. Numbers are for your Bambu Lab P1S · 0,4 mm nozzle',
    );
    expect(slicedFor({ madeForPrinter: 'Bambu Lab X1 Carbon' })).toBe(
      'Made for Bambu Lab X1 Carbon. Numbers are for your Bambu Lab P1S · 0,4 mm nozzle',
    );
    expect(slicedFor({ madeForPrinter: 'Bambu Lab A1', madeForNozzle: 0.2 })).toBe(
      'Made for Bambu Lab A1 · 0,2 mm nozzle. Numbers are for your Bambu Lab P1S · 0,4 mm nozzle',
    );
    expect(slicedFor({ printer: null })).toBe('0,4 mm nozzle');
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

    it('lets the admin re-slice while slicing is on', () => {
      const card = create(BAMBU, true);
      const emitted = vi.fn();
      card.reslice.subscribe(emitted);

      expect(card['canReslice']()).toBe(true);
      card['requestReslice']();
      expect(emitted).toHaveBeenCalled();
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
});
