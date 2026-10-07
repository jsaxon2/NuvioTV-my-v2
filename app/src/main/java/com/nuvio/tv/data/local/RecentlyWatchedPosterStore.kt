package com.nuvio.tv.data.local

import android.content.Context

/**
 * Remembers which poster belongs to each Recently Watched title ("type:id" -> URL) across
 * launches, so the row shows artwork straight away and only new titles hit the addons.
 * Posters are a property of the title, not the viewer, so one store serves every profile.
 */
class RecentlyWatchedPosterStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    data class Entry(val posterUrl: String, val isFresh: Boolean)

    /** The saved poster for [key], or null. Entries older than [MAX_AGE_MS] come back stale. */
    fun get(key: String, nowEpochMs: Long): Entry? {
        val raw = preferences.getString(key, null) ?: return null
        val savedAt = raw.substringBefore(SEPARATOR, "").toLongOrNull() ?: return null
        val url = raw.substringAfter(SEPARATOR, "").takeIf(String::isNotBlank) ?: return null
        return Entry(url, isFresh = nowEpochMs - savedAt < MAX_AGE_MS)
    }

    fun put(key: String, posterUrl: String, nowEpochMs: Long) {
        val editor = preferences.edit().putString(key, "$nowEpochMs$SEPARATOR$posterUrl")
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
        const val MAX_ENTRIES = 1000
        const val MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L
    }
}
