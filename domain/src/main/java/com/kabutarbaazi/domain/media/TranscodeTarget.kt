package com.kabutarbaazi.domain.media

/**
 * Computes the encode target for a video before upload.
 *
 * 720p at roughly 1.6 Mbps is deliberate. On the 5 to 6.7 inch phones that dominate this market
 * 720p is indistinguishable from 1080p in a vertical feed, while 1080p at 4 Mbps triples storage
 * and stalls on rural connections. At 1.6 Mbps a two second start buffer is about 400 KB, which
 * opens in well under a second on a typical 3 to 5 Mbps link.
 */
object TranscodeTarget {

    const val SHORT_SIDE = 720
    const val VIDEO_BITRATE = 1_600_000
    const val AUDIO_BITRATE = 96_000

    data class Target(
        val width: Int,
        val height: Int,
        val videoBitrate: Int,
        val audioBitrate: Int,
    )

    /**
     * Scales so the short side is 720, preserving aspect ratio, and never upscales: a source
     * already smaller than the target is re-encoded at its own size rather than blown up.
     *
     * Both dimensions are rounded to even numbers. H.264 with 4:2:0 chroma subsampling cannot
     * encode odd dimensions, and several hardware encoders fail outright rather than adjusting.
     */
    fun forSource(sourceWidth: Int, sourceHeight: Int, sourceBitrate: Int? = null): Target {
        require(sourceWidth > 0 && sourceHeight > 0) { "Source dimensions must be positive" }

        val shortSide = minOf(sourceWidth, sourceHeight)
        val scale = if (shortSide <= SHORT_SIDE) 1.0 else SHORT_SIDE.toDouble() / shortSide

        val w = even((sourceWidth * scale).toInt())
        val h = even((sourceHeight * scale).toInt())

        // Never spend more bits than the source actually had.
        val bitrate = sourceBitrate?.let { minOf(it, VIDEO_BITRATE) } ?: VIDEO_BITRATE

        return Target(w, h, bitrate, AUDIO_BITRATE)
    }

    private fun even(v: Int): Int = if (v % 2 == 0) v else v - 1

    /** Rough size prediction, used to warn before a long export that will be rejected anyway. */
    fun estimatedBytes(durationMs: Int, target: Target): Long =
        ((target.videoBitrate + target.audioBitrate).toLong() * durationMs / 1000L) / 8L
}
