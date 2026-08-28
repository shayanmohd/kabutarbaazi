package com.kabutarbaazi.domain.model

/**
 * Regions double as the community list: there is exactly one community per region, seeded by
 * an admin. Users cannot create their own groups in v1, which removes an entire class of
 * moderation problem and matches how keepers already organise, by area.
 *
 * The split of Uttar Pradesh into west and east is not administrative tidiness. They are
 * genuinely distinct kabutarbaazi scenes with different breeds and different circuits, and
 * lumping them together would make the busiest community useless.
 */
data class Region(
    val code: String,
    val nameEn: String,
    val nameHi: String,
    val nameUr: String,
)

object Regions {
    val ALL: List<Region> = listOf(
        Region("delhi", "Delhi", "दिल्ली", "دہلی"),
        Region("west_up", "West UP", "पश्चिमी यूपी", "مغربی یوپی"),
        Region("east_up", "East UP", "पूर्वी यूपी", "مشرقی یوپی"),
        Region("punjab", "Punjab", "पंजाब", "پنجاب"),
        Region("haryana", "Haryana", "हरियाणा", "ہریانہ"),
        Region("bihar", "Bihar", "बिहार", "بہار"),
        Region("rajasthan", "Rajasthan", "राजस्थान", "راجستھان"),
        Region("mp", "Madhya Pradesh", "मध्य प्रदेश", "مدھیہ پردیش"),
        Region("maharashtra", "Maharashtra", "महाराष्ट्र", "مہاراشٹر"),
        Region("bengal", "West Bengal", "पश्चिम बंगाल", "مغربی بنگال"),
        Region("hyderabad", "Hyderabad", "हैदराबाद", "حیدرآباد"),
        Region("pakistan", "Pakistan", "पाकिस्तान", "پاکستان"),
        Region("other", "Other", "अन्य", "دیگر"),
    )

    val BY_CODE: Map<String, Region> = ALL.associateBy { it.code }

    fun isValid(code: String): Boolean = code in BY_CODE
}
