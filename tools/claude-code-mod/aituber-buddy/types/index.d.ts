/** What the buddy is doing, which picks its face and its line. */
export type Mood = 'idle' | 'thinking' | 'talking' | 'working' | 'happy' | 'worried'

declare module 'claude-code' {
  interface PluginState {
    'aituber-buddy': {
      mood: Mood
      /** The speech bubble. */
      line: string
      /** Animation frame: mouth open/closed, blink. */
      frame: number
    }
  }
}
