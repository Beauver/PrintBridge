import { Scene } from 'three';
import { TRIANGLE_MESH } from '../../core/testing';

const renderer = vi.hoisted(() => {
  return {
    setPixelRatio: vi.fn(),
    setSize: vi.fn(),
    render: vi.fn(),
    dispose: vi.fn(),
    domElement: null as HTMLCanvasElement | null,
  };
});

// jsdom has no WebGL: everything but the renderer is the real Three.js.
vi.mock('three', async (importOriginal) => {
  const three = await importOriginal<typeof import('three')>();
  return {
    ...three,
    WebGLRenderer: vi.fn(function () {
      renderer.domElement = document.createElement('canvas');
      return renderer;
    }),
  };
});

describe('PreviewScene', () => {
  let observed: (() => void) | null;
  let disconnect: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.clearAllMocks();
    observed = null;
    disconnect = vi.fn();
    vi.stubGlobal(
      'ResizeObserver',
      vi.fn(function (callback: () => void) {
        observed = callback;
        return { observe: vi.fn(), disconnect };
      }),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  async function create() {
    const { PreviewScene } = await import('./preview-scene');
    const host = document.createElement('div');
    Object.defineProperty(host, 'clientWidth', { value: 400 });
    Object.defineProperty(host, 'clientHeight', { value: 300 });
    return { scene: new PreviewScene(host), host };
  }

  function sceneOf(): Scene {
    return renderer.render.mock.calls.at(-1)![0] as Scene;
  }

  it('puts its canvas in the host and sizes it', async () => {
    const { host } = await create();

    expect(host.querySelector('canvas')).not.toBeNull();
    expect(renderer.setSize).toHaveBeenCalledWith(400, 300);

    observed!();
    expect(renderer.setSize).toHaveBeenCalledTimes(2);
  });

  it('shows a plate with the bed and replaces it with the next one', async () => {
    const { scene } = await create();

    scene.show(TRIANGLE_MESH, 1);
    const shown = sceneOf().children.length;
    scene.show(TRIANGLE_MESH, 2);

    expect(sceneOf().children.length).toBe(shown);
    scene.show({ ...TRIANGLE_MESH, bed: null }, 2);
    expect(renderer.render).toHaveBeenCalled();
  });

  it('cleans everything up', async () => {
    const { scene, host } = await create();
    scene.show(TRIANGLE_MESH, 1);

    scene.dispose();

    expect(disconnect).toHaveBeenCalled();
    expect(renderer.dispose).toHaveBeenCalled();
    expect(host.querySelector('canvas')).toBeNull();
  });
});
