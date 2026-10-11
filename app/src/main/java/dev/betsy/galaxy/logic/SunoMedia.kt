package dev.betsy.galaxy.logic

import org.json.JSONObject
import java.net.URI

/**
 * Same choice as Betsy `fetch_metadata`: the song video is `video_cover_url`.
 * The plain `video_url` on a Suno clip is the karaoke video and is not used here.
 */
object SunoMedia {
    fun fromClip(clip: JSONObject): JSONObject {
        val image = asset(clip.optString("image_large_url")).ifEmpty { asset(clip.optString("image_url")) }
        return JSONObject()
            .put("image_url", image)
            .put("video_url", asset(clip.optString("video_cover_url")))
            .put("title", clip.optString("title").trim().take(200))
            .put("artist", clip.optString("display_name").trim().take(200))
    }

    fun asset(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return ""
        val uri = try {
            URI(value)
        } catch (error: Exception) {
            return ""
        }
        val host = uri.host?.lowercase().orEmpty()
        if (uri.scheme != "https" || uri.userInfo != null) return ""
        if (host != "suno.ai" && !host.endsWith(".suno.ai")) return ""
        return value
    }
}
