package dev.betsy.galaxy.logic

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class RemoteFailure(val status: Int, val code: String) : Exception(friendlyApiError(code))

fun friendlyApiError(code: String): String = when (code) {
    "app_api_disabled" -> "The app API is turned off in Galaxy admin."
    "unauthorized" -> "The app token was rejected."
    "channel_unavailable" -> "That showcase channel is not released to the app."
    "wlm_unavailable" -> "We Love Music is not available."
    "invalid_limit" -> "That planet count is outside the allowed range."
    "invalid_range" -> "That time range is outside the allowed range."
    "invalid_collection" -> "Choose a collection first."
    "invalid_source" -> "That music source is not available."
    "audio_unavailable", "wlm_audio_unavailable" -> "Audio could not be loaded."
    "not_found" -> "That song is no longer available."
    else -> if (code.isBlank()) "Request failed." else code.replace('_', ' ')
}

class PlayerBackend(
    private val engine: ExplorerEngine,
    private val fetch: (String) -> JSONObject,
    private val loadClip: (String) -> JSONObject = { JSONObject() },
    private val onExpedition: (List<GalaxySong>, String) -> Unit = { _, _ -> },
) {
    private var sourcesCache: JSONObject? = null
    private var sourcesAt = 0L
    var expeditionSongs: List<GalaxySong> = emptyList()
        private set
    var expeditionToken: String = ""
        private set

    fun request(method: String, path: String, body: String): String {
        return try {
            dispatch(method.uppercase(), path.substringBefore("?"), body)
        } catch (error: RemoteFailure) {
            errorJson(error.status, error.code, error.message ?: friendlyApiError(error.code))
        } catch (error: IllegalArgumentException) {
            errorJson(400, "bad_request", error.message ?: "Bad request.")
        } catch (error: IllegalStateException) {
            errorJson(409, "rejected", error.message ?: "Request rejected.")
        }
    }

    fun sources(force: Boolean = false): JSONObject {
        val now = System.currentTimeMillis()
        val cached = sourcesCache
        if (!force && cached != null && now - sourcesAt < 30_000) return cached
        val fresh = fetch("/sources")
        sourcesCache = fresh
        sourcesAt = now
        return fresh
    }

    fun loadSongs(kind: String, collection: String, days: Int, limit: Int): List<GalaxySong> {
        val query = when (kind) {
            "showcase" -> "/songs?source=showcase&channel_id=${enc(collection)}&days=$days&limit=$limit"
            "playlist" -> "/songs?source=wlm&kind=playlist&collection_id=${enc(collection)}&limit=$limit"
            "genre" -> "/songs?source=wlm&kind=genre&collection_id=${enc(collection)}&limit=$limit"
            "artist" -> "/songs?source=wlm&kind=artist&collection_id=${enc(collection)}&limit=$limit"
            else -> throw IllegalArgumentException("That music source is not available.")
        }
        val payload = fetch(query)
        val rows = payload.optJSONArray("songs") ?: JSONArray()
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                GalaxySong.fromApi(row)?.let { add(it) }
            }
        }
    }

    fun remember(songs: List<GalaxySong>, token: String) {
        rememberQuiet(songs, token)
        onExpedition(songs, token)
    }

    fun rememberQuiet(songs: List<GalaxySong>, token: String) {
        expeditionSongs = songs
        expeditionToken = token
    }

    fun fetchJson(path: String): JSONObject = fetch(path)

    private fun dispatch(method: String, path: String, body: String): String {
        val data = if (body.isBlank()) JSONObject() else JSONObject(body)
        return when {
            method == "GET" && path == "/galaxy/api/config" -> config()
            method == "GET" && path == "/galaxy/api/wlm/catalog" -> wlmCatalog()
            method == "POST" && path == "/galaxy/api/expeditions" -> launch(data)
            method == "POST" && path == "/galaxy/api/preferences" -> preferences(data)
            method == "GET" && path == "/galaxy/api/bookmarks" -> bookmarkPayload()
            method == "POST" && path == "/galaxy/api/bookmarks" -> bookmark(data)
            method == "POST" && path == "/galaxy/api/bookmarks/clear" -> clearBookmarks()
            method == "POST" && path == "/galaxy/api/listens/start" -> listenStart(data)
            method == "POST" && path == "/galaxy/api/listens/heartbeat" -> JSONObject().put("ok", true).toString()
            method == "POST" && path == "/galaxy/api/listens/complete" -> listenComplete(data)
            method == "POST" && path == "/galaxy/api/shop/buy" -> buy(data)
            method == "POST" && path == "/galaxy/api/loadout" -> loadout(data)
            method == "GET" && path.startsWith("/galaxy/api/media/") -> media(path.substringAfterLast("/"))
            method == "GET" && path.startsWith("/galaxy/api/wlm/artists/") -> artist(path.substringAfterLast("/"))
            method == "GET" && path.startsWith("/galaxy/api/wlm/tracks/") && path.endsWith("/ratings") ->
                ratingGet(path.removePrefix("/galaxy/api/wlm/tracks/").removeSuffix("/ratings"))
            method == "POST" && path.startsWith("/galaxy/api/wlm/tracks/") && path.endsWith("/ratings") ->
                ratingSet(path.removePrefix("/galaxy/api/wlm/tracks/").removeSuffix("/ratings"), data)
            else -> errorJson(404, "not_found", "That player request is not available.")
        }
    }

    private fun media(uuid: String): String {
        if (!uuid.matches(MEDIA_UUID)) return errorJson(404, "not_found", "That song is no longer available.")
        val clip = try {
            loadClip(uuid.lowercase())
        } catch (error: Exception) {
            return errorJson(502, "media_unavailable", "The song video could not be loaded.")
        }
        val parsed = SunoMedia.fromClip(clip)
        if (parsed.optString("image_url").isEmpty() && parsed.optString("video_url").isEmpty()) {
            return errorJson(502, "media_unavailable", "The song video could not be loaded.")
        }
        return parsed.toString()
    }

    private fun config(): String {
        val payload = sources()
        val showcase = payload.optJSONObject("showcase") ?: JSONObject()
        val channels = JSONArray()
        val rows = showcase.optJSONArray("channels") ?: JSONArray()
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            channels.put(JSONObject().put("id", row.optString("id")).put("name", row.optString("name")))
        }
        val wlm = payload.optJSONObject("wlm")?.optBoolean("available") == true
        return JSONObject()
            .put("channels", channels)
            .put("default_channel_id", showcase.optString("default_channel_id"))
            .put("default_song_limit", showcase.optInt("default_song_limit", 30))
            .put("max_song_limit", showcase.optInt("max_song_limit", 75))
            .put("max_range_days", showcase.optInt("max_range_days", 30))
            .put("mobile_planet_limit", 35)
            .put("desktop_planet_limit", 75)
            .put("target_fps", 60)
            .put("wlm_available", wlm)
            .put("upgrades", engine.upgradesJson())
            .put("profile", engine.profileJson())
            .toString()
    }

    private fun wlmCatalog(): String {
        val payload = fetch("/wlm/collections")
        val playlists = JSONArray()
        var preferred = ""
        val playlistRows = payload.optJSONArray("playlists") ?: JSONArray()
        for (index in 0 until playlistRows.length()) {
            val row = playlistRows.optJSONObject(index) ?: continue
            val id = row.optString("id")
            if (id.isBlank()) continue
            val title = row.optString("title").ifBlank { id }
            if (preferred.isEmpty() && title.replace(Regex("\\s+"), " ").trim().equals(PREFERRED_WLM_PLAYLIST, ignoreCase = true)) {
                preferred = id
            }
            playlists.put(JSONObject().put("id", id).put("slug", id).put("title", title).put("name", title))
        }
        val genres = JSONArray()
        val genreRows = payload.optJSONArray("genres") ?: JSONArray()
        for (index in 0 until genreRows.length()) {
            val row = genreRows.optJSONObject(index) ?: continue
            val name = row.optString("title").ifBlank { row.optString("id") }
            if (name.isBlank()) continue
            genres.put(JSONObject().put("name", name).put("id", row.optString("id").ifBlank { name }))
        }
        return JSONObject()
            .put("playlists", playlists)
            .put("genres", genres)
            .put("preferred_playlist_slug", preferred)
            .toString()
    }

    private fun launch(data: JSONObject): String {
        val source = data.optString("source")
        val collection = data.optString("collection_id").ifBlank { data.optString("channel_id") }
        val days = data.optInt("days", engine.expeditionDays())
        val limit = data.optInt("limit", 30)
        if (source == "suno_new") {
            throw IllegalArgumentException("Suno New Songs is not part of the app API.")
        }
        val kind = when (source) {
            "discord" -> "showcase"
            "wlm_playlist" -> "playlist"
            "wlm_genre" -> "genre"
            "wlm_artist" -> "artist"
            else -> throw IllegalArgumentException("Choose a collection first.")
        }
        if (kind != "showcase" && collection.isBlank()) throw IllegalArgumentException("Choose a collection first.")
        val channel = if (kind == "showcase") collection else ""
        if (kind == "showcase" && channel.isBlank()) throw IllegalArgumentException("Choose a showcase channel.")
        val songs = loadSongs(kind, if (kind == "showcase") channel else collection, days, limit)
        engine.fillKnownBookmarks(songs)
        val token = UUID.randomUUID().toString()
        remember(songs, token)
        val array = JSONArray()
        songs.forEach { array.put(it.toPlayerJson(engine.isFullyListened(it.messageId))) }
        return JSONObject()
            .put("token", token)
            .put("source", source)
            .put("songs", array)
            .toString()
    }

    private fun preferences(data: JSONObject): String {
        engine.savePreferences(
            auto = data.optBoolean("auto_navigation"),
            days = data.optInt("expedition_days", 7),
            skip = data.optBoolean("skip_completed"),
            collapsed = data.optBoolean("shop_collapsed"),
            volumePercent = data.optInt("volume_percent", 80),
            speed = data.optBoolean("speed_mode"),
        )
        return JSONObject().put("profile", engine.profileJson()).toString()
    }

    private fun bookmark(data: JSONObject): String {
        if (data.optString("token") != expeditionToken || expeditionToken.isEmpty()) {
            return errorJson(404, "not_found", "The Galaxy expedition no longer exists.")
        }
        val song = expeditionSongs.find { it.messageId == data.optString("message_id") }
            ?: return errorJson(400, "bad_request", "Choose the song that is currently playing.")
        val created = engine.addBookmark(song)
        return bookmarkPayload().let { payload ->
            JSONObject(payload).put("created", created).toString()
        }
    }

    private fun clearBookmarks(): String {
        engine.clearBookmarks()
        return bookmarkPayload()
    }

    private fun bookmarkPayload(): String = JSONObject()
        .put("keys", engine.bookmarkKeys())
        .put("bookmarks", engine.bookmarkItems())
        .toString()

    private fun listenStart(data: JSONObject): String {
        if (data.optString("token") != expeditionToken || expeditionToken.isEmpty()) {
            return errorJson(403, "forbidden", "Choose a song from your active expedition.")
        }
        val song = expeditionSongs.find { it.messageId == data.optString("message_id") }
            ?: return errorJson(403, "forbidden", "Choose a song from your active expedition.")
        val id = engine.startListen(song.messageId, data.optDouble("duration", 0.0))
        return JSONObject().put("listen_id", id).put("heartbeat_seconds", 12).toString()
    }

    private fun listenComplete(data: JSONObject): String {
        val result = engine.complete(data.optLong("listen_id"))
        if (!result.ok && result.message == "Listen session not found.") {
            return errorJson(404, "not_found", result.message)
        }
        if (!result.ok) return errorJson(409, "rejected", result.message)
        return JSONObject()
            .put("ok", true)
            .put("credits", result.credits)
            .put("message", result.message)
            .put("fully_listened", result.fullyListened)
            .put("profile", result.profile)
            .toString()
    }

    private fun buy(data: JSONObject): String {
        val (ok, message) = engine.buy(data.optString("upgrade_id"))
        if (!ok) return errorJson(409, "rejected", message)
        return JSONObject().put("ok", true).put("message", message).put("profile", engine.profileJson()).toString()
    }

    private fun loadout(data: JSONObject): String {
        val ok = engine.equip(data.optString("category"), data.optString("upgrade_id"))
        if (!ok) return errorJson(400, "rejected", "That upgrade cannot be equipped.")
        return JSONObject().put("ok", true).put("message", "Equipped.").put("profile", engine.profileJson()).toString()
    }

    private fun artist(id: String): String {
        val song = expeditionSongs.find { it.wlmArtistId == id }
        val name = song?.artist ?: "WLM artist"
        return JSONObject().put(
            "artist",
            JSONObject()
                .put("name", name)
                .put("bio", "Explore this artist's tracks from the app catalog.")
                .put("avatar_url", ""),
        ).toString()
    }

    private fun ratingGet(trackId: String): String =
        JSONObject().put("rating", engine.rating(trackId)).toString()

    private fun ratingSet(trackId: String, data: JSONObject): String {
        engine.setRating(trackId, data.optInt("rating"))
        return JSONObject().put("ok", true).put("rating", engine.rating(trackId)).toString()
    }

    private fun enc(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8)

    private companion object {
        val MEDIA_UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        const val PREFERRED_WLM_PLAYLIST = "tarja ravenveil weekly top 10"
    }

    private fun errorJson(status: Int, code: String, message: String): String =
        JSONObject().put("__status", status).put("error", code).put("message", message).put("description", message).toString()
}
