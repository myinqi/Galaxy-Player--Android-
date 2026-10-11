package dev.betsy.galaxy.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dev.betsy.galaxy.logic.ExplorerEngine
import dev.betsy.galaxy.logic.GalaxySong
import dev.betsy.galaxy.logic.MediaIds
import dev.betsy.galaxy.logic.PlayerBackend
import dev.betsy.galaxy.net.AppApi
import org.json.JSONArray
import org.json.JSONObject

class PlaybackDirector(
    private val engine: ExplorerEngine,
    private val backend: PlayerBackend,
    private val api: AppApi,
) {
    private val main = Handler(Looper.getMainLooper())
    private var exo: ExoPlayer? = null
    private var controls: ForwardingPlayer? = null
    private var web: WebView? = null
    @Volatile private var songs: List<GalaxySong> = emptyList()
    @Volatile private var token: String = ""
    @Volatile private var currentIndex: Int = -1
    @Volatile private var expectedUrl: String = ""
    @Volatile private var readyFor: String = ""
    private var handlingEnd = false
    private var pendingJsEnd = false
    private var ticking = false

    fun ensurePlayer(context: Context): ExoPlayer {
        exo?.let { return it }
        val sourceFactory = DefaultMediaSourceFactory(OkHttpDataSource.Factory(api.client))
        val player = ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(sourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) emitReady()
                if (playbackState == Player.STATE_ENDED) onTrackEnded()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val playerNow = exo ?: return
                if (!isPlaying && (playerNow.playbackState == Player.STATE_ENDED || nearEnd(playerNow))) return
                emit(if (isPlaying) "play" else "pause")
            }

            override fun onPlayerError(error: PlaybackException) {
                emit("error")
            }
        })
        player.volume = engine.volumePercent() / 100f
        exo = player
        controls = ExpeditionControls(player, this)
        startTicker()
        return player
    }

    fun controls(): ForwardingPlayer {
        return controls ?: error("Player is not ready.")
    }

    fun attach(view: WebView) {
        web = view
    }

    fun detach(view: WebView) {
        if (web == view) web = null
    }

    fun adopt(nextSongs: List<GalaxySong>, nextToken: String) {
        songs = nextSongs
        token = nextToken
        currentIndex = -1
        expectedUrl = ""
        readyFor = ""
        backend.rememberQuiet(nextSongs, nextToken)
    }

    fun command(json: String): String = onPlayerThread { dispatchCommand(json) }

    /**
     * The WebView bridge calls in on a background thread. ExoPlayer rejects that.
     * Commands that already run on the player thread, such as the end-of-track
     * handoff, stay there so waiting for the main looper cannot deadlock.
     */
    private fun onPlayerThread(block: () -> String): String {
        if (Looper.myLooper() == Looper.getMainLooper()) return runPlayerCommand(block)
        val result = AtomicReference("""{"error":"Playback failed."}""")
        val latch = CountDownLatch(1)
        main.post {
            result.set(runPlayerCommand(block))
            latch.countDown()
        }
        return if (latch.await(8, TimeUnit.SECONDS)) result.get() else """{"error":"Playback timed out."}"""
    }

    private fun runPlayerCommand(block: () -> String): String {
        return try {
            block()
        } catch (error: Exception) {
            JSONObject().put("error", error.message ?: "Playback failed.").toString()
        }
    }

    private fun dispatchCommand(json: String): String {
        val data = try {
            JSONObject(json)
        } catch (error: Exception) {
            return "{}"
        }
        val player = exo
        return when (data.optString("op")) {
            "play" -> {
                if (player == null) {
                    JSONObject().put("error", "Player is not ready.").toString()
                } else {
                    playUrl(data.optString("url"), true)
                    "{}"
                }
            }
            "pause" -> {
                player?.pause()
                "{}"
            }
            "seek" -> {
                seekTo(data.optDouble("position", 0.0))
                "{}"
            }
            "rate" -> {
                val rate = data.optDouble("rate", 1.0).coerceIn(1.0, 2.0)
                player?.playbackParameters = PlaybackParameters(rate.toFloat())
                "{}"
            }
            "volume" -> {
                player?.volume = data.optDouble("volume", 1.0).coerceIn(0.0, 1.0).toFloat()
                "{}"
            }
            "stop" -> {
                player?.pause()
                "{}"
            }
            "prepare" -> prepare(data.optString("url")).toString()
            "advance" -> advance().toString()
            else -> "{}"
        }
    }

    fun snapshot(): String {
        if (songs.isEmpty()) return "null"
        val array = JSONArray()
        songs.forEach { array.put(it.toPlayerJson(engine.isFullyListened(it.messageId))) }
        return JSONObject()
            .put("kind", "expedition")
            .put("token", token)
            .put("songs", array)
            .put("index", currentIndex.coerceAtLeast(0))
            .toString()
    }

    fun children(parentId: String): List<MediaItem> {
        val parts = MediaIds.decode(parentId)
        if (parts.firstOrNull() != "folder") return emptyList()
        return when (parts.getOrNull(1)) {
            "root" -> {
                val items = mutableListOf(folder("Discord showcase", MediaIds.folder("showcase")))
                val wlm = backend.sources().optJSONObject("wlm")?.optBoolean("available") == true
                if (wlm) items += folder("We Love Music", MediaIds.folder("wlm"))
                items
            }
            "showcase" -> channels()
            "channel" -> songItems("showcase", parts[2], days(), limit())
            "wlm" -> listOf(
                folder("Playlists", MediaIds.folder("playlists")),
                folder("Genres", MediaIds.folder("genres")),
            )
            "playlists" -> collectionFolders("playlists", "playlist")
            "genres" -> collectionFolders("genres", "genre")
            "playlist" -> songItems("playlist", parts[2], 0, limit())
            "genre" -> songItems("genre", parts[2], 0, limit())
            else -> emptyList()
        }
    }

    fun resolve(mediaId: String): Pair<List<GalaxySong>, Int> {
        val parts = MediaIds.decode(mediaId)
        require(parts.size >= 6 && parts[0] == "song") { "Unknown song." }
        val kind = parts[1]
        val collection = parts[2]
        val songDays = parts[3].toIntOrNull() ?: days()
        val songLimit = parts[4].toIntOrNull() ?: limit()
        val songId = parts[5]
        val loaded = backend.loadSongs(kind, collection, songDays, songLimit)
        val index = loaded.indexOfFirst { it.messageId == songId }.coerceAtLeast(0)
        require(loaded.isNotEmpty()) { "That collection has no playable songs." }
        return loaded to index
    }

    fun arm(index: Int, nextSongs: List<GalaxySong>, nextToken: String) {
        adopt(nextSongs, nextToken)
        currentIndex = index
        expectedUrl = nextSongs[index].audioUrl
        readyFor = ""
        engine.startListen(nextSongs[index].messageId, 0.0)
        notifyExpedition(index)
    }

    fun mediaItem(song: GalaxySong): MediaItem {
        val artwork = song.artwork.takeIf { it.startsWith("https://") }?.let(Uri::parse)
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle("Galaxy Player")
            .setArtworkUri(artwork)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .build()
        return MediaItem.Builder()
            .setMediaId(song.mediaKey)
            .setUri(song.audioUrl)
            .setMediaMetadata(metadata)
            .build()
    }

    fun seekTo(seconds: Double) {
        val player = exo ?: return
        val target = (seconds.coerceAtLeast(0.0) * 1000).toLong()
        val delta = target - player.currentPosition
        if (delta > 700) engine.noteSeek(delta / 1000.0)
        player.seekTo(target)
    }

    fun skip(direction: Int) {
        val player = exo ?: return
        if (direction < 0 && player.currentPosition > 3_000) {
            seekTo(0.0)
            return
        }
        val index = if (direction < 0) previousIndex(currentIndex) else nextIndex(currentIndex)
        if (index >= 0) {
            playIndex(index, true)
            eval("window.galaxyOnExternal && window.galaxyOnExternal({kind:'index', index:$index})")
        }
    }

    fun release() {
        ticking = false
        main.removeCallbacksAndMessages(null)
        controls = null
        exo?.release()
        exo = null
    }

    private fun playUrl(url: String, autoplay: Boolean) {
        val index = songs.indexOfFirst { it.audioUrl == url }
        if (index < 0) return
        val player = exo
        if (player != null && index == currentIndex && currentUrl() == url &&
            player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED
        ) {
            if (autoplay) player.play()
            return
        }
        playIndex(index, autoplay)
    }

    private fun prepare(url: String): JSONObject {
        val player = exo
        if (url.isNotEmpty() && url == expectedUrl && player != null && currentUrl() == url && player.playbackState == Player.STATE_READY) {
            return readyPayload(player)
        }
        if (url.isNotEmpty() && url == expectedUrl) return JSONObject().put("ready", false)
        playUrl(url, false)
        return JSONObject().put("ready", false)
    }

    private fun playIndex(index: Int, autoplay: Boolean) {
        val player = exo ?: return
        val song = songs.getOrNull(index) ?: return
        currentIndex = index
        expectedUrl = song.audioUrl
        readyFor = ""
        player.setMediaItem(mediaItem(song))
        player.prepare()
        player.playWhenReady = autoplay
        if (engine.playbackRate() > 1) player.playbackParameters = PlaybackParameters(engine.playbackRate().toFloat())
        engine.startListen(song.messageId, 0.0)
    }

    private fun advance(): JSONObject {
        pendingJsEnd = false
        if (!engine.autoNavigation()) return JSONObject().put("index", -1)
        val next = nextIndex(currentIndex)
        if (next < 0) return JSONObject().put("index", -1)
        playIndex(next, true)
        return JSONObject().put("index", next)
    }

    private fun onTrackEnded() {
        if (handlingEnd) return
        handlingEnd = true
        try {
            val player = exo ?: return
            val listenId = engine.activeListenId()
            if (listenId != null) {
                engine.heartbeat(player.currentPosition / 1000.0, false, player.playbackParameters.speed.toDouble())
                engine.complete(listenId)
            }
            if (web == null) {
                advance()
                return
            }
            pendingJsEnd = true
            eval(
                """
                (function(){
                  window.galaxyHoldContinue = true;
                  if (window.galaxyAudio) window.galaxyAudio.onNative({type:"ended"});
                  var result = JSON.parse(GalaxyNative.command('{"op":"advance"}') || "{}");
                  if (result.index >= 0 && window.galaxyOnExternal) window.galaxyOnExternal({kind:"index", index: result.index});
                })();
                """.trimIndent(),
            )
            val view = web
            main.postDelayed({
                if (pendingJsEnd && exo?.playbackState == Player.STATE_ENDED) {
                    val result = advance()
                    val index = result.optInt("index", -1)
                    if (index >= 0 && view != null) {
                        eval("window.galaxyOnExternal && window.galaxyOnExternal({kind:'index', index:$index})")
                    }
                }
            }, 1600)
        } finally {
            handlingEnd = false
        }
    }

    private fun emitReady() {
        val player = exo ?: return
        val url = currentUrl()
        if (url.isEmpty() || url == readyFor || player.duration <= 0) return
        readyFor = url
        songs.getOrNull(currentIndex)?.let { engine.startListen(it.messageId, player.duration / 1000.0) }
        eval(
            "window.galaxyAudio && window.galaxyAudio.onNative(" +
                JSONObject()
                    .put("type", "loadedmetadata")
                    .put("duration", player.duration / 1000.0)
                    .put("position", player.currentPosition / 1000.0)
                    .put("playing", player.isPlaying)
                    .toString() + ")",
        )
    }

    private fun emit(type: String) {
        val player = exo
        val payload = JSONObject().put("type", type)
        if (player != null && player.duration > 0) {
            payload.put("duration", player.duration / 1000.0)
            payload.put("position", player.currentPosition / 1000.0)
        }
        eval("window.galaxyAudio && window.galaxyAudio.onNative($payload)")
    }

    private fun startTicker() {
        if (ticking) return
        ticking = true
        val task = object : Runnable {
            override fun run() {
                if (!ticking) return
                val player = exo
                if (player != null && currentIndex >= 0 && player.playbackState != Player.STATE_IDLE) {
                    engine.heartbeat(
                        player.currentPosition / 1000.0,
                        !player.isPlaying,
                        player.playbackParameters.speed.toDouble(),
                    )
                    if (player.duration > 0) {
                        eval(
                            "window.galaxyAudio && window.galaxyAudio.onNative(" + JSONObject()
                                .put("type", "time")
                                .put("position", player.currentPosition / 1000.0)
                                .put("duration", player.duration / 1000.0)
                                .put("paused", !player.isPlaying)
                                .put("ended", player.playbackState == Player.STATE_ENDED)
                                .toString() + ")",
                        )
                    }
                }
                main.postDelayed(this, 250)
            }
        }
        main.postDelayed(task, 250)
    }

    private fun notifyExpedition(index: Int) {
        val payload = JSONObject(snapshot()).put("index", index)
        eval("window.galaxyOnExternal && window.galaxyOnExternal($payload)")
    }

    private fun eval(script: String) {
        val view = web ?: return
        view.post { view.evaluateJavascript(script, null) }
    }

    private fun currentUrl(): String = exo?.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()

    private fun nearEnd(player: Player): Boolean {
        val duration = player.duration
        if (duration <= 0 || duration == C.TIME_UNSET) return false
        return duration - player.currentPosition <= 1_500
    }

    private fun nextIndex(after: Int): Int {
        val skip = engine.skipCompleted()
        for (index in after + 1 until songs.size) {
            if (!skip || !engine.isFullyListened(songs[index].messageId)) return index
        }
        return -1
    }

    private fun previousIndex(before: Int): Int {
        val skip = engine.skipCompleted()
        for (index in before - 1 downTo 0) {
            if (!skip || !engine.isFullyListened(songs[index].messageId)) return index
        }
        return -1
    }

    private fun channels(): List<MediaItem> {
        val showcase = backend.sources().optJSONObject("showcase") ?: return emptyList()
        val rows = showcase.optJSONArray("channels") ?: return emptyList()
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val id = row.optString("id")
                if (id.isBlank()) continue
                add(folder("#${row.optString("name", id)}", MediaIds.folder("channel", id)))
            }
        }
    }

    private fun collectionFolders(key: String, kind: String): List<MediaItem> {
        val payload = backend.fetchJson("/wlm/collections")
        val rows = payload.optJSONArray(key) ?: return emptyList()
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val id = row.optString("id")
                val title = row.optString("title").ifBlank { id }
                if (id.isBlank()) continue
                add(folder(title, MediaIds.folder(kind, id)))
            }
        }
    }

    private fun songItems(kind: String, collection: String, songDays: Int, songLimit: Int): List<MediaItem> {
        val loaded = backend.loadSongs(kind, collection, songDays, songLimit)
        return loaded.map { song ->
            mediaItem(song).buildUpon()
                .setMediaId(MediaIds.song(kind, collection, songDays, songLimit, song.messageId))
                .build()
        }
    }

    private fun folder(title: String, id: String): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .build()
        return MediaItem.Builder().setMediaId(id).setMediaMetadata(metadata).build()
    }

    private fun days(): Int = engine.expeditionDays()

    private fun limit(): Int =
        backend.sources().optJSONObject("showcase")?.optInt("default_song_limit", 30) ?: 30

    private fun readyPayload(player: Player) = JSONObject()
        .put("ready", true)
        .put("duration", player.duration / 1000.0)
        .put("position", player.currentPosition / 1000.0)
        .put("playing", player.isPlaying)

    private class ExpeditionControls(
        player: Player,
        private val director: PlaybackDirector,
    ) : ForwardingPlayer(player) {
        override fun seekToNext() = director.skip(1)
        override fun seekToNextMediaItem() = director.skip(1)
        override fun seekToPrevious() = director.skip(-1)
        override fun seekToPreviousMediaItem() = director.skip(-1)
        override fun seekTo(positionMs: Long) = director.seekTo(positionMs / 1000.0)
        override fun seekTo(mediaItemIndex: Int, positionMs: Long) = director.seekTo(positionMs / 1000.0)

        override fun isCommandAvailable(command: Int): Boolean {
            return command == COMMAND_SEEK_TO_NEXT ||
                command == COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
                command == COMMAND_SEEK_TO_PREVIOUS ||
                command == COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ||
                super.isCommandAvailable(command)
        }

        override fun getAvailableCommands(): Player.Commands {
            return super.getAvailableCommands().buildUpon()
                .add(COMMAND_SEEK_TO_NEXT)
                .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(COMMAND_SEEK_TO_PREVIOUS)
                .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()
        }
    }
}
