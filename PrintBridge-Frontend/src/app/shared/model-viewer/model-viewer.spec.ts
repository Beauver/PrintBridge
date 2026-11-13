import { TestBed } from '@angular/core/testing';
import { PreviewMesh } from '../../core/models/model-preview.model';
import { TRIANGLE_MESH } from '../../core/testing';
import { ModelViewer } from './model-viewer';
import { PREVIEW_SCENE_FACTORY, PreviewSceneHandle } from './preview-scene-factory';

describe('ModelViewer', () => {
  let scene: { show: ReturnType<typeof vi.fn>; dispose: ReturnType<typeof vi.fn> };
  let factory: { isAvailable: ReturnType<typeof vi.fn>; create: ReturnType<typeof vi.fn> };

  async function create(mesh: PreviewMesh = TRIANGLE_MESH) {
    const fixture = TestBed.createComponent(ModelViewer);
    fixture.componentRef.setInput('mesh', mesh);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    scene = { show: vi.fn(), dispose: vi.fn() };
    factory = {
      isAvailable: vi.fn().mockReturnValue(true),
      create: vi.fn().mockResolvedValue(scene),
    };
    TestBed.configureTestingModule({
      providers: [{ provide: PREVIEW_SCENE_FACTORY, useValue: factory }],
    });
  });

  it('labels the plates and starts at the first one with objects', async () => {
    const onSecond: PreviewMesh = {
      ...TRIANGLE_MESH,
      parts: [{ ...TRIANGLE_MESH.parts[0], plate: 2 }],
    };
    const viewer = (await create(onSecond)).componentInstance;

    expect(viewer['plates']()).toEqual([
      { index: 1, label: '1. Lid', empty: true },
      { index: 2, label: 'Plate 2', empty: false },
    ]);
    expect(viewer['showPlateButtons']()).toBe(true);
    expect(viewer['selectedPlate']()).toBe(2);
    expect(scene.show).toHaveBeenLastCalledWith(onSecond, 2);
    expect(viewer['ready']()).toBe(true);
  });

  it('shows another plate when it is picked', async () => {
    const fixture = await create();

    fixture.componentInstance['selectPlate'](2);
    fixture.detectChanges();

    expect(scene.show).toHaveBeenLastCalledWith(TRIANGLE_MESH, 2);
  });

  it('has no plate buttons for a single plate and falls back to plate 1', async () => {
    const single: PreviewMesh = { bed: null, plates: [{ index: 1, name: null }], parts: [] };
    const empty: PreviewMesh = { bed: null, plates: [], parts: [] };

    expect((await create(single)).componentInstance['showPlateButtons']()).toBe(false);
    expect((await create(empty)).componentInstance['selectedPlate']()).toBe(1);
  });

  it('frees the scene when it goes away', async () => {
    const fixture = await create();

    fixture.destroy();

    expect(scene.dispose).toHaveBeenCalled();
  });

  it('frees a scene that arrives after it went away', async () => {
    let resolve: (handle: PreviewSceneHandle) => void = () => {
      // Replaced below.
    };
    factory.create.mockReturnValue(
      new Promise<PreviewSceneHandle>((done) => {
        resolve = done;
      }),
    );
    const fixture = TestBed.createComponent(ModelViewer);
    fixture.componentRef.setInput('mesh', TRIANGLE_MESH);
    fixture.detectChanges();

    fixture.destroy();
    resolve(scene as unknown as PreviewSceneHandle);
    await Promise.resolve();

    expect(scene.dispose).toHaveBeenCalled();
    expect(scene.show).not.toHaveBeenCalled();
  });

  it('says so without WebGL or when the 3D code does not load', async () => {
    factory.isAvailable.mockReturnValue(false);
    expect((await create()).componentInstance['webGl']()).toBe(false);
    expect(factory.create).not.toHaveBeenCalled();

    factory.isAvailable.mockReturnValue(true);
    factory.create.mockRejectedValue(new Error('offline'));
    expect((await create()).componentInstance['webGl']()).toBe(false);
  });
});
