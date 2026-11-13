import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { PreviewMesh } from '../../core/models/model-preview.model';
import { TRIANGLE_MESH } from '../../core/testing';
import { ModelPreview } from './model-preview';
import { PREVIEW_SCENE_FACTORY } from './preview-scene-factory';

describe('ModelPreview', () => {
  function create(loader: () => Observable<PreviewMesh>) {
    const fixture = TestBed.createComponent(ModelPreview);
    fixture.componentRef.setInput('loader', loader);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: PREVIEW_SCENE_FACTORY,
          useValue: {
            isAvailable: () => {
              return false;
            },
            create: vi.fn(),
          },
        },
      ],
    });
  });

  it('loads nothing until asked, then shows the mesh', () => {
    const loader = vi.fn().mockReturnValue(of(TRIANGLE_MESH));
    const preview = create(loader).componentInstance;

    expect(loader).not.toHaveBeenCalled();
    preview['show']();

    expect(loader).toHaveBeenCalledTimes(1);
    expect(preview['mesh']()).toBe(TRIANGLE_MESH);
    expect(preview['loading']()).toBe(false);
  });

  it('shows why there is no preview', () => {
    const preview = create(() => {
      return throwError(() => {
        return new HttpErrorResponse({
          status: 422,
          error: { message: 'This model is too detailed for a preview' },
        });
      });
    }).componentInstance;

    preview['show']();

    expect(preview['error']()).toBe('This model is too detailed for a preview');
    expect(preview['mesh']()).toBeNull();

    const broken = create(() => {
      return throwError(() => {
        return new Error('Not a preview mesh');
      });
    }).componentInstance;
    broken['show']();
    expect(broken['error']()).toBe('No preview available for this file');
  });

  it('starts over for another file', () => {
    const fixture = create(() => {
      return of(TRIANGLE_MESH);
    });
    const preview = fixture.componentInstance;
    preview['show']();

    fixture.componentRef.setInput('loader', () => {
      return of(TRIANGLE_MESH);
    });
    fixture.detectChanges();

    expect(preview['mesh']()).toBeNull();
    expect(preview['error']()).toBeNull();
  });
});
