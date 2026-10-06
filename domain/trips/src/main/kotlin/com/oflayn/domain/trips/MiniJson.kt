package com.oflayn.domain.trips

/**
 * Dependency-free JSON model + parser + writer.
 * The whole offline core is pure Kotlin/JVM so it can be compiled and tested without Android.
 */
sealed class J {
    data class O(val m: Map<String, J>) : J()
    data class A(val l: List<J>) : J()
    data class S(val v: String) : J()
    data class N(val v: Double) : J()
    data class B(val v: Boolean) : J()
    data object Z : J()
}

fun jobj(vararg p: Pair<String, J>): J.O = J.O(p.toMap())
fun jarr(v: List<J>): J.A = J.A(v)
fun js(v: String): J = J.S(v)
fun jn(v: Number): J = J.N(v.toDouble())
fun jb(v: Boolean): J = J.B(v)

private fun numText(d: Double): String {
    if (d.isNaN() || d.isInfinite()) return "0"
    val l = d.toLong()
    return if (l.toDouble() == d) l.toString() else d.toString()
}

fun J?.str(def: String = ""): String = when (this) {
    is J.S -> v
    is J.N -> numText(v)
    is J.B -> if (v) "true" else "false"
    else -> def
}

fun J?.num(def: Double = 0.0): Double = when (this) {
    is J.N -> v
    is J.S -> v.toDoubleOrNull() ?: def
    is J.B -> if (v) 1.0 else 0.0
    else -> def
}

fun J?.int(def: Int = 0): Int = num(def.toDouble()).toInt()

fun J?.bool(def: Boolean = false): Boolean = when (this) {
    is J.B -> v
    is J.S -> v.equals("true", true)
    is J.N -> v != 0.0
    else -> def
}

fun J?.at(k: String): J? = (this as? J.O)?.m?.get(k)
fun J?.at(i: Int): J? = (this as? J.A)?.l?.getOrNull(i)
fun J?.items(): List<J> = (this as? J.A)?.l ?: emptyList()
val J?.isNull: Boolean get() = this == null || this is J.Z

object Json {
    fun parse(text: String): J = Parser(text).run { ws(); value() }

    fun write(v: J): String = StringBuilder().also { w(v, it) }.toString()

    private fun w(v: J, sb: StringBuilder) {
        when (v) {
            is J.O -> {
                sb.append('{'); var first = true
                for ((k, x) in v.m) { if (!first) sb.append(','); first = false; sb.append(quote(k)).append(':'); w(x, sb) }
                sb.append('}')
            }
            is J.A -> { sb.append('['); v.l.forEachIndexed { i, x -> if (i > 0) sb.append(','); w(x, sb) }; sb.append(']') }
            is J.S -> sb.append(quote(v.v))
            is J.N -> sb.append(numText(v.v))
            is J.B -> sb.append(if (v.v) "true" else "false")
            is J.Z -> sb.append("null")
        }
    }

    fun quote(s: String): String {
        val sb = StringBuilder(s.length + 2); sb.append('"')
        for (c in s) when {
            c == '"' -> sb.append("\\\"")
            c == '\\' -> sb.append("\\\\")
            c == '\n' -> sb.append("\\n")
            c == '\r' -> sb.append("\\r")
            c == '\t' -> sb.append("\\t")
            c < ' ' -> sb.append("\\u").append("%04x".format(c.code))
            else -> sb.append(c)
        }
        sb.append('"'); return sb.toString()
    }

    private class Parser(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }

        fun value(): J {
            ws()
            if (i >= s.length) return J.Z
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> J.S(string())
                't' -> { i += 4; J.B(true) }
                'f' -> { i += 5; J.B(false) }
                'n' -> { i += 4; J.Z }
                else -> number()
            }
        }

        fun obj(): J.O {
            val m = LinkedHashMap<String, J>()
            i++; ws()
            if (i < s.length && s[i] == '}') { i++; return J.O(m) }
            while (i < s.length) {
                ws(); val k = string(); ws()
                if (i < s.length && s[i] == ':') i++
                m[k] = value(); ws()
                if (i < s.length && s[i] == ',') { i++; continue }
                if (i < s.length && s[i] == '}') { i++; break }
                break
            }
            return J.O(m)
        }

        fun arr(): J.A {
            val l = ArrayList<J>()
            i++; ws()
            if (i < s.length && s[i] == ']') { i++; return J.A(l) }
            while (i < s.length) {
                l.add(value()); ws()
                if (i < s.length && s[i] == ',') { i++; continue }
                if (i < s.length && s[i] == ']') { i++; break }
                break
            }
            return J.A(l)
        }

        fun string(): String {
            ws()
            if (i < s.length && s[i] == '"') i++
            val sb = StringBuilder()
            while (i < s.length) {
                val c = s[i]
                if (c == '"') { i++; break }
                if (c == '\\' && i + 1 < s.length) {
                    i++
                    when (val e = s[i]) {
                        'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                        'b' -> sb.append('\b'); 'f' -> sb.append('\u000C'); '/' -> sb.append('/')
                        '\\' -> sb.append('\\'); '"' -> sb.append('"')
                        'u' -> { val h = s.substring(i + 1, minOf(i + 5, s.length)); i += 4; sb.append((h.toIntOrNull(16) ?: 32).toChar()) }
                        else -> sb.append(e)
                    }
                    i++
                } else { sb.append(c); i++ }
            }
            return sb.toString()
        }

        fun number(): J.N {
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] == '-' || s[i] == '+' || s[i] == '.' || s[i] == 'e' || s[i] == 'E')) i++
            if (st == i) { i++; return J.N(0.0) }
            return J.N(s.substring(st, i).toDoubleOrNull() ?: 0.0)
        }
    }
}

/** Reads a gzipped or plain UTF-8 payload (the bundled offline assets are gzipped). */
object Gz {
    fun read(bytes: ByteArray): String {
        val body = if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte())
            java.util.zip.GZIPInputStream(bytes.inputStream()).use { it.readBytes() } else bytes
        return String(body, Charsets.UTF_8)
    }
}
