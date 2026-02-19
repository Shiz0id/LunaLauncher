package com.lunasysman.launcher.core.justtype.providers

import com.lunasysman.launcher.core.justtype.model.JustTypeItemUi
import com.lunasysman.launcher.core.model.LaunchPoint

object AppsProvider {
    fun itemsFor(
        query: String,
        allApps: List<LaunchPoint>,
        favorites: List<LaunchPoint>,
        nowEpochMs: Long,
        maxItems: Int = 50,
    ): List<JustTypeItemUi.LaunchPointItem> {
        val q = query.trim()
        if (q.isEmpty()) {
            val favoritesIds = favorites.asSequence().map { it.id }.toHashSet()
            val recents =
                allApps.asSequence()
                    .filter { it.lastLaunchedAtEpochMs != null }
                    .sortedWith(
                        compareByDescending<LaunchPoint> { it.lastLaunchedAtEpochMs ?: Long.MIN_VALUE }
                            .thenBy { it.title.lowercase() }
                            .thenBy { it.id },
                    )
                    .filterNot { it.id in favoritesIds }
                    .toList()

            val merged =
                buildList {
                    addAll(favorites)
                    addAll(recents)
                }.take(maxItems)

            return merged.map { lp -> JustTypeItemUi.LaunchPointItem(lpId = lp.id) }
        }

        val qLower = q.lowercase()
        val tokens = qLower.split(Regex("\\s+")).filter { it.isNotBlank() }.distinct()

        data class Scored(
            val lp: LaunchPoint,
            val score: Int,
        )

        val scored =
            allApps.asSequence()
                .mapNotNull { lp ->
                    val titleLower = lp.title.lowercase()
                    val isTitlePrefix = titleLower.startsWith(qLower)
                    val isSubstring = titleLower.contains(qLower)
                    val isTokenPrefix =
                        tokens.isNotEmpty() &&
                            tokens.all { t ->
                                titleLower.split(Regex("[^\\p{L}\\p{N}]+")).any { word -> word.startsWith(t) }
                            }

                    if (!isSubstring && !isTitlePrefix && !isTokenPrefix) return@mapNotNull null

                    val pinnedScore = if (lp.pinned) 20 else 0
                    val recencyScore = recencyScore(lp.lastLaunchedAtEpochMs, nowEpochMs)
                    val matchScore =
                        when {
                            isTokenPrefix -> 100
                            isTitlePrefix -> 90
                            else -> 60
                        }

                    Scored(
                        lp = lp,
                        score = matchScore + pinnedScore + recencyScore,
                    )
                }
                .sortedWith(
                    compareByDescending<Scored> { it.score }
                        .thenByDescending { it.lp.pinned }
                        .thenByDescending { it.lp.lastLaunchedAtEpochMs ?: Long.MIN_VALUE }
                        .thenBy { it.lp.title.lowercase() }
                        .thenBy { it.lp.id },
                )
                .take(maxItems)
                .toList()

        return scored.map { s -> JustTypeItemUi.LaunchPointItem(lpId = s.lp.id) }
    }

    private fun recencyScore(lastLaunchedAtEpochMs: Long?, nowEpochMs: Long): Int {
        if (lastLaunchedAtEpochMs == null) return 0
        val ageMs = (nowEpochMs - lastLaunchedAtEpochMs).coerceAtLeast(0L)
        val ageDays = (ageMs / DAY_MS).toInt()
        // Bucketed decay (stable within a day): 25 at 0 days, down to 0 at >=30 days.
        val score = 25 - (ageDays * 25 / 30)
        return score.coerceIn(0, 25)
    }

    private const val DAY_MS: Long = 24L * 60L * 60L * 1000L
}
