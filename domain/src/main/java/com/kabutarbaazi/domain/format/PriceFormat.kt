package com.kabutarbaazi.domain.format

/**
 * Prices are stored as minor units (paise / paisa) so no float ever touches money.
 *
 * Formatting uses the South Asian grouping system (last three digits, then pairs) because
 * 1,50,000 is what a seller in Delhi reads as one lakh fifty thousand. Western grouping
 * (150,000) is legible but reads as foreign.
 *
 * Digits are always ASCII, in every locale. NumberFormat under ur-PK can emit Eastern
 * Arabic-Indic digits, and a price rendered as ۴۵۰۰ in a marketplace reads as a bug to the
 * very users it is meant to serve. Buyers in both India and Pakistan read prices in ASCII.
 */
object PriceFormat {

    fun symbol(currency: String): String = when (currency.uppercase()) {
        "INR" -> "₹"   // rupee sign
        "PKR" -> "Rs"
        else -> currency.uppercase()
    }

    /** South Asian digit grouping: 1234567 becomes 12,34,567. */
    fun groupSouthAsian(value: Long): String {
        val negative = value < 0
        val digits = kotlin.math.abs(value).toString()
        if (digits.length <= 3) return if (negative) "-$digits" else digits

        val lastThree = digits.takeLast(3)
        val rest = digits.dropLast(3)
        val grouped = rest.reversed().chunked(2).joinToString(",").reversed()
        val out = "$grouped,$lastThree"
        return if (negative) "-$out" else out
    }

    /** Full display form, for example "₹12,500". Minor units are dropped: nobody prices a pigeon in paise. */
    fun format(minorUnits: Long, currency: String = "INR"): String {
        val major = minorUnits / 100
        return "${symbol(currency)}${groupSouthAsian(major)}"
    }

    /**
     * Compact form for dense list cards, where a full number crowds the row.
     * 150000 paise becomes "₹1.5K", 12500000 becomes "₹1.25L".
     */
    fun formatCompact(minorUnits: Long, currency: String = "INR"): String {
        val major = minorUnits / 100
        val sym = symbol(currency)
        return when {
            major >= 10_000_000 -> "$sym${trimZeros(major / 100_000.0 / 100.0)}Cr"
            major >= 100_000 -> "$sym${trimZeros(major / 100_000.0)}L"
            major >= 1_000 -> "$sym${trimZeros(major / 1_000.0)}K"
            else -> "$sym$major"
        }
    }

    private fun trimZeros(v: Double): String {
        val rounded = kotlin.math.round(v * 100) / 100
        return if (rounded == kotlin.math.floor(rounded)) rounded.toLong().toString()
        else rounded.toString().trimEnd('0').trimEnd('.')
    }

    /** Parses what a user typed into minor units. Returns null when it is not a usable amount. */
    fun parseToMinor(input: String): Long? {
        val cleaned = input.filter { it.isDigit() }
        if (cleaned.isEmpty()) return null
        val major = cleaned.toLongOrNull() ?: return null
        if (major > MAX_MAJOR) return null
        return major * 100
    }

    /** A ten lakh ceiling. Above this the listing is almost certainly a typo or a scam. */
    const val MAX_MAJOR = 1_000_000L
}
