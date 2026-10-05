package com.aituber.poc.character.live2d.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniJsonTest {

    @Test
    fun numbersKeepTheirOriginalText() {
        val text = """{"Meta":{"Duration":1.0,"Fps":30.0},"Segments":[0,0.1,1,0.5e1,-2.25]}"""
        val out = MiniJson.write(MiniJson.parse(text))
        assertTrue(out.contains("\"Duration\": 1.0"))
        assertTrue(out.contains("\"Fps\": 30.0"))
        assertTrue(out.contains("0.1,\n"))
        assertTrue(out.contains("-2.25\n"))
    }

    /** Cubism SDK parser rule: a number must be followed directly by ',' or a newline. */
    @Test
    fun everyNumberIsFollowedByCommaOrNewline() {
        val text = """{"a":[0,1,[2,3],{"b":4}],"c":5,"d":{"e":[6]}}"""
        val out = MiniJson.write(MiniJson.parse(text))
        val number = Regex("-?\\d+(\\.\\d+)?")
        number.findAll(out).forEach { m ->
            val next = out.getOrNull(m.range.last + 1)
            assertTrue("number '${m.value}' followed by '$next' in:\n$out", next == ',' || next == '\n')
        }
    }

    @Test
    fun roundTripKeepsKeyOrderAndStrings() {
        val text = """{"b":"日本語\n\"q\"","a":[{"x":true},{"y":null}],"c":{}}"""
        val parsed = MiniJson.parse(text) as JsonValue.Obj
        assertEquals(listOf("b", "a", "c"), parsed.fields.keys.toList())
        val reparsed = MiniJson.parse(MiniJson.write(parsed)) as JsonValue.Obj
        assertEquals("日本語\n\"q\"", reparsed.str("b"))
        assertEquals(true, (reparsed.arr("a")!!.items[0] as JsonValue.Obj).bool("x"))
    }

    @Test
    fun toleratesBomTrailingCommasAndLineComments() {
        val text = "﻿{\n // exported by hand\n \"a\": [1, 2,],\n \"b\": 3,\n}"
        val parsed = MiniJson.parse(text) as JsonValue.Obj
        assertEquals(2, parsed.arr("a")!!.items.size)
        assertEquals(3, parsed.int("b"))
    }

    @Test
    fun unicodeEscapes() {
        val parsed = MiniJson.parse("""{"s":"日本"}""") as JsonValue.Obj
        assertEquals("日本", parsed.str("s"))
    }

    @Test(expected = JsonParseException::class)
    fun truncatedJsonFails() {
        MiniJson.parse("""{"FileReferences":{"Moc":"a.moc3" """)
    }

    @Test(expected = JsonParseException::class)
    fun garbageAfterRootFails() {
        MiniJson.parse("""{"a":1} x""")
    }

    @Test(expected = JsonParseException::class)
    fun hugeExponentIsRejected() {
        // Rewriting 1e999999999 as a plain decimal would exhaust memory.
        MiniJson.parse("""{"a":1e999999999}""")
    }

    @Test(expected = JsonParseException::class)
    fun excessiveNestingFails() {
        MiniJson.parse("[".repeat(1000) + "]".repeat(1000))
    }
}
