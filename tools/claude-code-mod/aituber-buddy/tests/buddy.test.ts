import { describe, expect, test } from 'claude-code/testing'

import { base64, pixels, rasterCells, WIDTH, HEIGHT } from '../hooks/sprite'
import { describeTool, tail } from '../hooks/register'

const PANE = {
  plugin: 'aituber-buddy',
  component: 'Pane',
  requestId: 'aituber-buddy',
  props: {
    title: 'AITuber',
    isFocused: false,
    bodyColumns: 40,
    placement: 'dock',
    scroll: { offset: 0, bodyRows: 30 },
    view: {},
  },
} as const

describe('sprite', () => {
  test('every mood keeps the 20 x 24 shape', async () => {
    for (const mood of ['idle', 'thinking', 'talking', 'working', 'happy', 'worried'] as const) {
      for (const frame of [0, 1, 15]) {
        const rows = pixels(mood, frame)
        expect(rows.length).toBe(HEIGHT)
        for (const row of rows) expect(row.length).toBe(WIDTH)
      }
    }
  })

  test('talking opens and closes the mouth', async () => {
    expect(pixels('talking', 0)[11]).not.toBe(pixels('talking', 1)[11])
  })

  test('raster cells hold one triplet per cell', async () => {
    const cells = rasterCells(pixels('idle', 0))
    // 20 x 12 cells, 3 u32 each, 4 bytes per u32, base64 4 chars per 3 bytes.
    expect(cells.length).toBe(Math.ceil((WIDTH * 12 * 12) / 3) * 4)
  })

  test('base64 matches the standard alphabet with padding', async () => {
    expect(base64(new Uint8Array([77, 97, 110]))).toBe('TWFu')
    expect(base64(new Uint8Array([77, 97]))).toBe('TWE=')
    expect(base64(new Uint8Array([77]))).toBe('TQ==')
  })
})

describe('lines', () => {
  test('tool calls read like a person talking', async () => {
    expect(describeTool('Bash', { command: 'npm   test' })).toBe('執行指令：npm test')
    expect(describeTool('Edit', { file_path: '/a/b/MainActivity.kt' })).toBe('修改：MainActivity.kt')
    expect(describeTool('mcp__github__create_issue', {})).toBe('使用 create_issue')
  })

  test('the bubble keeps the end of a long answer', async () => {
    expect(tail('a'.repeat(100) + 'END', 10)).toBe('…' + 'a'.repeat(6) + 'END')
  })
})

describe('pane', () => {
  test('draws a sprite and the bubble on every surface', async ($, on) => {
    on('turn.start', (_$, e) => ({ turnId: e.turnId }))
    await $.turn.start({ text: 'hi', turnId: 't1' })
    for (const surface of ['terminal', 'desktop', 'vscode', 'mobile'] as const) {
      const ui = await $.ui.mount({ ...PANE, surface })
      const line = await ui.find({ type: 'Text' })
      expect(line?.text).toMatch(/讓我想想/)
      await ui.unmount()
    }
  })

  test('a finished turn makes the buddy happy', async ($, on) => {
    on('turn.start', (_$, e) => ({ turnId: e.turnId }))
    on('turn.complete', (_$, e) => ({ text: e.answer }))
    await $.turn.start({ text: 'hi', turnId: 't2' })
    await $.turn.complete({
      turnId: 't2',
      answer: 'done',
      durationMs: 1200,
      isAborted: false,
      reason: 'answer',
    })
    const ui = await $.ui.mount({ ...PANE, surface: 'terminal' })
    expect((await ui.find({ type: 'Text' }))?.text).toMatch(/完成了/)
    await ui.unmount()
  })
})
