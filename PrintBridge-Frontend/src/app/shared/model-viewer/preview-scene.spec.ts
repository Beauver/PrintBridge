import { Box3, LineSegments, Mesh, MeshStandardMaterial, Vector3 } from 'three';
import { TRIANGLE_MESH } from '../../core/testing';
import { isWebGlAvailable } from './preview-scene-factory';
import { bedLines, cameraPlacement, NEUTRAL_COLOR, plateObjects } from './preview-scene';

describe('preview scene', () => {
  it('builds the meshes of one plate in their filament colour', () => {
    const neutral = {
      ...TRIANGLE_MESH,
      parts: [...TRIANGLE_MESH.parts, { ...TRIANGLE_MESH.parts[0], color: null }],
    };

    const group = plateObjects(neutral, 1);

    expect(group.children).toHaveLength(2);
    const [red, grey] = group.children as Mesh[];
    expect((red.material as MeshStandardMaterial).color.getHexString()).toBe('ff0000');
    expect((grey.material as MeshStandardMaterial).color.getHexString()).toBe(
      NEUTRAL_COLOR.substring(1),
    );
    expect(red.geometry.getIndex()?.count).toBe(3);
    expect(plateObjects(TRIANGLE_MESH, 2).children).toHaveLength(0);
  });

  it('draws the bed as a 10 mm grid with its edge', () => {
    const group = bedLines({ x: 30, y: 20, z: 10 });

    const [grid, edge] = group.children as LineSegments[];
    // x = 10, 20 and y = 10: three lines of two points.
    expect(grid.geometry.getAttribute('position').count).toBe(6);
    expect(edge.geometry.getAttribute('position').count).toBe(8);
  });

  it('places the camera in front of the model, looking at its centre', () => {
    const bounds = new Box3(new Vector3(0, 0, 0), new Vector3(20, 20, 20));

    const { position, target } = cameraPlacement(bounds, 40);

    expect(target.toArray()).toEqual([10, 10, 10]);
    expect(position.y).toBeLessThan(target.y);
    expect(position.z).toBeGreaterThan(target.z);
    expect(position.distanceTo(target)).toBeCloseTo(Math.sqrt(300) / Math.sin(Math.PI / 9), 3);
    expect(
      cameraPlacement(new Box3(new Vector3(), new Vector3()), 40).position.length(),
    ).toBeGreaterThan(0);
  });

  it('knows jsdom has no WebGL', () => {
    expect(isWebGlAvailable()).toBe(false);
  });
});
