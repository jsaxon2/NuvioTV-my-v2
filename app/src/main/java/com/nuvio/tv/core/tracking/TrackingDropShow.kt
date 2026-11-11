package com.nuvio.tv.core.tracking

import android.util.Log
import com.nuvio.tv.data.mdblist.MdbListApiClient
import com.nuvio.tv.data.mdblist.MdbListIds
import com.nuvio.tv.data.mdblist.MdbListSyncRepository
import com.nuvio.tv.data.mdblist.requestIds
import com.nuvio.tv.data.repository.TraktProgressService
import com.nuvio.tv.data.simkl.SimklMutationService
import com.nuvio.tv.data.wetrakr.WeTrakrPlanningBatch
import com.nuvio.tv.domain.model.LibraryEntryInput
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fork: marks a show Dropped on every connected tracker, for Continue watching's Drop show.
 *
 * Simkl moves it to its Dropped status, WeTrakr to its Dropped list, Trakt hides it under "dropped"
 * (the section the app already reads back) and MDBList adds it to its dropped shows. Watch history,
 * ratings and library lists are left alone, so the show can be picked up again where it was. Each
 * tracker is tried on its own: one that fails or isn't connected doesn't stop the others.
 */
@Singleton
class TrackingDropShowService @Inject constructor(
    private val providers: TrackingProviderRegistry,
    private val simkl: SimklMutationService,
    private val weTrakr: WeTrakrPlanningBatch,
    private val traktProgress: TraktProgressService,
    private val mdbListApi: MdbListApiClient,
    private val mdbListSync: MdbListSyncRepository
) {
    /** The trackers the show was dropped on. */
    suspend fun dropShow(contentId: String, title: String, year: Int?): List<TrackingProviderId> = coroutineScope {
        val attempts = buildList {
            if (connected(TrackingProviderId.SIMKL)) add(TrackingProviderId.SIMKL to suspend { dropOnSimkl(contentId, title, year) })
            if (connected(TrackingProviderId.WETRAKR)) add(TrackingProviderId.WETRAKR to suspend { dropOnWeTrakr(contentId, title, year) })
            if (connected(TrackingProviderId.TRAKT)) add(TrackingProviderId.TRAKT to suspend { traktProgress.dropShow(contentId) })
            if (connected(TrackingProviderId.MDBLIST)) add(TrackingProviderId.MDBLIST to suspend { dropOnMdbList(contentId) })
        }
        attempts.map { (provider, drop) ->
            async {
                val dropped = runCatching { drop() }
                    .onFailure { Log.w(TAG, "drop on ${provider.storageId} failed: ${it.javaClass.simpleName}") }
                    .getOrDefault(false)
                provider.takeIf { dropped }
            }
        }.mapNotNull { it.await() }
    }

    /** Whether [provider] is signed in, so Drop show can reach it. */
    fun isConnected(provider: TrackingProviderId): Boolean = providers.isAuthenticated(provider)

    private fun connected(provider: TrackingProviderId) = isConnected(provider)

    private suspend fun dropOnSimkl(contentId: String, title: String, year: Int?): Boolean {
        val reference = buildTrackingMediaReference(
            contentType = "series",
            parentMetaId = contentId,
            title = title,
            releaseInfo = year?.toString()
        )
        val result = simkl.moveToList(listOf(reference), TrackingListStatus.DROPPED)
        return result.attemptedCount > 0 && result.isComplete
    }

    private suspend fun dropOnWeTrakr(contentId: String, title: String, year: Int?): Boolean =
        weTrakr.drop(LibraryEntryInput(itemId = contentId, itemType = "series", title = title, year = year))

    private suspend fun dropOnMdbList(contentId: String): Boolean {
        val ids = parseTrackingExternalIds(contentId)
        val mdbListIds = MdbListIds(imdb = ids.imdb, tmdb = ids.tmdb, tvdb = ids.tvdb?.toLongOrNull(), trakt = ids.trakt)
        if (mdbListIds.imdb == null && mdbListIds.tmdb == null && mdbListIds.tvdb == null && mdbListIds.trakt == null) return false
        val body = buildJsonObject {
            put("shows", JsonArray(listOf(buildJsonObject { put("ids", mdbListIds.requestIds()) })))
        }.toString()
        val response = mdbListApi.post("/sync/dropped", body, mdbListSync.currentScope())
        if (response.status !in 200..299) return false
        mdbListSync.refreshAsync(TrackingRefreshIntent.INVALIDATED)
        return true
    }

    private companion object {
        const val TAG = "DropShow"
    }
}
