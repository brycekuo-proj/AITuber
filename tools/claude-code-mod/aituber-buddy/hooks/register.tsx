import { atom, read, update } from 'claude-code'
import type { EngineInterface, Register } from 'claude-code'

import type { Mood } from '../types'
import { pixels, rasterCells, svgSource, WIDTH } from './sprite'

const PANE = 'aituber-buddy'
const TITLE = 'AITuber'

const mood = atom({ plugin: 'aituber-buddy', key: 'mood' } as const, 'idle')
const line = atom({ plugin: 'aituber-buddy', key: 'line' } as const, '等你下指令～')
const frame = atom({ plugin: 'aituber-buddy', key: 'frame' } as const, 0)

/** How often the face animates (mouth while talking, sweat while working, blinks). */
const TICK_MS = 220
/** How long "done!" or "oops" stays before the buddy goes back to waiting. */
const SETTLE_MS = 6000
/** The bubble shows the tail of what Claude is writing. */
const BUBBLE_CHARS = 72

const STATUS_FACE: Record<Mood, string> = {
  idle: '(・ω・)',
  thinking: '(・_・?)',
  talking: '(・o・)',
  working: '(•̀ᴗ•́)و',
  happy: '(＾▽＾)',
  worried: '(；´Д｀)',
}

/** A short, human line for a tool call. */
export function describeTool(tool: string, input: Record<string, unknown>): string {
  const str = (k: string) => (typeof input[k] === 'string' ? (input[k] as string) : '')
  const base = (p: string) => p.split('/').pop() ?? p
  switch (tool) {
    case 'Bash':
      return `執行指令：${clip(str('command'), 40)}`
    case 'Read':
      return `讀檔：${base(str('file_path'))}`
    case 'Edit':
    case 'MultiEdit':
      return `修改：${base(str('file_path'))}`
    case 'Write':
      return `寫檔：${base(str('file_path'))}`
    case 'Grep':
    case 'Glob':
      return `搜尋：${clip(str('pattern'), 40)}`
    case 'WebSearch':
      return `上網查：${clip(str('query'), 40)}`
    case 'WebFetch':
      return `讀網頁：${clip(str('url'), 40)}`
    case 'Agent':
    case 'Task':
      return `找幫手：${clip(str('description'), 40)}`
    default:
      return `使用 ${tool.replace(/^mcp__[^_]+__/, '')}`
  }
}

function clip(text: string, max: number): string {
  const flat = text.replace(/\s+/g, ' ').trim()
  return flat.length > max ? flat.slice(0, max - 1) + '…' : flat
}

/** The last `max` characters of `text`, on one line. */
export function tail(text: string, max: number): string {
  const flat = text.replace(/\s+/g, ' ').trim()
  return flat.length > max ? '…' + flat.slice(flat.length - max + 1) : flat
}

// Module variables only steer timing; everything drawn lives in $.state.
let settleTimer: { cancel(): void } | undefined
let lastBubbleAt = 0
let ticks = 0

/** Sets the face and the bubble, and mirrors them on the status line. */
async function show($: EngineInterface, next: Mood, text: string) {
  settleTimer?.cancel()
  settleTimer = undefined
  await update($, mood, () => next)
  await update($, line, () => text)
  $.ui.status(`${STATUS_FACE[next]} ${clip(text, 30)}`)
}

/** One animation tick: busy moods animate every tick, calm ones only blink. */
async function tick($: EngineInterface) {
  ticks += 1
  const now = await read($, mood)
  const animating = now === 'thinking' || now === 'talking' || now === 'working'
  const wanted = animating ? ticks : ticks % 16 === 15 ? 15 : 0
  if ((await read($, frame)) !== wanted) await update($, frame, () => wanted)
}

export const register: Register = on => {

  on('session.start', async ($, e, next) => {
    await $.command.register({
      name: 'aituber',
      description: '打開 AITuber 小角色面板',
    })
    void $.ui.open({ id: PANE, title: TITLE })
    $.clock.every(TICK_MS, () => void tick($))
    $.ui.status(`${STATUS_FACE.idle} 等你下指令～`)
    return next(e)
  })

  on('command.run', { command: 'aituber' }, async $ => {
    await $.ui.open({ id: PANE, title: TITLE })
    return { text: 'AITuber 小角色已打開。' }
  })

  on('turn.start', async ($, e, next) => {
    await show($, 'thinking', '嗯…讓我想想')
    return next(e)
  })

  on('turn.step', async function* ($, e, next) {
    if (e.agentId !== undefined) {
      // A subagent's steps: keep the face, pass the stream untouched.
      return yield* next(e)
    }
    let written = ''
    for await (const chunk of next(e)) {
      if (chunk.kind === 'thinking') {
        const now = await $.clock.now()
        if (now - lastBubbleAt > 500) {
          lastBubbleAt = now
          await show($, 'thinking', '嗯…讓我想想')
        }
      } else if (chunk.kind === 'text') {
        written += chunk.text
        const now = await $.clock.now()
        if (now - lastBubbleAt > 150) {
          lastBubbleAt = now
          await show($, 'talking', tail(written, BUBBLE_CHARS))
        }
      }
      yield chunk
    }
  })

  on('tool.call', async ($, e, next) => {
    const input = e as unknown as Record<string, unknown>
    const isMain = e.agentId === undefined
    if (isMain) await show($, 'working', describeTool(String(e.tool), input))
    const ran = await next(e)
    if (isMain && ran.deny === undefined && ran.isError === true) {
      await show($, 'worried', `唉，${String(e.tool)} 沒成功…`)
    }
    return ran
  })

  on('turn.complete', async ($, e, next) => {
    if (e.agentId === undefined) {
      const seconds = Math.round(e.durationMs / 1000)
      if (e.reason === 'answer') {
        await show($, 'happy', seconds >= 30 ? `完成了！花了 ${seconds} 秒` : '完成了！')
      } else if (e.reason === 'aborted') {
        await show($, 'idle', '好，先停在這裡')
      } else {
        await show($, 'worried', '出了點問題…')
      }
      if (e.reason !== 'aborted') {
        settleTimer = $.clock.after(SETTLE_MS, () => void show($, 'idle', '等你下指令～'))
      }
    }
    return next(e)
  })

  on('ui.render', { component: 'Pane', requestId: PANE }, async ($, e) => {
    const [m, l, f] = await Promise.all([read($, mood), read($, line), read($, frame)])
    const rows = pixels(m, f)
    const bubbleWidth = Math.max(10, Math.min(e.props.bodyColumns, 48))
    const dots = m === 'thinking' ? '.'.repeat((f % 3) + 1) : ''
    if (e.surface === 'terminal') {
      const { Box, Text, Raster } = $.ui.resolve(e)
      return (
        <Box flexDirection="column">
          <Raster key="buddy" columns={WIDTH} rows={rows.length / 2} cells={rasterCells(rows)} />
          <Box width={bubbleWidth} borderStyle="round" paddingX={1}>
            <Text key="line" wrap="wrap">
              {l}
              {dots}
            </Text>
          </Box>
        </Box>
      )
    }
    const { Box, Text, Svg } = $.ui.resolve(e)
    return (
      <Box flexDirection="column">
        <Svg source={svgSource(rows)} alt={`AITuber 小角色（${m}）`} />
        <Box width={bubbleWidth} borderStyle="round" paddingX={1}>
          <Text key="line" wrap="wrap">
            {l}
            {dots}
          </Text>
        </Box>
      </Box>
    )
  })
}
