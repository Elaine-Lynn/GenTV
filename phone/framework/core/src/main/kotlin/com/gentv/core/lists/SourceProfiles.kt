package com.gentv.core.lists

import kotlinx.serialization.json.*

/** Input routes from model-specific community records. Similar-brand matches never activate a route. */
class SourceProfiles(json: String) {
    private data class Profile(val model: DebloatList, val android: Set<String>, val config: JsonObject)
    private val profiles = Json.parseToJsonElement(json).jsonArray.map { element ->
        val row = element.jsonObject
        val id = row.getValue("id").jsonPrimitive.content
        val title = row.getValue("title").jsonPrimitive.content
        val versions = (row["android"] as? JsonArray).orEmpty().map { it.jsonPrimitive.content }.toSet()
        Profile(DebloatList(id, title, emptyList(), emptyList(), emptyList(), null, emptySet(), false, false),
            versions, routing(row.getValue("config").jsonObject))
    }

    fun select(tv: TvInfo): String? {
        val candidates = profiles.filter { it.android.isEmpty() || tv.release.substringBefore('.') in it.android }
            .filter { brandMatches(tv, it.model) && ModelMatcher.score(tv, it.model) >= ModelMatcher.EXACT }
        // Shared board codenames can match several models. Conflicting routes require manual configuration.
        return candidates.map { it.config }.distinct().singleOrNull()?.takeIf { it.isNotEmpty() }?.toString()
    }

    private fun brandMatches(tv: TvInfo, model: DebloatList): Boolean {
        val tokens = (model.title + " " + model.id.replace('-', ' ')).lowercase().split(Regex("[^a-z0-9_]+"))
        return listOf(tv.brand, tv.manufacturer).map { it.lowercase() }.any {
            it.length >= 2 && (it in tokens || model.id.startsWith("$it-"))
        }
    }

    companion object {
        private val strings = setOf("live_tv_package", "live_tv_action", "live_tv_input_id",
            "source_panel_service", "source_panel_action", "source_panel_extra", "input_action", "input_uri_scheme")
        private val booleans = setOf("coocaa_sources", "handle_view_inputs")
        val keys = strings + booleans + setOf("input_sources", "input_key_keycodes")

        /** Whitelist hardware routing fields; never import dock apps, cleanup lists or arbitrary settings. */
        fun routing(config: JsonObject): JsonObject = JsonObject(config.filter { (key, value) ->
            when (key) {
                in strings -> value is JsonPrimitive && value.isString
                in booleans -> value is JsonPrimitive && !value.isString && value.booleanOrNull != null
                "input_sources" -> value is JsonArray && value.all { it is JsonPrimitive && it.isString }
                "input_key_keycodes" -> value is JsonArray && value.all { it is JsonPrimitive && !it.isString && it.intOrNull != null }
                else -> false
            }
        })
    }
}
