package com.kabutarbaazi.domain.model

/**
 * The breed catalogue. This is the canonical source: the SQL seed for the `breeds` table is
 * generated from this list, so names live in exactly one place.
 *
 * Breeds are drawn from what kabutarbaaz in North India and Pakistan actually keep and trade:
 * the Pakistani highflyer families (Teddy, Sialkoti, Kamagar, Banka), the Indian highflyers
 * (Rampoori, Ferozpori, Madrasi), and the fancy breeds. "Other" exists because regional naming
 * varies enormously and a fixed list would otherwise turn sellers away.
 *
 * Hindi and Urdu names are transliterations of the same spoken name, which is how keepers say
 * them. A native speaker should still review this list before launch.
 */
data class Breed(
    val slug: String,
    val nameEn: String,
    val nameHi: String,
    val nameUr: String,
)

object Breeds {
    val ALL: List<Breed> = listOf(
        Breed("teddy", "Teddy", "टेडी", "ٹیڈی"),
        Breed("golden", "Golden", "गोल्डन", "گولڈن"),
        Breed("sherazi", "Sherazi", "शेराज़ी", "شیرازی"),
        Breed("lakka", "Lakka", "लक्का", "لکا"),
        Breed("sialkoti", "Sialkoti", "सियालकोटी", "سیالکوٹی"),
        Breed("kamagar", "Kamagar", "कामगर", "کامگر"),
        Breed("banka", "Banka", "बांका", "بانکا"),
        Breed("rampoori", "Rampoori", "रामपुरी", "رامپوری"),
        Breed("ferozpori", "Ferozpori", "फ़िरोज़पुरी", "فیروزپوری"),
        Breed("kalsira", "Kalsira", "कालसिरा", "کالسرا"),
        Breed("madrasi", "Madrasi Highflyer", "मद्रासी", "مدراسی"),
        Breed("lahori", "Lahori", "लाहौरी", "لاہوری"),
        Breed("mookee", "Mookee", "मुक्खी", "مکھی"),
        Breed("fantail", "Fantail", "फैनटेल", "فین ٹیل"),
        Breed("jacobin", "Jacobin", "जैकोबिन", "جیکوبن"),
        Breed("homer", "Racing Homer", "होमर", "ہومر"),
        Breed("tippler", "Tippler", "टिपलर", "ٹپلر"),
        Breed("other", "Other", "अन्य", "دیگر"),
    )

    val BY_SLUG: Map<String, Breed> = ALL.associateBy { it.slug }

    fun isValid(slug: String): Boolean = slug in BY_SLUG
}
