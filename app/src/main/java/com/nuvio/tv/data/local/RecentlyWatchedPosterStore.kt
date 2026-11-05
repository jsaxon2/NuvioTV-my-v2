package com.nuvio.tv.data.local

import android.content.Context

/**
 * Remembers the artwork that belongs to each Recently Watched title ("type:id" -> poster,
 * backdrop, logo) across launches, so the row and its hero show artwork straight away and only
 * new titles hit the addons. Artwork is a property of the title, not the viewer, so one store
 * serves every profile.
 */
class RecentlyWatchedPosterStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    data class Artwork(val poster: String?, val background: String?, val logo: String?)

    data class Entry(val artwork: Artwork, val isFresh: Boolean)

    /**
     * The saved artwork for [key], or null. Entries older than [MAX_AGE_MS] come back stale, and
     * so do entries saved before backdrops were kept (poster only), so those get looked up again.
     */
    fun get(key: String, nowEpochMs: Long): Entry? {
        val raw = preferences.getString(key, null) ?: return null
        val savedAt = raw.substringBefore(SEPARATOR, "").toLongOrNull() ?: return null
        val fields = raw.substringAfter(SEPARATOR, "").split(FIELD_SEPARATOR)
        val artwork = Artwork(
            poster = fields.getOrNull(0)?.takeIf(String::isNotBlank),
            background = fields.getOrNull(1)?.takeIf(String::isNotBlank),
            logo = fields.getOrNull(2)?.takeIf(String::isNotBlank)
        )
        if (artwork.poster == null && artwork.background == null) return null
        val hasBackdropField = fields.size > 1
        return Entry(artwork, isFresh = hasBackdropField && nowEpochMs - savedAt < MAX_AGE_MS)
    }

    fun put(key: String, artwork: Artwork, nowEpochMs: Long) {
        val value = listOf(artwork.poster, artwork.background, artwork.logo)
            .joinToString(FIELD_SEPARATOR.toString()) { it.orEmpty() }
        val editor = preferences.edit().putString(key, "$nowEpochMs$SEPARATOR$value")
        val all = preferences.all
        if (all.size >= MAX_ENTRIES && !all.containsKey(key)) {
            // Drop the oldest tenth so the file stays small without pruning on every write.
            all.entries
                .sortedBy { (it.value as? String)?.substringBefore(SEPARATOR, "")?.toLongOrNull() ?: 0L }
                .take(MAX_ENTRIES / 10)
                .forEach { editor.remove(it.key) }
        }
        editor.apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "recently_watched_posters"
        const val SEPARATOR = '|'
        const val FIELD_SEPARATOR = '\t'
        const val MAX_ENTRIES = 1000
        const val MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L
    }
}
