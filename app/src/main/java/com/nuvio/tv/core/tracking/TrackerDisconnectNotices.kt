package com.nuvio.tv.core.tracking

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Fork: trackers that signed themselves out because their service rejected the sign-in (not a sign-out the
 * viewer chose). Home shows them in a dialog to reconnect or leave off. Kept on the box across restarts until the
 * viewer answers, and logged with what the service said, to help find why it happens.
 */
object TrackerDisconnectNotices {

    /** [profileId] is the profile whose tracker was signed out; tracker sign-ins belong to a profile. */
    data class Notice(val tracker: String, val atMs: Long, val reason: String, val profileId: Int)

    private const val TAG = "TrackerDisconnect"
    private const val PREFS = "tracker_disconnect_notices"
    private const val KEY = "pending"
    private const val NO_PROFILE = -1

    private var prefs: SharedPreferences? = null
    private var activeProfileFlow: StateFlow<Int> = MutableStateFlow(NO_PROFILE)

    /** The profile in use, so the dialog shows only its own trackers. */
    val activeProfile: StateFlow<Int> get() = activeProfileFlow
    private val _pending = MutableStateFlow<List<Notice>>(emptyList())
    val pending: StateFlow<List<Notice>> = _pending.asStateFlow()

    fun init(context: Context, activeProfile: StateFlow<Int>) {
        activeProfileFlow = activeProfile
        val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        _pending.value = runCatching { decode(store.getString(KEY, null)) }.getOrDefault(emptyList())
    }

    /** A tracker was signed out by its service; [reason] is what it answered (e.g. "HTTP 401 on token refresh"). */
    fun report(tracker: String, reason: String, profileId: Int = activeProfileFlow.value) {
        Log.w(TAG, "$tracker signed out on profile $profileId: $reason")
        val notice = Notice(tracker, System.currentTimeMillis(), reason, profileId)
        update { list -> list.filterNot { it.tracker == tracker && it.profileId == profileId } + notice }
    }

    /** The notices for [profileId]: its own, and any recorded without a profile. */
    fun forProfile(list: List<Notice>, profileId: Int): List<Notice> =
        list.filter { it.profileId == profileId || it.profileId == NO_PROFILE }

    /** The viewer answered the dialog on [profileId]: Reconnect or Leave off. Other profiles keep theirs. */
    fun dismissFor(profileId: Int) = update { list -> list.filterNot { it in forProfile(list, profileId) } }

    private fun update(change: (List<Notice>) -> List<Notice>) {
        synchronized(this) {
            val next = change(_pending.value)
            _pending.value = next
            prefs?.edit()?.putString(KEY, encode(next))?.apply()
        }
    }

    private fun encode(list: List<Notice>): String = JSONArray().apply {
        list.forEach {
            put(JSONObject().put("t", it.tracker).put("at", it.atMs).put("r", it.reason).put("p", it.profileId))
        }
    }.toString()

    private fun decode(raw: String?): List<Notice> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Notice(o.getString("t"), o.getLong("at"), o.optString("r"), o.optInt("p", NO_PROFILE))
        }
    }
}
