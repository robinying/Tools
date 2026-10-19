package com.robin.tools.tool

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persists favorite tool ids and the recent-tool list. */
class ToolPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _favorites = MutableStateFlow(
        prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet().orEmpty()
    )
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _recents = MutableStateFlow(parseRecents(prefs.getString(KEY_RECENTS, null)))
    val recents: StateFlow<List<String>> = _recents.asStateFlow()

    fun toggleFavorite(id: String) {
        val updated = _favorites.value.toMutableSet().apply {
            if (!add(id)) remove(id)
        }
        _favorites.value = updated
        prefs.edit().putStringSet(KEY_FAVORITES, updated).apply()
    }

    fun recordRecent(id: String) {
        val updated = buildList {
            add(id)
            addAll(_recents.value.filterNot { it == id })
        }.take(MAX_RECENTS)
        _recents.value = updated
        prefs.edit().putString(KEY_RECENTS, updated.joinToString(SEPARATOR)).apply()
    }

    private fun parseRecents(raw: String?): List<String> =
        raw?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?.filter { ToolRegistry.byId(it) != null }
            ?.take(MAX_RECENTS)
            .orEmpty()

    private companion object {
        const val PREFS_NAME = "tool_preferences"
        const val KEY_FAVORITES = "favorites"
        const val KEY_RECENTS = "recents"
        const val MAX_RECENTS = 6
        const val SEPARATOR = "\n"
    }
}
