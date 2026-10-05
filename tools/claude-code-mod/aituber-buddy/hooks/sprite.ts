import type { Mood } from '../types'

/**
 * The buddy is a 20 x 24 pixel sprite. The terminal draws it as a Raster of
 * half-block cells (two pixels per cell, 20 x 12 cells); the other surfaces
 * draw the same pixels as an SVG.
 */
export const WIDTH = 20
export const HEIGHT = 24

const BASE = [
  '......HHHHHHHH......',
  '....HHHHHHHHHHHH....',
  '...HHhhHHHHHHhhHH...',
  '..HHHHHHHHHHHHHHHH..',
  '..HHHHSSHHHHSSHHHH..',
  '.RHHSSSSSSSSSSSSHHR.',
  '.RHSSSSSSSSSSSSSSHR.',
  '..HSS@@SSSSSS##SSH..',
  '..HSS@@SSSSSS##SSH..',
  '..HSSSSSSSSSSSSSSH..',
  '..HSBBSSSSSSSSBBSH..',
  '..HHSSSS%%%%SSSSHH..',
  '...HHSSSSSSSSSSHH...',
  '...HHHssSSSSssHHH...',
  '...HHH..sSSs..HHH...',
  '...HH.CCCRRCCC.HH...',
  '....HCCCCRRCCCCH....',
  '....CCCCCCCCCCCC....',
  '...CCCcCCCCCCcCCC...',
  '...CCCcCCCCCCcCCC...',
  '..SCCCcCCCCCCcCCCS..',
  '..SCCCCCCCCCCCCCCS..',
  '....CCCCCCCCCCCC....',
  '....cccc....cccc....',
]

/** Colors as 0xRRGGBB; '.' is see-through. */
const PALETTE: Record<string, number> = {
  H: 0x4b3b78, // hair
  h: 0x7a68b8, // hair shine
  S: 0xffe0cc, // skin
  s: 0xf0c2a6, // skin shade
  E: 0x2b2140, // eye
  W: 0xffffff, // eye shine
  M: 0xc0485a, // lips
  D: 0x7a1f33, // open mouth
  B: 0xff9eb0, // blush
  C: 0x3fa7d6, // clothes
  c: 0x2b7fa8, // clothes shade
  R: 0xff6f91, // ribbons
  T: 0x8fd3ff, // sweat drop
}

type Eye = [string, string]

/** Left eye as two rows of two pixels; the right eye is its mirror. */
function eyeFor(mood: Mood, frame: number): Eye {
  if (mood === 'happy') return ['EE', 'SS']
  if (mood === 'worried') return ['SE', 'EW']
  if (mood === 'thinking') return ['WE', 'EE']
  // Blink now and then while idle or working.
  if ((mood === 'idle' || mood === 'working') && frame % 16 === 15) return ['SS', 'EE']
  return ['EE', 'EW']
}

function mouthFor(mood: Mood, frame: number): string {
  switch (mood) {
    case 'talking':
      return frame % 2 === 0 ? 'MDDM' : 'SMMS'
    case 'happy':
      return 'MDDM'
    case 'worried':
      return 'SMMS'.replace('MM', 'MS')
    case 'thinking':
      return 'SSMS'
    default:
      return 'SMMS'
  }
}

/** The sprite as rows of palette letters for this mood and frame. */
export function pixels(mood: Mood, frame: number): string[] {
  const [eyeTop, eyeBottom] = eyeFor(mood, frame)
  const mirror = (s: string) => s.split('').reverse().join('')
  const mouth = mouthFor(mood, frame)
  return BASE.map((row, y) => {
    let out = row
    if (y === 7) out = out.replace('@@', eyeTop).replace('##', mirror(eyeTop))
    if (y === 8) out = out.replace('@@', eyeBottom).replace('##', mirror(eyeBottom))
    if (y === 11) out = out.replace('%%%%', mouth)
    // A sweat drop beside the head while working hard or worried.
    if ((mood === 'worried' || (mood === 'working' && frame % 4 < 2)) && (y === 5 || y === 6)) {
      out = out.slice(0, 19) + 'T'
    }
    return out
  })
}

export function colorOf(letter: string): number | undefined {
  return PALETTE[letter]
}

const DEFAULT_COLOR = 0x01000000
const UPPER_HALF = 0x2580
const LOWER_HALF = 0x2584
const SPACE = 0x20

/** RasterProps.cells: one ▀ / ▄ / space per two stacked pixels, base64 of u32 LE triplets. */
export function rasterCells(rows: string[]): string {
  const cellRows = Math.ceil(rows.length / 2)
  const words = new Uint32Array(WIDTH * cellRows * 3)
  let i = 0
  for (let cy = 0; cy < cellRows; cy++) {
    for (let x = 0; x < WIDTH; x++) {
      const top = colorOf(rows[cy * 2]?.[x] ?? '.')
      const bottom = colorOf(rows[cy * 2 + 1]?.[x] ?? '.')
      if (top !== undefined) {
        words[i++] = UPPER_HALF
        words[i++] = top
        words[i++] = bottom ?? DEFAULT_COLOR
      } else if (bottom !== undefined) {
        words[i++] = LOWER_HALF
        words[i++] = bottom
        words[i++] = DEFAULT_COLOR
      } else {
        words[i++] = SPACE
        words[i++] = DEFAULT_COLOR
        words[i++] = DEFAULT_COLOR
      }
    }
  }
  return base64(new Uint8Array(words.buffer))
}

/** The same pixels as an SVG document, one rect per pixel run. */
export function svgSource(rows: string[]): string {
  const rects: string[] = []
  rows.forEach((row, y) => {
    let x = 0
    while (x < row.length) {
      const letter = row[x] ?? '.'
      let run = 1
      while (x + run < row.length && row[x + run] === letter) run++
      const color = colorOf(letter)
      if (color !== undefined) {
        const hex = '#' + color.toString(16).padStart(6, '0')
        rects.push(`<rect x="${x}" y="${y}" width="${run}" height="1" fill="${hex}"/>`)
      }
      x += run
    }
  })
  return (
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${WIDTH} ${HEIGHT}" ` +
    `width="${WIDTH * 6}" height="${HEIGHT * 6}" shape-rendering="crispEdges">` +
    rects.join('') +
    '</svg>'
  )
}

const ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'

/** Standard padded base64 (the environment has no Buffer). */
export function base64(bytes: Uint8Array): string {
  let out = ''
  for (let i = 0; i < bytes.length; i += 3) {
    const a = bytes[i] ?? 0
    const b = bytes[i + 1] ?? 0
    const c = bytes[i + 2] ?? 0
    const n = (a << 16) | (b << 8) | c
    const at = (k: number) => ALPHABET.charAt(k)
    out += at((n >> 18) & 63) + at((n >> 12) & 63)
    out += i + 1 < bytes.length ? at((n >> 6) & 63) : '='
    out += i + 2 < bytes.length ? at(n & 63) : '='
  }
  return out
}
