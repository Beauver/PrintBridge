import { encodeMesh, TRIANGLE_MESH } from '../../core/testing';
import { decodeMesh } from './mesh-decoder';

describe('decodeMesh', () => {
  it('reads the header and the arrays of every part', () => {
    const mesh = decodeMesh(encodeMesh(TRIANGLE_MESH));

    expect(mesh.bed).toEqual({ x: 256, y: 256, z: 250 });
    expect(mesh.plates).toEqual(TRIANGLE_MESH.plates);
    expect(mesh.parts).toHaveLength(1);
    expect(mesh.parts[0].color).toBe('#FF0000');
    expect(Array.from(mesh.parts[0].positions)).toEqual([0, 0, 0, 10, 0, 0, 0, 10, 5]);
    expect(Array.from(mesh.parts[0].indices)).toEqual([0, 1, 2]);
  });

  it('refuses anything that is not a whole preview mesh', () => {
    const encoded = encodeMesh(TRIANGLE_MESH);

    expect(() => {
      return decodeMesh(new TextEncoder().encode('{"message":"nope"}').buffer);
    }).toThrow('Not a preview mesh');
    expect(() => {
      return decodeMesh(new ArrayBuffer(4));
    }).toThrow('Not a preview mesh');
    expect(() => {
      return decodeMesh(encoded.slice(0, encoded.byteLength - 4));
    }).toThrow('The preview mesh is incomplete');
    expect(() => {
      return decodeMesh(encoded.slice(0, 12));
    }).toThrow('The preview mesh is incomplete');
  });
});
