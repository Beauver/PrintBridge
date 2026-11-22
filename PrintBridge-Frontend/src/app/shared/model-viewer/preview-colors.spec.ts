import { PreviewColor, PreviewPart } from '../../core/models/model-preview.model';
import { chosenColor } from './preview-colors';

describe('chosenColor', () => {
  function part(plate: number, color: string | null): PreviewPart {
    return { plate, color, positions: new Float32Array(), indices: new Uint32Array() };
  }

  it('replaces a colour on its own plate only, ignoring case', () => {
    const colors: PreviewColor[] = [{ wholeFile: false, plate: 1, from: '#FFFFFF', to: '#000000' }];
    expect(chosenColor(part(1, '#ffffff'), colors)).toBe('#000000');
    expect(chosenColor(part(2, '#FFFFFF'), colors)).toBeNull();
    expect(chosenColor(part(1, '#FF0000'), colors)).toBeNull();
    expect(chosenColor(part(1, '#FF0000'), [])).toBeNull();
  });

  it('gives parts without a colour the whole file, else the only choice on their plate', () => {
    const whole: PreviewColor = { wholeFile: true, plate: null, from: null, to: '#123456' };
    expect(chosenColor(part(1, null), [whole])).toBe('#123456');
    // The slicer gave an STL one colour: its choice colours the colourless parts.
    const sliced: PreviewColor = { wholeFile: false, plate: 1, from: '#00FF00', to: '#0000FF' };
    expect(chosenColor(part(1, null), [sliced])).toBe('#0000FF');
    const second: PreviewColor = { wholeFile: false, plate: 1, from: '#FFFFFF', to: '#FF00FF' };
    expect(chosenColor(part(1, null), [sliced, second])).toBeNull();
    expect(chosenColor(part(1, null), [])).toBeNull();
  });
});
