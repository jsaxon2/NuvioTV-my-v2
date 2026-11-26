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

    data class Notice(val tracker: String, val atMs: Long, val reason: String)

    private const val TAG = "TrackerDisconnect"
    private const val PREFS = "tracker_disconnect_notices"
    private const val KEY = "pending"

    private var prefs: SharedPreferences? = null
    private val _pending = MutableStateFlow<List<Notice>>(emptyList())
    val pending: StateFlow<List<Notice>> = _pending.asStateFlow()

    fun init(context: Context) {
        val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        _pending.value = runCatching { decode(store.getString(KEY, null)) }.getOrDefault(emptyList())
    }

    /** A tracker was signed out by its service; [reason] is what it answered (e.g. "HTTP 401 on token refresh"). */
    fun report(tracker: String, reason: String) {
        Log.w(TAG, "$tracker signed out: $reason")
        val notice = Notice(tracker, System.currentTimeMillis(), reason)
        update { list -> list.filterNot { it.tracker == tracker } + notice }
    }

    /** The viewer answered the dialog: Reconnect or Leave off. */
    fun dismissAll() = update { emptyList() }

    private fun update(change: (List<Notice>) -> List<Notice>) {
        synchronized(this) {
            val next = change(_pending.value)
            _pending.value = next
            prefs?.edit()?.putString(KEY, encode(next))?.apply()
        }
    }

    private fun encode(list: List<Notice>): String = JSONArray().apply {
        list.forEach { put(JSONObject().put("t", it.tracker).put("at", it.atMs).put("r", it.reason)) }
    }.toString()

    private fun decode(raw: String?): List<Notice> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Notice(o.getString("t"), o.getLong("at"), o.optString("r"))
        }
    }
}
