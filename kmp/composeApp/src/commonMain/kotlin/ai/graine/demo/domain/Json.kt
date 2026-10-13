package ai.graine.demo.domain

/**
 * JSON for the demo's own files — the use-case packs and the call log kept on
 * the device. Objects are `Map<String, Any?>` in key order, arrays `List<Any?>`,
 * whole numbers `Long`, others `Double`. Small, so the app needs no JSON library.
 */
object Json {
    fun parse(text: String): Any? {
        val p = Parser(text)
        p.space()
        val v = p.value()
        p.space()
        require(p.done) { "unexpected text after the value" }
        return v
    }

    fun write(value: Any?): String = buildString { put(this, value) }

    private fun put(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is String -> {
                out.append('"')
                for (c in value) when (c) {
                    '"' -> out.append("\\\"")
                    '\\' -> out.append("\\\\")
                    '\n' -> out.append("\\n")
                    '\r' -> out.append("\\r")
                    '\t' -> out.append("\\t")
                    else -> if (c.code < 0x20) out.append("\\u").append(c.code.toString(16).padStart(4, '0')) else out.append(c)
                }
                out.append('"')
            }
            is Boolean, is Int, is Long -> out.append(value.toString())
            is Number -> out.append(value.toDouble().let { if (it.isFinite()) it.toString() else "null" })
            is Map<*, *> -> {
                out.append('{')
                value.entries.forEachIndexed { i, (k, v) ->
                    if (i > 0) out.append(',')
                    put(out, k.toString())
                    out.append(':')
                    put(out, v)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                value.forEachIndexed { i, v ->
                    if (i > 0) out.append(',')
                    put(out, v)
                }
                out.append(']')
            }
            else -> put(out, value.toString())
        }
    }

    private class Parser(private val s: String) {
        private var i = 0
        val done: Boolean get() = i >= s.length

        fun space() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(): Any? {
            require(!done) { "the text ended" }
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw IllegalArgumentException("unexpected '$c'")
            }
        }

        private fun word(w: String, v: Any?): Any? {
            require(s.startsWith(w, i)) { "expected $w" }
            i += w.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            i++
            space()
            if (!done && s[i] == '}') { i++; return out }
            while (true) {
                space()
                val k = str()
                space()
                require(!done && s[i] == ':') { "expected ':'" }
                i++
                space()
                out[k] = value()
                space()
                require(!done) { "the object never closed" }
                if (s[i++] == '}') return out
            }
        }

        private fun arr(): List<Any?> {
            val out = ArrayList<Any?>()
            i++
            space()
            if (!done && s[i] == ']') { i++; return out }
            while (true) {
                space()
                out.add(value())
                space()
                require(!done) { "the array never closed" }
                if (s[i++] == ']') return out
            }
        }

        private fun str(): String {
            require(!done && s[i] == '"') { "expected a string" }
            i++
            val out = StringBuilder()
            while (true) {
                require(!done) { "the string never closed" }
                when (val c = s[i++]) {
                    '"' -> return out.toString()
                    '\\' -> when (val e = s[i++]) {
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'u' -> { out.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        else -> out.append(e)
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun num(): Any {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val text = s.substring(start, i)
            return text.toLongOrNull() ?: text.toDouble()
        }
    }
}

/** Reading a JSON object leniently, the way the packs are read. */
internal typealias JsonObject = Map<String, Any?>

@Suppress("UNCHECKED_CAST")
internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.req(key: String): JsonObject = obj(key) ?: throw UseCasePack.PackException("missing \"$key\"")

internal fun JsonObject.str(key: String): String = this[key] as? String ?: throw UseCasePack.PackException("missing \"$key\"")

internal fun JsonObject.optStr(key: String): String? = (this[key] as? String)?.takeIf { it.isNotEmpty() }

@Suppress("UNCHECKED_CAST")
internal fun JsonObject.objects(key: String): List<JsonObject> = (this[key] as? List<*>).orEmpty().mapNotNull { it as? JsonObject }

internal fun JsonObject.strings(key: String): List<String> = (this[key] as? List<*>).orEmpty().filterIsInstance<String>()

internal fun JsonObject.int(key: String): Int? = when (val v = this[key]) {
    is Long -> v.toInt()
    is Double -> v.toInt()
    else -> null
}
