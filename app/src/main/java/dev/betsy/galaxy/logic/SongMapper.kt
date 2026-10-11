package dev.betsy.galaxy.logic

import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class GalaxySong(
    val messageId: String,
    val playerSource: String,
    val title: String,
    val artist: String,
    val pageUrl: String,
    val audioUrl: String,
    val artwork: String,
    val channelId: String,
    val reactionCount: Int,
    val uuid: String,
    val wlmTrackId: String,
    val wlmArtistId: String,
) {
    val mediaKey: String
        get() = if (playerSource == "wlm") "wlm:$wlmTrackId" else "showcase:$messageId"

    fun bookmarkKey(): String {
        if (uuid.matches(UUID_PATTERN)) return "suno:$uuid"
        if (wlmTrackId.matches(WLM_ID)) return "wlm:$wlmTrackId"
        if (messageId.matches(MESSAGE_ID)) return "msg:$messageId"
        return ""
    }

    fun bookmarkSunoUrl(): String {
        val page = pageUrl.substringBefore("?").trim()
        if (page.startsWith("https://suno.com/song/") ||
            page.startsWith("https://www.suno.com/song/") ||
            page.startsWith("https://app.suno.com/song/")
        ) {
            return page.take(300)
        }
        return if (uuid.matches(UUID_PATTERN)) "https://suno.com/song/${uuid.lowercase()}" else ""
    }

    fun bookmarkWlmUrl(): String =
        if (wlmTrackId.matches(WLM_ID)) "https://welovemusic.ai/track/$wlmTrackId" else ""

    fun toPlayerJson(fullyListened: Boolean): JSONObject {
        val json = JSONObject()
            .put("message_id", messageId)
            .put("source", playerSource)
            .put("title", title)
            .put("artist", artist)
            .put("url", pageUrl)
            .put("page_url", pageUrl)
            .put("audio_url", audioUrl)
            .put("artwork", artwork)
            .put("channel_id", channelId)
            .put("reaction_count", reactionCount)
            .put("uuid", uuid)
            .put("fully_listened", fullyListened)
        if (playerSource == "wlm") {
            json.put("wlm_track_id", wlmTrackId)
                .put("wlm_artist_id", wlmArtistId)
                .put("wlm_artist_name", artist)
        } else {
            json.put("discord_display_name", artist)
        }
        return json
    }

    companion object {
        private val UUID_PATTERN = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        private val WLM_ID = Regex("^[A-Za-z0-9_-]{1,100}$")
        private val MESSAGE_ID = Regex("^[A-Za-z0-9_-]{1,80}$")
        private val ART_UUID = Regex(
            """image_(?:large_)?([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})""",
        )

        fun fromApi(raw: JSONObject): GalaxySong? {
            val apiSource = raw.optString("source")
            val id = raw.optString("id").trim()
            val title = raw.optString("title").trim().ifEmpty { "Unknown song" }
            val artist = raw.optString("artist").trim().ifEmpty { "Unknown artist" }
            val audio = raw.optString("audio_url").trim()
            if (id.isEmpty() || audio.isEmpty() || (apiSource != "showcase" && apiSource != "wlm")) return null
            val artwork = raw.optString("artwork").trim()
            val uuid = ART_UUID.find(artwork)?.groupValues?.get(1)?.lowercase().orEmpty()
            return if (apiSource == "wlm") {
                GalaxySong(
                    messageId = id,
                    playerSource = "wlm",
                    title = title,
                    artist = artist,
                    pageUrl = raw.optString("page_url"),
                    audioUrl = audio,
                    artwork = artwork,
                    channelId = "",
                    reactionCount = raw.optInt("reaction_count"),
                    uuid = uuid,
                    wlmTrackId = id,
                    wlmArtistId = raw.optString("artist_id"),
                )
            } else {
                GalaxySong(
                    messageId = id,
                    playerSource = "discord",
                    title = title,
                    artist = artist,
                    pageUrl = raw.optString("page_url"),
                    audioUrl = audio,
                    artwork = artwork,
                    channelId = raw.optString("channel_id"),
                    reactionCount = raw.optInt("reaction_count"),
                    uuid = uuid,
                    wlmTrackId = "",
                    wlmArtistId = "",
                )
            }
        }
    }
}

object MediaIds {
    fun song(kind: String, collection: String, days: Int, limit: Int, songId: String): String =
        encode(listOf("song", kind, collection, days.toString(), limit.toString(), songId))

    fun folder(vararg parts: String): String = encode(listOf("folder") + parts.toList())

    fun decode(id: String): List<String> =
        id.split("|").map { URLDecoder.decode(it, StandardCharsets.UTF_8) }

    private fun encode(parts: List<String>): String =
        parts.joinToString("|") { URLEncoder.encode(it, StandardCharsets.UTF_8) }
}
