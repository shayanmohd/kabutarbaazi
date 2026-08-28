package com.kabutarbaazi.domain.model

/**
 * What a user can report, and why.
 *
 * animal_cruelty and illegal_sale are not filler. A live-animal marketplace needs both, and
 * their presence in the reporting sheet is the clearest signal to a Play reviewer that the
 * moderation story was thought about rather than bolted on.
 */
enum class ReportTargetType(val wire: String) {
    Listing("listing"),
    Reel("reel"),
    ReelComment("reel_comment"),
    Post("post"),
    PostComment("post_comment"),
    Message("message"),
    User("user"),
}

enum class ReportReason(val wire: String) {
    Spam("spam"),
    ScamOrFraud("scam_or_fraud"),
    AnimalCruelty("animal_cruelty"),
    NudityOrSexual("nudity_or_sexual"),
    Violence("violence"),
    HateOrHarassment("hate_or_harassment"),
    Impersonation("impersonation"),
    IllegalSale("illegal_sale"),
    Other("other"),
}
