package com.kabutarbaazi.domain.moderation

/**
 * Content screening and the auto-hide threshold.
 *
 * Google Play requires apps carrying user-generated content to moderate it, not merely to
 * collect reports. Three mechanisms do that work here: a keyword screen before content is
 * posted, an automatic hide once enough distinct people report something, and an admin queue.
 *
 * AUTO_HIDE_THRESHOLD is duplicated in SQL (submit_report). That is a two-language
 * specification: change both in the same commit. The test below asserts the constant so the
 * duplication is at least visible when it drifts.
 */
object ModerationRules {

    /**
     * Three DISTINCT reporters, enforced by a unique constraint on (reporter, target). Three
     * taps from one angry buyer does nothing.
     */
    const val AUTO_HIDE_THRESHOLD = 3

    /**
     * Reports from accounts younger than this are recorded but do not count toward auto-hide.
     * Combined with one-account-per-phone, this makes brigading a competitor expensive.
     */
    const val MIN_REPORTER_ACCOUNT_AGE_MS = 24L * 60 * 60 * 1000

    fun shouldAutoHide(distinctReporters: Int, targetOwnerIsAdmin: Boolean = false): Boolean =
        !targetOwnerIsAdmin && distinctReporters >= AUTO_HIDE_THRESHOLD

    fun reportCountsTowardThreshold(reporterAccountAgeMs: Long): Boolean =
        reporterAccountAgeMs >= MIN_REPORTER_ACCOUNT_AGE_MS

    // ---------------------------------------------------------------------------------------
    // Keyword screen
    // ---------------------------------------------------------------------------------------

    /**
     * Scam patterns common to Indian and Pakistani classifieds. These do not block a post: they
     * flag it into the admin queue while it stays visible. Blocking on these outright would
     * catch honest sellers negotiating payment, and a false block is worse than a slow review.
     */
    private val SCAM_PATTERNS: List<Regex> = listOf(
        // Advance-payment pressure, the single most common marketplace fraud here.
        "\\badvance\\s*(payment|paisa|paise|money)\\b",
        "\\bpehle\\s*(paisa|paise|payment|transfer)\\b",
        "\\bpaisa\\s*pehle\\b",
        // The army-officer/army-quota impersonation scam, endemic on Indian classifieds.
        "\\barmy\\s*(officer|quota|man|posting)\\b",
        "\\bmilitary\\s*(officer|quota)\\b",
        // OTP harvesting. There is no legitimate reason to ask for someone's OTP.
        "\\botp\\s*(bhejo|batao|send|share|do)\\b",
        "\\bsend\\s*(me\\s*)?(the\\s*)?otp\\b",
        // Scan-to-receive QR fraud: the victim scans a "receive" code that actually pays out.
        "\\bscan\\s*(karo|kar|the)?\\s*qr\\b",
        "\\bqr\\s*code\\s*scan\\b",
        // Fake courier/delivery charge up front.
        "\\b(courier|delivery|shipping)\\s*charge[s]?\\s*(pehle|first|advance)\\b",
    ).map { Regex(it, setOf(RegexOption.IGNORE_CASE)) }

    /**
     * Unambiguous abuse. Kept deliberately short: this list must be curated by a native Hindi
     * and Urdu speaker before it is trusted, and every addition risks a false positive on
     * pigeon slang. Word boundaries are mandatory. Breed names such as "lakka", "banka" and
     * "teddy" must never be caught by a substring match.
     */
    private val ABUSE_PATTERNS: List<Regex> = listOf(
        "\\b(m+a+d+a+r+c+h+o+d+|b+h+e+n+c+h+o+d+|b+e+h+e+n+c+h+o+d+)\\w*\\b",
        "\\bg+a+n+d+u+\\w*\\b",
        "\\bh+a+r+a+m+i+\\w*\\b",
        "\\bk+u+t+t+i+y+a+\\w*\\b",
        "\\bf+u+c+k+\\w*\\b",
        "\\bb+i+t+c+h+\\w*\\b",
    ).map { Regex(it, setOf(RegexOption.IGNORE_CASE)) }

    enum class Severity {
        /** Post it. */
        Clean,

        /** Post it, but surface it in the admin queue. */
        Review,

        /** Refuse the post and tell the user why. */
        Block,
    }

    data class ScreenResult(val severity: Severity, val matched: List<String>)

    fun screen(text: String): ScreenResult {
        if (text.isBlank()) return ScreenResult(Severity.Clean, emptyList())

        val abuse = ABUSE_PATTERNS.mapNotNull { it.find(text)?.value }
        if (abuse.isNotEmpty()) return ScreenResult(Severity.Block, abuse)

        val scam = SCAM_PATTERNS.mapNotNull { it.find(text)?.value }
        if (scam.isNotEmpty()) return ScreenResult(Severity.Review, scam)

        return ScreenResult(Severity.Clean, emptyList())
    }
}
