package com.nuvio.tv.core.streams

import com.nuvio.tv.data.local.StreamBadgeSettingsDataStore
import com.nuvio.tv.domain.model.AddonStreams
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamBadgePresentation @Inject constructor(
    private val dataStore: StreamBadgeSettingsDataStore
) {
    private val badgeFilterCache = AtomicReference<Pair<StreamBadgeRules, List<CompiledStreamBadgeFilter>>?>()

    private val lastRulesFilters = AtomicReference<Pair<StreamBadgeRules, List<CompiledStreamBadgeFilter>>?>()

    private val badgeInstancePool = AtomicReference<HashMap<String, StreamBadge>>(HashMap(64))

    suspend fun apply(groups: List<AddonStreams>): List<AddonStreams> {
        return withContext(Dispatchers.Default) {
            apply(groups, dataStore.settings.first().rules)
        }
    }

    suspend fun hasActiveFilters(): Boolean =
        withContext(Dispatchers.Default) {
            getBadgeFilters(dataStore.settings.first().rules).isNotEmpty()
        }

    fun apply(groups: List<AddonStreams>, rules: StreamBadgeRules): List<AddonStreams> {
        val filters = getBadgeFilters(rules)
        if (filters.isEmpty()) return groups
        val pool = badgeInstancePool.get()
        return groups.map { group ->
            group.copy(
                streams = group.streams.map { stream ->
                    val matchedBadges = StreamBadgeMatcher.matchedBadgesPooled(stream, filters, pool)
                    if (matchedBadges.isEmpty()) {
                        stream
                    } else {
                        stream.copy(badges = mergeBadges(stream.badges, matchedBadges, pool))
                    }
                }
            )
        }
    }

    // Fork: player OSD badge lookup, cached per rule set.
    private val osdBadgeCache = java.util.concurrent.ConcurrentHashMap<String, java.util.Optional<StreamBadge>>()
    @Volatile private var osdBadgeCacheRules: StreamBadgeRules? = null

    /**
     * The user's badge for one thing the player is actually playing (e.g. "DDP", "5.1").
     * Badge rules are written against release names, so each candidate [tokens] entry is placed
     * into a neutral release name; only badges that appear because of that token count, which
     * ignores badges the neutral name triggers on its own. Prefers a badge whose name is one of
     * the tokens. Null when the user has no badge for it.
     */
    suspend fun osdBadge(tokens: List<String>): StreamBadge? {
        if (tokens.isEmpty()) return null
        val rules = dataStore.settings.first().rules.normalized()
        val filters = getBadgeFilters(rules)
        if (filters.isEmpty()) return null
        if (osdBadgeCacheRules != rules) {
            osdBadgeCache.clear()
            osdBadgeCacheRules = rules
        }
        return osdBadgeCache.getOrPut(tokens.joinToString("\u0000")) {
            java.util.Optional.ofNullable(resolveOsdBadge(tokens, filters))
        }.orElse(null)
    }

    private fun resolveOsdBadge(tokens: List<String>, filters: List<CompiledStreamBadgeFilter>): StreamBadge? {
        fun probe(text: String) = Stream(
            name = null, title = text, description = null, url = null, ytId = null, infoHash = null,
            fileIdx = null, externalUrl = null, behaviorHints = null, addonName = "", addonLogo = null
        )
        fun norm(value: String) = value.lowercase().filterNot { it == ' ' || it == '.' || it == '-' || it == '_' || it == ':' }
        val baseline = StreamBadgeMatcher.matchedBadges(probe(OSD_PROBE_NAME.format("")), filters)
            .mapTo(HashSet()) { it.dedupeKey() }
        val wanted = tokens.mapTo(HashSet(), ::norm)
        for (token in tokens) {
            val added = StreamBadgeMatcher.matchedBadges(probe(OSD_PROBE_NAME.format(".$token")), filters)
                .filter { it.imageURL.isNotBlank() && it.dedupeKey() !in baseline }
            if (added.isNotEmpty()) return added.firstOrNull { norm(it.name) in wanted } ?: added.first()
        }
        return null
    }

    private companion object {
        /** A plain release name; "%s" takes ".<token>". */
        const val OSD_PROBE_NAME = "Movie.Title.2022.WEB-DL%s.x264-NUVIO.mkv"
    }

    suspend fun badgesFor(stream: Stream): List<StreamBadge> {
        val rules = dataStore.settings.first().rules
        val filters = getBadgeFilters(rules)
        if (filters.isEmpty()) return emptyList()
        return StreamBadgeMatcher.matchedBadges(stream, filters)
    }

    private fun getBadgeFilters(rules: StreamBadgeRules): List<CompiledStreamBadgeFilter> {
        // The settings flow hands out one rules instance per change, so the same
        // instance arrives for every batch: skip normalizing and deep-comparing it.
        lastRulesFilters.get()?.let { (source, compiled) ->
            if (source === rules) return compiled
        }
        val normalized = rules.normalized()
        val cached = badgeFilterCache.get()
        if (cached?.first == normalized) {
            lastRulesFilters.set(rules to cached.second)
            return cached.second
        }
        val compiled = StreamBadgeMatcher.compile(normalized)
        // Rebuild the badge instance pool from compiled filters so all
        // matching operations reuse these canonical instances.
        val newPool = HashMap<String, StreamBadge>(compiled.size * 2)
        compiled.forEach { filter ->
            val key = filter.badge.dedupeKey()
            newPool.putIfAbsent(key, filter.badge)
        }
        badgeInstancePool.set(newPool)
        badgeFilterCache.set(normalized to compiled)
        lastRulesFilters.set(rules to compiled)
        return compiled
    }

    private fun mergeBadges(
        existing: List<StreamBadge>,
        matched: List<StreamBadge>,
        pool: HashMap<String, StreamBadge>
    ): List<StreamBadge> {
        // Fast path: no existing badges, just return matched (already pooled).
        if (existing.isEmpty()) return matched

        val capacity = existing.size + matched.size
        val merged = LinkedHashMap<String, StreamBadge>(capacity)
        existing.forEach { badge ->
            val key = badge.dedupeKey()
            if (key !in merged) merged[key] = pool.getOrPut(key) { badge }
        }
        matched.forEach { badge ->
            val key = badge.dedupeKey()
            if (key !in merged) merged[key] = badge // already pooled
        }
        return StreamBadgeMatcher.mostExactDts(merged.values.toList())
    }
}
