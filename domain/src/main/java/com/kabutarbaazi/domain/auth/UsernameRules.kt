package com.kabutarbaazi.domain.auth

import java.text.Normalizer

/**
 * Usernames are the app's identity: there is no email, and a seller's username is what a buyer
 * trusts. The rules are therefore strict about anything that lets one account impersonate another.
 */
object UsernameRules {

    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 20

    /**
     * Supabase Auth requires an email address, so one is derived deterministically from the
     * username. The user never sees, types, or is told about this address.
     *
     * The domain must be one we actually control and must have no MX record. Pointing synthetic
     * addresses at a domain someone else owns would leak every signup to a stranger's mail server.
     */
    const val EMAIL_DOMAIN = "users.kamapathy.app"

    private val PATTERN = Regex("^[a-z][a-z0-9._]{2,19}$")

    /** Adjacent or trailing separators let `ravi.singh` and `ravi..singh` read as the same person. */
    private val ADJACENT_SEPARATORS = Regex("[._]{2,}")

    private val RESERVED = setOf(
        "admin", "administrator", "moderator", "mod", "support", "help", "helpdesk",
        "official", "team", "staff", "kabutarbaazi", "kabutar", "kabootar",
        "root", "system", "api", "www", "app", "null", "undefined",
        "deleted", "anonymous", "me", "you", "everyone", "here",
    )

    /** Lowercase, trim, and fold compatibility forms so visually identical inputs collapse. */
    fun normalize(raw: String): String =
        Normalizer.normalize(raw.trim(), Normalizer.Form.NFKC).lowercase()

    fun validate(raw: String): UsernameError? {
        val u = normalize(raw)
        return when {
            u.isEmpty() -> UsernameError.Empty
            u.length < MIN_LENGTH -> UsernameError.TooShort
            u.length > MAX_LENGTH -> UsernameError.TooLong
            !u[0].isLetter() -> UsernameError.MustStartWithLetter
            !PATTERN.matches(u) -> UsernameError.IllegalCharacters
            ADJACENT_SEPARATORS.containsMatchIn(u) -> UsernameError.AdjacentSeparators
            u.last() == '.' || u.last() == '_' -> UsernameError.TrailingSeparator
            u in RESERVED -> UsernameError.Reserved
            else -> null
        }
    }

    fun isValid(raw: String): Boolean = validate(raw) == null

    /**
     * @throws IllegalArgumentException if the username is not valid. Callers must validate first;
     *   deriving an address from an unchecked string is how a malformed account gets created.
     */
    fun syntheticEmail(raw: String): String {
        val u = normalize(raw)
        require(isValid(u)) { "Cannot derive an email from an invalid username: $raw" }
        return "$u@$EMAIL_DOMAIN"
    }
}

enum class UsernameError {
    Empty,
    TooShort,
    TooLong,
    MustStartWithLetter,
    IllegalCharacters,
    AdjacentSeparators,
    TrailingSeparator,
    Reserved,
}
