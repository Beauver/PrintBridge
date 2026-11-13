import {
  AmbientLight,
  Box3,
  BufferAttribute,
  BufferGeometry,
  DirectionalLight,
  Float32BufferAttribute,
  Group,
  LineBasicMaterial,
  LineSegments,
  Material,
  Mesh,
  MeshStandardMaterial,
  Object3D,
  PerspectiveCamera,
  Scene,
  Sphere,
  Vector3,
  WebGLRenderer,
} from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { PreviewBed, PreviewMesh } from '../../core/models/model-preview.model';
import { PreviewSceneHandle } from './preview-scene-factory';

/** Colour for STL files and parts without a known filament. */
export const NEUTRAL_COLOR = '#8fa8c8';
/** Grid spacing on the bed, in mm. */
const GRID_STEP = 10;
const GRID_COLOR = '#9aa0a6';
const BED_EDGE_COLOR = '#5f6368';

/** The parts of one plate as Three.js meshes, coloured by filament. */
export function plateObjects(mesh: PreviewMesh, plate: number): Group {
  const group = new Group();
  for (const part of mesh.parts) {
    if (part.plate !== plate) {
      continue;
    }
    const geometry = new BufferGeometry();
    geometry.setAttribute('position', new BufferAttribute(part.positions, 3));
    geometry.setIndex(new BufferAttribute(part.indices, 1));
    // Flat shading: STL corners are shared between faces, smooth normals would blur the edges.
    const material = new MeshStandardMaterial({
      color: part.color ?? NEUTRAL_COLOR,
      flatShading: true,
      roughness: 0.7,
      metalness: 0.05,
    });
    group.add(new Mesh(geometry, material));
  }
  return group;
}

/** The bed as a grid of 10 mm lines on z = 0, with its edge drawn darker. */
export function bedLines(bed: PreviewBed): Group {
  const grid: number[] = [];
  for (let x = GRID_STEP; x < bed.x; x += GRID_STEP) {
    grid.push(x, 0, 0, x, bed.y, 0);
  }
  for (let y = GRID_STEP; y < bed.y; y += GRID_STEP) {
    grid.push(0, y, 0, bed.x, y, 0);
  }
  const edge = [0, 0, 0, bed.x, 0, 0, bed.x, 0, 0, bed.x, bed.y, 0];
  edge.push(bed.x, bed.y, 0, 0, bed.y, 0, 0, bed.y, 0, 0, 0, 0);
  const group = new Group();
  group.add(lines(grid, GRID_COLOR), lines(edge, BED_EDGE_COLOR));
  return group;
}

/** Where to look from so `bounds` fills the view: in front of it, a bit above. */
export function cameraPlacement(
  bounds: Box3,
  fovDegrees: number,
): { position: Vector3; target: Vector3 } {
  const sphere = bounds.getBoundingSphere(new Sphere());
  const radius = Math.max(sphere.radius, 1);
  const distance = radius / Math.sin(((fovDegrees / 2) * Math.PI) / 180);
  const direction = new Vector3(0, -1, 0.75).normalize();
  return {
    position: sphere.center.clone().addScaledVector(direction, distance),
    target: sphere.center.clone(),
  };
}

function lines(positions: number[], color: string): LineSegments {
  const geometry = new BufferGeometry();
  geometry.setAttribute('position', new Float32BufferAttribute(positions, 3));
  return new LineSegments(geometry, new LineBasicMaterial({ color }));
}

/**
 * A WebGL canvas in `host` with orbit controls (drag to rotate, scroll or pinch to zoom,
 * right-drag to pan). It only renders when something changes, so it costs nothing while idle.
 * Printer coordinates are z-up, so the camera is too.
 */
export class PreviewScene implements PreviewSceneHandle {
  private readonly renderer: WebGLRenderer;
  private readonly scene = new Scene();
  private readonly camera = new PerspectiveCamera(40, 1, 0.1, 10000);
  private readonly controls: OrbitControls;
  private readonly resizeObserver: ResizeObserver;
  private content: Object3D | null = null;

  constructor(private readonly host: HTMLElement) {
    this.renderer = new WebGLRenderer({ antialias: true, alpha: true });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    host.appendChild(this.renderer.domElement);
    this.camera.up.set(0, 0, 1);
    this.scene.add(new AmbientLight('#ffffff', 1.2));
    const sun = new DirectionalLight('#ffffff', 1.6);
    sun.position.set(-1, -2, 3);
    this.scene.add(sun);
    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.addEventListener('change', () => {
      this.render();
    });
    this.resizeObserver = new ResizeObserver(() => {
      this.resize();
    });
    this.resizeObserver.observe(host);
    this.resize();
  }

  show(mesh: PreviewMesh, plate: number): void {
    this.clear();
    const content = new Group();
    const model = plateObjects(mesh, plate);
    content.add(model);
    if (mesh.bed) {
      content.add(bedLines(mesh.bed));
    }
    this.scene.add(content);
    this.content = content;
    // Frame the model; the bed only when there is nothing on the plate.
    let bounds = new Box3().setFromObject(model);
    if (bounds.isEmpty()) {
      bounds = new Box3().setFromObject(content);
    }
    const placement = cameraPlacement(bounds, this.camera.fov);
    this.camera.position.copy(placement.position);
    this.controls.target.copy(placement.target);
    this.camera.near = Math.max(0.1, placement.position.distanceTo(placement.target) / 1000);
    this.camera.far = placement.position.distanceTo(placement.target) * 20;
    this.camera.updateProjectionMatrix();
    this.controls.update();
    this.render();
  }

  dispose(): void {
    this.resizeObserver.disconnect();
    this.controls.dispose();
    this.clear();
    this.renderer.dispose();
    this.renderer.domElement.remove();
  }

  private clear(): void {
    if (!this.content) {
      return;
    }
    this.scene.remove(this.content);
    this.content.traverse((object) => {
      if (object instanceof Mesh || object instanceof LineSegments) {
        object.geometry.dispose();
        const materials: Material[] = [object.material].flat();
        for (const material of materials) {
          material.dispose();
        }
      }
    });
    this.content = null;
  }

  private resize(): void {
    const width = Math.max(this.host.clientWidth, 1);
    const height = Math.max(this.host.clientHeight, 1);
    this.renderer.setSize(width, height);
    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
    this.render();
  }

  private render(): void {
    this.renderer.render(this.scene, this.camera);
  }
}
