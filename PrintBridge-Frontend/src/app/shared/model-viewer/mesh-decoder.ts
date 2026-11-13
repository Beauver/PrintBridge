import { PreviewBed, PreviewMesh, PreviewPlate } from '../../core/models/model-preview.model';

const MAGIC = 'PBM1';

interface MeshHeader {
  bed: PreviewBed | null;
  plates: PreviewPlate[];
  parts: { plate: number; color: string | null; vertices: number; triangles: number }[];
}

/**
 * Reads the backend's preview mesh (see `MeshEncoder` in the backend): "PBM1", a uint32 header
 * length, a JSON header, then per part float32 positions and uint32 indices, all little-endian.
 * The arrays are views on `buffer`, nothing is copied.
 */
export function decodeMesh(buffer: ArrayBuffer): PreviewMesh {
  if (buffer.byteLength < 8 || new TextDecoder().decode(new Uint8Array(buffer, 0, 4)) !== MAGIC) {
    throw new Error('Not a preview mesh');
  }
  const headerLength = new DataView(buffer).getUint32(4, true);
  if (8 + headerLength > buffer.byteLength) {
    throw new Error('The preview mesh is incomplete');
  }
  const header = JSON.parse(
    new TextDecoder().decode(new Uint8Array(buffer, 8, headerLength)),
  ) as MeshHeader;
  let offset = 8 + headerLength;
  const parts = header.parts.map((part) => {
    const positionBytes = part.vertices * 3 * 4;
    const indexBytes = part.triangles * 3 * 4;
    if (offset + positionBytes + indexBytes > buffer.byteLength) {
      throw new Error('The preview mesh is incomplete');
    }
    const positions = new Float32Array(buffer, offset, part.vertices * 3);
    offset += positionBytes;
    const indices = new Uint32Array(buffer, offset, part.triangles * 3);
    offset += indexBytes;
    return { plate: part.plate, color: part.color, positions, indices };
  });
  return { bed: header.bed, plates: header.plates, parts };
}
