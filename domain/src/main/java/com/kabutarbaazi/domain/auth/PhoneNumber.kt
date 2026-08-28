package com.kabutarbaazi.domain.auth

/**
 * Phone handling for India and Pakistan, which is where kabutarbaazi actually happens.
 *
 * Deliberately not libphonenumber: that library carries roughly 2 MB of metadata for the whole
 * world, and this app needs two country codes and a generic fallback. Regex, fully tested.
 */
object PhoneNumber {

    /** Indian mobile numbers start 6-9 and are 10 digits. Landlines cannot receive WhatsApp. */
    private val INDIA = Regex("^\\+91[6-9][0-9]{9}$")

    /** Pakistani mobile numbers are +92 3xx xxxxxxx. */
    private val PAKISTAN = Regex("^\\+923[0-9]{9}$")

    /** Generic E.164 for everyone else. */
    private val E164 = Regex("^\\+[1-9][0-9]{7,14}$")

    val COUNTRY_CODES = listOf(
        CountryCode("IN", "+91", "India"),
        CountryCode("PK", "+92", "Pakistan"),
    )

    /**
     * Builds an E.164 number from a dial code and whatever the user typed. Strips spaces, dashes
     * and brackets, and drops a leading 0, which Indian users type out of habit.
     */
    fun parse(dialCode: String, localPart: String): String? {
        val digits = localPart.filter { it.isDigit() }.trimStart('0')
        if (digits.isEmpty()) return null
        val cc = dialCode.trim().removePrefix("+").filter { it.isDigit() }
        if (cc.isEmpty()) return null
        val candidate = "+$cc$digits"
        return if (isValid(candidate)) candidate else null
    }

    /**
     * Country-specific rules take precedence over the generic fallback. Checking the fallback as
     * an alternative would defeat the point: +915876543210 is a well-formed E.164 string but not
     * a valid Indian mobile, and accepting it produces a WhatsApp button that goes nowhere.
     */
    fun isValid(e164: String): Boolean = when {
        e164.startsWith("+91") -> INDIA.matches(e164)
        e164.startsWith("+92") -> PAKISTAN.matches(e164)
        else -> E164.matches(e164)
    }

    /**
     * WhatsApp's click-to-chat link. wa.me wants the number with no plus and no separators.
     * The prefill text is what turns a cold tap into an actual conversation.
     */
    fun waMeUrl(e164: String, prefillText: String? = null): String {
        require(isValid(e164)) { "Not a valid phone number: $e164" }
        val bare = e164.removePrefix("+")
        return if (prefillText.isNullOrBlank()) {
            "https://wa.me/$bare"
        } else {
            "https://wa.me/$bare?text=${urlEncode(prefillText)}"
        }
    }

    /**
     * Percent-encoding for the wa.me query. java.net.URLEncoder encodes a space as '+', which
     * WhatsApp renders literally as a plus sign, so this encodes to %20 instead.
     */
    private fun urlEncode(s: String): String = buildString {
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt().toChar()
            if (c.isLetterOrDigit() && c.code < 128 || c in "-_.~") append(c)
            else append('%').append("%02X".format(b))
        }
    }

    /** Last two digits only, for showing which number is on file without revealing it. */
    fun mask(e164: String): String =
        if (e164.length < 4) "•••" else "•••••• ${e164.takeLast(2)}"
}

data class CountryCode(val iso: String, val dial: String, val name: String)
