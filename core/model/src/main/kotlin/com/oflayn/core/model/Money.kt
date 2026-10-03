package com.oflayn.core.model

import java.math.BigDecimal

/** Money in kuruş. Never use Double for currency. */
@JvmInline
value class Kurus(val value: Long) : Comparable<Kurus> {
    operator fun plus(other: Kurus) = Kurus(Math.addExact(value, other.value))
    operator fun minus(other: Kurus) = Kurus(Math.subtractExact(value, other.value))
    override fun compareTo(other: Kurus): Int = value.compareTo(other.value)

    /** Locale-independent, e.g. "12.50". Presentation layer formats per locale. */
    fun toPlainString(): String {
        val sign = if (value < 0) "-" else ""
        val abs = Math.abs(value)
        return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }

    companion object {
        val ZERO = Kurus(0)
        /** Parses "12.50"; throws if more than 2 decimals. */
        fun ofLira(text: String): Kurus = Kurus(BigDecimal(text).movePointRight(2).longValueExact())
    }
}
