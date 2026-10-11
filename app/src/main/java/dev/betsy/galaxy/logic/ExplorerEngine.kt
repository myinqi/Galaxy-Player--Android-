package dev.betsy.galaxy.logic

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CompleteResult(
    val ok: Boolean,
    val credits: Int,
    val message: String,
    val fullyListened: Boolean,
    val profile: JSONObject,
)

/**
 * Device-side explorer. The app API has no Discord profile, so credits, the shipyard,
 * bookmarks, ratings and heard songs stay on the phone and follow Betsy's rules.
 */
class ExplorerEngine(
    private val file: File,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val rules: ListenRules = ListenRules(),
) {
    private var credits = 0
    private val owned = mutableSetOf("scout", "ion_blue", "pulse", "still")
    private var hull = "scout"
    private var trail = "ion_blue"
    private var scanner = "pulse"
    private var engine = ""
    private var worlds = "still"
    private var autoNavigation = true
    private var skipCompleted = true
    private var shopCollapsed = false
    private var volume = 80
    private var speedMode = false
    private var expeditionDays = 7
    private val bookmarks = linkedMapOf<String, SavedBookmark>()
    private val ratings = mutableMapOf<String, Int>()
    private val completions = mutableListOf<Completion>()
    private val ledger = mutableListOf<LedgerEntry>()
    private var listenSerial = 1L
    private var active: ActiveListen? = null
    private var pendingSeek = 0.0

    init {
        load()
    }

    @Synchronized
    fun profileJson(): JSONObject = JSONObject()
        .put("credits", credits)
        .put("selected_hull", hull)
        .put("selected_trail", trail)
        .put("selected_scanner", scanner)
        .put("selected_engine", engine)
        .put("selected_worlds", worlds)
        .put("owned_upgrades", JSONArray(owned.sorted()))
        .put("auto_navigation", if (autoNavigation) 1 else 0)
        .put("skip_completed", if (skipCompleted) 1 else 0)
        .put("shop_collapsed", if (shopCollapsed) 1 else 0)
        .put("volume_percent", volume)
        .put("speed_mode", if (speedMode) 1 else 0)
        .put("expedition_days", expeditionDays)

    @Synchronized fun autoNavigation(): Boolean = autoNavigation
    @Synchronized fun skipCompleted(): Boolean = skipCompleted
    @Synchronized fun expeditionDays(): Int = expeditionDays
    @Synchronized fun volumePercent(): Int = volume

    @Synchronized
    fun playbackRate(): Double {
        if (!speedMode) return 1.0
        val selected = upgradeById(engine) ?: return 1.0
        if (selected.category != "engine" || selected.id !in owned) return 1.0
        return selected.playbackRate.coerceIn(1.05, 2.0)
    }

    @Synchronized
    fun upgradesJson(): JSONArray {
        val array = JSONArray()
        SHIPYARD.sortedWith(compareBy({ it.category }, { it.sortOrder }, { it.name })).forEach { array.put(it.toJson()) }
        return array
    }

    @Synchronized
    fun savePreferences(
        auto: Boolean,
        days: Int,
        skip: Boolean,
        collapsed: Boolean,
        volumePercent: Int,
        speed: Boolean,
    ) {
        require(days in ALLOWED_DAYS) { "Invalid expedition range." }
        autoNavigation = auto
        expeditionDays = days
        skipCompleted = skip
        shopCollapsed = collapsed
        volume = volumePercent.coerceIn(0, 100)
        speedMode = speed && playbackRateUnlocked()
        save()
    }

    @Synchronized
    fun buy(upgradeId: String): Pair<Boolean, String> {
        val item = upgradeById(upgradeId) ?: return false to "Upgrade not found."
        if (item.id in owned || item.price == 0) return false to "Upgrade already owned."
        if (credits < item.price) return false to "Not enough credits."
        credits -= item.price
        owned += item.id
        ledger += LedgerEntry(-item.price, "purchase", clock(), item.id)
        save()
        return true to "Upgrade purchased."
    }

    @Synchronized
    fun equip(category: String, upgradeId: String): Boolean {
        val item = upgradeById(upgradeId) ?: return false
        if (item.category != category || item.id !in owned) return false
        when (category) {
            "hull" -> hull = item.id
            "trail" -> trail = item.id
            "scanner" -> scanner = item.id
            "engine" -> engine = item.id
            "worlds" -> worlds = item.id
            else -> return false
        }
        if (category == "engine" && speedMode && !playbackRateUnlocked()) speedMode = false
        save()
        return true
    }

    @Synchronized
    fun bookmarkKeys(): JSONArray {
        val array = JSONArray()
        bookmarks.keys.forEach { array.put(it) }
        return array
    }

    @Synchronized
    fun bookmarkItems(): JSONArray {
        val array = JSONArray()
        bookmarks.values.reversed().forEach { item ->
            array.put(
                JSONObject()
                    .put("key", item.key)
                    .put("title", item.title)
                    .put("artist", item.artist)
                    .put("source", item.source)
                    .put("suno_url", item.sunoUrl)
                    .put("wlm_url", item.wlmUrl),
            )
        }
        return array
    }

    @Synchronized
    fun addBookmark(song: GalaxySong): Boolean {
        val key = song.bookmarkKey()
        if (key.isEmpty()) return false
        val incoming = SavedBookmark(
            key = key,
            title = song.title.ifBlank { "Saved song" },
            artist = song.artist,
            source = song.playerSource,
            sunoUrl = song.bookmarkSunoUrl(),
            wlmUrl = song.bookmarkWlmUrl(),
        )
        val existing = bookmarks[key]
        if (existing != null) {
            val merged = existing.copy(
                title = if (existing.title.isBlank() || existing.title == "Saved song") incoming.title else existing.title,
                artist = existing.artist.ifBlank { incoming.artist },
                source = existing.source.ifBlank { incoming.source },
                sunoUrl = existing.sunoUrl.ifBlank { incoming.sunoUrl },
                wlmUrl = existing.wlmUrl.ifBlank { incoming.wlmUrl },
            )
            if (merged != existing) {
                bookmarks[key] = merged
                save()
            }
            return false
        }
        if (bookmarks.size >= MAX_BOOKMARKS) bookmarks.remove(bookmarks.keys.first())
        bookmarks[key] = incoming
        save()
        return true
    }

    @Synchronized
    fun clearBookmarks() {
        if (bookmarks.isEmpty()) return
        bookmarks.clear()
        save()
    }

    /** Fills title and artist for bookmarks that were saved as a key only. */
    @Synchronized
    fun fillKnownBookmarks(songs: Iterable<GalaxySong>) {
        var changed = false
        for (song in songs) {
            val key = song.bookmarkKey()
            val existing = bookmarks[key] ?: continue
            val title = song.title.ifBlank { existing.title }
            val artist = song.artist.ifBlank { existing.artist }
            val merged = existing.copy(
                title = if (existing.title.isBlank() || existing.title == "Saved song") title else existing.title,
                artist = existing.artist.ifBlank { artist },
                source = existing.source.ifBlank { song.playerSource },
                sunoUrl = existing.sunoUrl.ifBlank { song.bookmarkSunoUrl() },
                wlmUrl = existing.wlmUrl.ifBlank { song.bookmarkWlmUrl() },
            )
            if (merged != existing) {
                bookmarks[key] = merged
                changed = true
            }
        }
        if (changed) save()
    }

    @Synchronized
    fun rating(trackId: String): Int = ratings[trackId] ?: 0

    @Synchronized
    fun setRating(trackId: String, rating: Int) {
        ratings[trackId] = rating.coerceIn(1, 5)
        save()
    }

    @Synchronized
    fun isFullyListened(messageId: String): Boolean =
        completions.any { it.messageId == messageId && it.fullyListened }

    @Synchronized
    fun startListen(messageId: String, duration: Double): Long {
        val current = active
        if (current != null && current.messageId == messageId && current.completedAt == null) {
            if (duration > 0) current.duration = duration.coerceIn(5.0, 1200.0)
            return current.id
        }
        val id = listenSerial++
        active = ActiveListen(
            id = id,
            messageId = messageId,
            duration = if (duration > 0) duration.coerceIn(5.0, 1200.0) else 0.0,
            lastHeartbeatAt = clock(),
        )
        pendingSeek = 0.0
        return id
    }

    @Synchronized
    fun noteSeek(forwardSeconds: Double) {
        if (forwardSeconds > 0) pendingSeek += forwardSeconds
    }

    @Synchronized
    fun heartbeat(position: Double, paused: Boolean, playbackRate: Double) {
        val listen = active ?: return
        if (listen.completedAt != null) return
        val now = clock()
        val wall = ((now - listen.lastHeartbeatAt) / 1000.0).coerceIn(0.0, 30.0)
        val rateCap = playbackRate().coerceAtLeast(1.0)
        val rate = playbackRate.coerceIn(1.0, minOf(rateCap, 2.0))
        val playable = wall * rate
        val safePosition = position.coerceAtLeast(0.0).let { value ->
            if (listen.duration > 0) minOf(value, listen.duration + 5) else value
        }
        val delta = (safePosition - listen.lastPosition).coerceAtLeast(0.0)
        val reported = pendingSeek.coerceIn(0.0, delta)
        pendingSeek = 0.0
        val seeked = reported > 0.0
        val seekAdd = when {
            seeked && reported > 0 -> reported
            seeked -> delta
            delta > playable + 3 -> (delta - playable).coerceAtLeast(0.0)
            else -> 0.0
        }
        val eligibleAdd = if (paused) 0.0 else minOf(playable, (delta - seekAdd).coerceAtLeast(0.0))
        val cap = if (listen.duration > 0) listen.duration else Double.MAX_VALUE
        listen.eligible = minOf(cap, listen.eligible + eligibleAdd)
        listen.maxPosition = maxOf(listen.maxPosition, safePosition)
        listen.lastPosition = safePosition
        listen.seeked = minOf(cap, listen.seeked + seekAdd)
        listen.lastHeartbeatAt = now
    }

    @Synchronized
    fun complete(listenId: Long): CompleteResult {
        val listen = active
        if (listen == null || listen.id != listenId) {
            return CompleteResult(false, 0, "Listen session not found.", false, profileJson())
        }
        if (listen.completedAt != null) {
            return CompleteResult(true, listen.creditsAwarded, listen.message, listen.fullyListened, profileJson())
        }
        val duration = maxOf(1.0, listen.duration)
        val required = maxOf(rules.minListenSeconds.toDouble(), duration * rules.minListenPercent / 100.0)
        if (listen.eligible < minOf(duration, required)) {
            return CompleteResult(false, 0, "Not enough verified listening time.", false, profileJson())
        }
        val now = clock()
        val dayStart = (now / 86_400_000L) * 86_400_000L
        val earnedToday = ledger.filter { it.reason == "listen" && it.at >= dayStart }.sumOf { it.amount.coerceAtLeast(0) }
        val repeatCutoff = now - rules.repeatCooldownDays * 86_400_000L
        val repeated = completions.any { it.messageId == listen.messageId && it.at >= repeatCutoff }
        val raw = calculateListenCredits(
            duration,
            listen.eligible,
            listen.seeked,
            rules.creditsPerMinute,
            rules.fullListenBonusPercent,
            rules.forwardSeekPenaltyPercent,
        )
        val award = if (repeated) 0 else raw.coerceAtMost((rules.dailyCreditCap - earnedToday).coerceAtLeast(0))
        val full = listen.eligible >= duration * 0.98 && listen.seeked <= 0.5
        listen.completedAt = now
        listen.creditsAwarded = award
        listen.fullyListened = full
        listen.message = "Expedition credits awarded."
        completions += Completion(listen.messageId, now, full)
        if (award > 0) {
            credits += award
            ledger += LedgerEntry(award, "listen", now, listenId.toString())
        }
        save()
        return CompleteResult(true, award, listen.message, full, profileJson())
    }

    @Synchronized
    fun activeListenId(): Long? = active?.takeIf { it.completedAt == null }?.id

    private fun playbackRateUnlocked(): Boolean {
        val selected = upgradeById(engine) ?: return false
        return selected.category == "engine" && selected.id in owned && selected.playbackRate > 1.0
    }

    private fun load() {
        if (!file.exists()) return
        val root = try {
            JSONObject(file.readText())
        } catch (error: Exception) {
            return
        }
        credits = root.optInt("credits")
        hull = root.optString("hull", hull)
        trail = root.optString("trail", trail)
        scanner = root.optString("scanner", scanner)
        engine = root.optString("engine", engine)
        worlds = root.optString("worlds", worlds)
        autoNavigation = root.optBoolean("auto", autoNavigation)
        skipCompleted = root.optBoolean("skip", skipCompleted)
        shopCollapsed = root.optBoolean("collapsed", shopCollapsed)
        volume = root.optInt("volume", volume).coerceIn(0, 100)
        speedMode = root.optBoolean("speed", speedMode)
        expeditionDays = root.optInt("days", expeditionDays).takeIf { it in ALLOWED_DAYS } ?: expeditionDays
        owned.clear()
        owned += listOf("scout", "ion_blue", "pulse", "still")
        val ownedArray = root.optJSONArray("owned") ?: JSONArray()
        for (index in 0 until ownedArray.length()) {
            val id = ownedArray.optString(index)
            if (upgradeById(id) != null) owned += id
        }
        bookmarks.clear()
        val bookmarkArray = root.optJSONArray("bookmarks") ?: JSONArray()
        for (index in 0 until bookmarkArray.length()) {
            val row = bookmarkArray.optJSONObject(index)
            if (row == null) {
                val key = bookmarkArray.optString(index)
                if (key.isNotBlank()) bookmarks[key] = savedBookmark(key, "Saved song", "", "", "", "")
                continue
            }
            val key = row.optString("key")
            if (key.isBlank()) continue
            bookmarks[key] = savedBookmark(
                key,
                row.optString("title").ifBlank { "Saved song" },
                row.optString("artist"),
                row.optString("source"),
                row.optString("suno_url"),
                row.optString("wlm_url"),
            )
        }
        ratings.clear()
        val ratingObject = root.optJSONObject("ratings") ?: JSONObject()
        ratingObject.keys().forEach { key -> ratings[key] = ratingObject.optInt(key) }
        completions.clear()
        val heard = root.optJSONArray("completions") ?: JSONArray()
        for (index in 0 until heard.length()) {
            val row = heard.optJSONObject(index) ?: continue
            completions += Completion(row.optString("id"), row.optLong("at"), row.optBoolean("full"))
        }
        ledger.clear()
        val ledgerArray = root.optJSONArray("ledger") ?: JSONArray()
        for (index in 0 until ledgerArray.length()) {
            val row = ledgerArray.optJSONObject(index) ?: continue
            ledger += LedgerEntry(row.optInt("amount"), row.optString("reason"), row.optLong("at"), row.optString("ref"))
        }
    }

    private fun save() {
        val ownedArray = JSONArray()
        owned.forEach { ownedArray.put(it) }
        val bookmarkArray = JSONArray()
        bookmarks.values.forEach { item ->
            bookmarkArray.put(
                JSONObject()
                    .put("key", item.key)
                    .put("title", item.title)
                    .put("artist", item.artist)
                    .put("source", item.source)
                    .put("suno_url", item.sunoUrl)
                    .put("wlm_url", item.wlmUrl),
            )
        }
        val ratingObject = JSONObject()
        ratings.forEach { (key, value) -> ratingObject.put(key, value) }
        val heard = JSONArray()
        completions.takeLast(2000).forEach {
            heard.put(JSONObject().put("id", it.messageId).put("at", it.at).put("full", it.fullyListened))
        }
        val ledgerArray = JSONArray()
        ledger.takeLast(2000).forEach {
            ledgerArray.put(JSONObject().put("amount", it.amount).put("reason", it.reason).put("at", it.at).put("ref", it.ref))
        }
        val root = JSONObject()
            .put("credits", credits)
            .put("hull", hull)
            .put("trail", trail)
            .put("scanner", scanner)
            .put("engine", engine)
            .put("worlds", worlds)
            .put("auto", autoNavigation)
            .put("skip", skipCompleted)
            .put("collapsed", shopCollapsed)
            .put("volume", volume)
            .put("speed", speedMode)
            .put("days", expeditionDays)
            .put("owned", ownedArray)
            .put("bookmarks", bookmarkArray)
            .put("ratings", ratingObject)
            .put("completions", heard)
            .put("ledger", ledgerArray)
        file.parentFile?.mkdirs()
        file.writeText(root.toString())
    }

    private data class SavedBookmark(
        val key: String,
        val title: String,
        val artist: String,
        val source: String,
        val sunoUrl: String,
        val wlmUrl: String,
    )

    private fun savedBookmark(
        key: String,
        title: String,
        artist: String,
        source: String,
        sunoUrl: String,
        wlmUrl: String,
    ): SavedBookmark {
        val suno = sunoUrl.ifBlank {
            val id = key.removePrefix("suno:")
            if (key.startsWith("suno:") && id.matches(Regex("^[0-9a-fA-F-]{36}$"))) "https://suno.com/song/${id.lowercase()}" else ""
        }
        val wlm = wlmUrl.ifBlank {
            if (key.startsWith("wlm:")) "https://welovemusic.ai/track/${key.removePrefix("wlm:")}" else ""
        }
        return SavedBookmark(key, title, artist, source, suno, wlm)
    }

    private data class Completion(val messageId: String, val at: Long, val fullyListened: Boolean)
    private data class LedgerEntry(val amount: Int, val reason: String, val at: Long, val ref: String)
    private class ActiveListen(
        val id: Long,
        val messageId: String,
        var duration: Double,
        var eligible: Double = 0.0,
        var maxPosition: Double = 0.0,
        var lastPosition: Double = 0.0,
        var seeked: Double = 0.0,
        var lastHeartbeatAt: Long,
        var completedAt: Long? = null,
        var creditsAwarded: Int = 0,
        var fullyListened: Boolean = false,
        var message: String = "",
    )

    companion object {
        private val ALLOWED_DAYS = setOf(1, 3, 7, 14, 30)
        private const val MAX_BOOKMARKS = 200
    }
}
