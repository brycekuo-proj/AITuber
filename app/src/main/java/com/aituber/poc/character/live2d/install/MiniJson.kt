package com.aituber.poc.character.live2d.install

/**
 * Small JSON reader/writer used by the character package converter.
 *
 * Why not org.json: it is an Android stub in local unit tests, and it rewrites
 * numbers (1.0 -> 1, 0.1f precision) when round-tripping. Live2D motion/physics
 * files are rewritten by the converter, so numbers are kept as their original
 * text and written back unchanged.
 */
sealed class JsonValue {
    object Null : JsonValue()
    data class Bool(val value: Boolean) : JsonValue()
    /** [raw] is the number exactly as written in the source (or as produced by [of]). */
    data class Num(val raw: String) : JsonValue() {
        fun toDouble(): Double = raw.toDouble()
        fun toInt(): Int = raw.toDouble().toInt()
    }
    data class Str(val value: String) : JsonValue()
    class Arr(val items: MutableList<JsonValue> = mutableListOf()) : JsonValue()
    /** Insertion-ordered so rewritten files keep the author's key order. */
    class Obj(val fields: LinkedHashMap<String, JsonValue> = LinkedHashMap()) : JsonValue() {
        operator fun get(key: String): JsonValue? = fields[key]
        operator fun set(key: String, value: JsonValue) { fields[key] = value }
        fun obj(key: String): Obj? = fields[key] as? Obj
        fun arr(key: String): Arr? = fields[key] as? Arr
        fun str(key: String): String? = (fields[key] as? Str)?.value
        fun int(key: String): Int? = (fields[key] as? Num)?.toInt()
        fun double(key: String): Double? = (fields[key] as? Num)?.toDouble()
        fun bool(key: String): Boolean? = (fields[key] as? Bool)?.value
    }

    companion object {
        fun of(value: Int): Num = Num(value.toString())
        fun of(value: Double): Num = Num(
            if (value == Math.floor(value) && !value.isInfinite() && kotlin.math.abs(value) < 1e15) {
                value.toLong().toString()
            } else {
                value.toString()
            }
        )
        fun of(value: String): Str = Str(value)
        fun of(value: Boolean): Bool = Bool(value)
    }
}

class JsonParseException(message: String) : IllegalArgumentException(message)

object MiniJson {
    private const val MAX_DEPTH = 256

    fun parse(text: String): JsonValue {
        val parser = Parser(text.removePrefix("﻿"))
        parser.skipWhitespace()
        val value = parser.readValue(0)
        parser.skipWhitespace()
        if (!parser.atEnd()) parser.fail("Trailing content")
        return value
    }

    /** Decodes UTF-8 (with or without BOM) and parses. */
    fun parse(bytes: ByteArray): JsonValue = parse(String(bytes, Charsets.UTF_8))

    fun parseObject(bytes: ByteArray): JsonValue.Obj =
        parse(bytes) as? JsonValue.Obj ?: throw JsonParseException("Root is not an object")

    /**
     * Pretty-prints with one value per line.
     *
     * This layout is required, not cosmetic: the Cubism SDK JSON parser only
     * accepts a number when it is followed directly by ',' or a newline
     * ("[0, 1]" or "1 }" fail to load), and it does not accept exponents.
     * The converter writes every file the SDK will read through here.
     */
    fun write(value: JsonValue, indent: String = "  "): String {
        val out = StringBuilder()
        writeValue(out, value, indent, 0)
        out.append('\n')
        return out.toString()
    }

    private fun writeValue(out: StringBuilder, value: JsonValue, indent: String, level: Int) {
        when (value) {
            is JsonValue.Null -> out.append("null")
            is JsonValue.Bool -> out.append(if (value.value) "true" else "false")
            is JsonValue.Num -> out.append(value.raw)
            is JsonValue.Str -> writeString(out, value.value)
            is JsonValue.Arr -> {
                if (value.items.isEmpty()) {
                    out.append("[]")
                    return
                }
                out.append("[\n")
                value.items.forEachIndexed { i, item ->
                    repeat(level + 1) { out.append(indent) }
                    writeValue(out, item, indent, level + 1)
                    if (i < value.items.size - 1) out.append(',')
                    out.append('\n')
                }
                repeat(level) { out.append(indent) }
                out.append(']')
            }
            is JsonValue.Obj -> {
                if (value.fields.isEmpty()) {
                    out.append("{}")
                    return
                }
                out.append("{\n")
                val entries = value.fields.entries.toList()
                entries.forEachIndexed { i, (key, item) ->
                    repeat(level + 1) { out.append(indent) }
                    writeString(out, key)
                    out.append(": ")
                    writeValue(out, item, indent, level + 1)
                    if (i < entries.size - 1) out.append(',')
                    out.append('\n')
                }
                repeat(level) { out.append(indent) }
                out.append('}')
            }
        }
    }

    private fun writeString(out: StringBuilder, value: String) {
        out.append('"')
        for (c in value) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (c < ' ') out.append(String.format("\\u%04x", c.code)) else out.append(c)
            }
        }
        out.append('"')
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun atEnd() = i >= s.length

        fun fail(message: String): Nothing = throw JsonParseException("$message at offset $i")

        fun skipWhitespace() {
            while (i < s.length) {
                val c = s[i]
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                    i++
                } else if (c == '/' && i + 1 < s.length && s[i + 1] == '/') {
                    // Tolerate // comments: some hand-edited model3.json files have them.
                    while (i < s.length && s[i] != '\n') i++
                } else {
                    return
                }
            }
        }

        fun readValue(depth: Int): JsonValue {
            if (depth > MAX_DEPTH) fail("Nesting too deep")
            if (atEnd()) fail("Unexpected end")
            return when (val c = s[i]) {
                '{' -> readObject(depth)
                '[' -> readArray(depth)
                '"' -> JsonValue.Str(readString())
                't' -> literal("true", JsonValue.Bool(true))
                'f' -> literal("false", JsonValue.Bool(false))
                'n' -> literal("null", JsonValue.Null)
                else -> if (c == '-' || c in '0'..'9') readNumber() else fail("Unexpected '$c'")
            }
        }

        private fun literal(word: String, value: JsonValue): JsonValue {
            if (!s.startsWith(word, i)) fail("Expected $word")
            i += word.length
            return value
        }

        private fun readObject(depth: Int): JsonValue.Obj {
            val obj = JsonValue.Obj()
            i++ // {
            skipWhitespace()
            if (i < s.length && s[i] == '}') {
                i++
                return obj
            }
            while (true) {
                skipWhitespace()
                if (atEnd() || s[i] != '"') fail("Expected key")
                val key = readString()
                skipWhitespace()
                if (atEnd() || s[i] != ':') fail("Expected ':'")
                i++
                skipWhitespace()
                obj.fields[key] = readValue(depth + 1)
                skipWhitespace()
                if (atEnd()) fail("Unterminated object")
                when (s[i]) {
                    ',' -> {
                        i++
                        skipWhitespace()
                        // Tolerate a trailing comma before '}'.
                        if (i < s.length && s[i] == '}') {
                            i++
                            return obj
                        }
                    }
                    '}' -> {
                        i++
                        return obj
                    }
                    else -> fail("Expected ',' or '}'")
                }
            }
        }

        private fun readArray(depth: Int): JsonValue.Arr {
            val arr = JsonValue.Arr()
            i++ // [
            skipWhitespace()
            if (i < s.length && s[i] == ']') {
                i++
                return arr
            }
            while (true) {
                skipWhitespace()
                arr.items += readValue(depth + 1)
                skipWhitespace()
                if (atEnd()) fail("Unterminated array")
                when (s[i]) {
                    ',' -> {
                        i++
                        skipWhitespace()
                        if (i < s.length && s[i] == ']') {
                            i++
                            return arr
                        }
                    }
                    ']' -> {
                        i++
                        return arr
                    }
                    else -> fail("Expected ',' or ']'")
                }
            }
        }

        private fun readString(): String {
            i++ // opening quote
            val out = StringBuilder()
            while (true) {
                if (atEnd()) fail("Unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (atEnd()) fail("Bad escape")
                        when (val e = s[i++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) fail("Bad unicode escape")
                                val hex = s.substring(i, i + 4)
                                out.append(hex.toIntOrNull(16)?.toChar() ?: fail("Bad unicode escape"))
                                i += 4
                            }
                            else -> fail("Bad escape '\\$e'")
                        }
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun readNumber(): JsonValue.Num {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '+' || s[i] == '-')) {
                i++
            }
            val raw = s.substring(start, i)
            val value = raw.toDoubleOrNull() ?: fail("Bad number '$raw'")
            if (value.isInfinite() || raw.length > 64) fail("Number out of range '$raw'")
            val exponent = raw.substringAfter('e', raw.substringAfter('E', "0")).toIntOrNull()
            // Plain-decimal rewriting (for the Cubism parser) of 1e999999 would need gigabytes.
            if (exponent == null || kotlin.math.abs(exponent) > 40) fail("Number out of range '$raw'")
            return JsonValue.Num(raw)
        }
    }
}
