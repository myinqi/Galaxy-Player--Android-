package dev.betsy.galaxy.logic

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.min

class GalaxyLogicTest {
    @Test
    fun fullListenAwardsBonusCredits() {
        assertEquals(7, calculateListenCredits(180.0, 180.0, 0.0))
        assertTrue(calculateListenCredits(180.0, 180.0, 60.0) < 7)
    }

    @Test
    fun shortListenIsRejectedAndAFullOneIsRemembered() {
        var clock = 0L
        val timed = ExplorerEngine(tempFile(), clock = { clock })
        val early = timed.startListen("song-1", 180.0)
        clock += 12_000
        timed.heartbeat(12.0, false, 1.0)
        val rejected = timed.complete(early)
        assertFalse(rejected.ok)
        assertEquals("Not enough verified listening time.", rejected.message)

        val done = playFully(timed, { clock += it }, "song-2")
        assertTrue(done.ok)
        assertTrue(done.fullyListened)
        assertEquals(7, done.credits)
        assertTrue(timed.isFullyListened("song-2"))
    }

    @Test
    fun repeatInsideSevenDaysAwardsNothing() {
        var clock = 0L
        val timed = ExplorerEngine(tempFile(), clock = { clock })
        val first = playFully(timed, { clock += it }, "same")
        val second = playFully(timed, { clock += it }, "same")
        assertEquals(7, first.credits)
        assertEquals(0, second.credits)
    }

    @Test
    fun shopPurchaseEquipsAndPersists() {
        val file = tempFile()
        var clock = 0L
        val engine = ExplorerEngine(file, clock = { clock })
        repeat(3) { index ->
            playFully(engine, { clock += it }, "seed-$index", 1200.0)
        }
        assertTrue(engine.profileJson().getInt("credits") >= 120)
        assertTrue(engine.buy("raven").first)
        assertTrue(engine.equip("hull", "raven"))
        val reloaded = ExplorerEngine(file, clock = { clock })
        assertEquals("raven", reloaded.profileJson().getString("selected_hull"))
        assertTrue(reloaded.profileJson().getJSONArray("owned_upgrades").toString().contains("raven"))
    }

    @Test
    fun songMappingKeepsPlayerFields() {
        val showcase = GalaxySong.fromApi(
            JSONObject()
                .put("id", "123")
                .put("source", "showcase")
                .put("title", "Orbit.mp3")
                .put("artist", "Ada")
                .put("page_url", "https://suno.com/song/11111111-1111-1111-1111-111111111111")
                .put("audio_url", "https://betsy.example/galaxy/api/app/v1/audio/showcase/123")
                .put("artwork", "https://cdn1.suno.ai/image_large_11111111-1111-1111-1111-111111111111.jpeg")
                .put("reaction_count", 4),
        )!!
        assertEquals("discord", showcase.playerSource)
        assertEquals("suno:11111111-1111-1111-1111-111111111111", showcase.bookmarkKey())
        assertFalse(showcase.toPlayerJson(false).has("hook_video_url"))
        assertEquals("Ada", showcase.toPlayerJson(false).getString("discord_display_name"))

        val wlm = GalaxySong.fromApi(
            JSONObject()
                .put("id", "track_9")
                .put("source", "wlm")
                .put("title", "Ion")
                .put("artist", "Nova")
                .put("page_url", "https://welovemusic.ai/track/track_9")
                .put("audio_url", "https://betsy.example/galaxy/api/app/v1/audio/wlm/track_9")
                .put("artwork", "https://cdn.example/cover.jpg")
                .put("artist_id", "artist_2"),
        )!!
        assertEquals("wlm:track_9", wlm.bookmarkKey())
        assertEquals("artist_2", wlm.toPlayerJson(true).getString("wlm_artist_id"))
    }

    @Test
    fun apiAddressNormalizesAdminBase() {
        assertEquals(
            "http://192.168.1.20/galaxy/api/app/v1",
            ApiAddress.normalize("http://192.168.1.20/galaxy/api/app/v1/"),
        )
        assertEquals("http://betsy.example/galaxy/api/app/v1", ApiAddress.normalize("betsy.example"))
    }

    @Test
    fun launchTranslatesShowcaseIntoPlayerSongs() {
        val backend = PlayerBackend(ExplorerEngine(tempFile()), fetch = { path ->
            if (path.startsWith("/songs")) {
                JSONObject(
                    """
                    {"source":"showcase","songs":[{
                      "id":"55","source":"showcase","title":"Nova","artist":"Ada",
                      "page_url":"https://suno.com/song/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                      "audio_url":"https://betsy.example/galaxy/api/app/v1/audio/showcase/55",
                      "artwork":"https://cdn1.suno.ai/image_large_aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpeg",
                      "reaction_count":2
                    }]}
                    """.trimIndent(),
                )
            } else {
                JSONObject()
            }
        })
        val payload = JSONObject(
            backend.request(
                "POST",
                "/galaxy/api/expeditions",
                """{"source":"discord","collection_id":"99","channel_id":"99","days":7,"limit":30}""",
            ),
        )
        assertEquals(1, payload.getJSONArray("songs").length())
        assertEquals("discord", payload.getJSONArray("songs").getJSONObject(0).getString("source"))
        assertEquals("55", backend.expeditionSongs.single().messageId)
        assertFalse(payload.getJSONArray("songs").getJSONObject(0).has("hook_video_url"))
    }

    @Test
    fun bookmarkListKeepsTheSongAndItsPage() {
        val file = tempFile()
        val engine = ExplorerEngine(file)
        val song = GalaxySong.fromApi(
            JSONObject(
                """
                {"id":"55","source":"showcase","title":"Nova","artist":"Ada",
                 "page_url":"https://suno.com/song/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                 "audio_url":"https://betsy.example/audio","artwork":"https://cdn1.suno.ai/image_large_aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpeg"}
                """.trimIndent(),
            ),
        )!!
        assertTrue(engine.addBookmark(song))
        val saved = ExplorerEngine(file).bookmarkItems().getJSONObject(0)
        assertEquals("Nova", saved.getString("title"))
        assertEquals("Ada", saved.getString("artist"))
        assertEquals("https://suno.com/song/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", saved.getString("suno_url"))
        engine.clearBookmarks()
        assertEquals(0, ExplorerEngine(file).bookmarkItems().length())
        engine.clearBookmarks()
    }

    @Test
    fun bookmarkAgainFillsATitleThatWasOnlyAKey() {
        val file = tempFile()
        file.writeText("""{"bookmarks":["suno:aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"]}""")
        val engine = ExplorerEngine(file)
        val song = GalaxySong.fromApi(
            JSONObject(
                """
                {"id":"55","source":"showcase","title":"Nova","artist":"Ada",
                 "page_url":"https://suno.com/song/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                 "audio_url":"https://betsy.example/audio","artwork":"https://cdn1.suno.ai/image_large_aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpeg"}
                """.trimIndent(),
            ),
        )!!
        assertFalse(engine.addBookmark(song))
        val saved = ExplorerEngine(file).bookmarkItems().getJSONObject(0)
        assertEquals("Nova", saved.getString("title"))
        assertEquals("Ada", saved.getString("artist"))
        assertFalse(engine.addBookmark(song))
        assertEquals("Nova", engine.bookmarkItems().getJSONObject(0).getString("title"))
    }

    @Test
    fun knownSongFillsAKeyOnlyBookmarkWithoutAddingOthers() {
        val file = tempFile()
        file.writeText("""{"bookmarks":["suno:aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"]}""")
        val engine = ExplorerEngine(file)
        val known = GalaxySong.fromApi(
            JSONObject(
                """
                {"id":"55","source":"showcase","title":"Nova","artist":"Ada",
                 "page_url":"https://suno.com/song/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                 "audio_url":"https://betsy.example/audio","artwork":"https://cdn1.suno.ai/image_large_aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpeg"}
                """.trimIndent(),
            ),
        )!!
        val other = GalaxySong.fromApi(
            JSONObject(
                """
                {"id":"56","source":"showcase","title":"Other","artist":"Bea",
                 "page_url":"https://suno.com/song/bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                 "audio_url":"https://betsy.example/audio","artwork":"https://cdn1.suno.ai/image_large_bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb.jpeg"}
                """.trimIndent(),
            ),
        )!!
        engine.fillKnownBookmarks(listOf(known, other))
        val items = ExplorerEngine(file).bookmarkItems()
        assertEquals(1, items.length())
        assertEquals("Nova", items.getJSONObject(0).getString("title"))
        assertEquals("Ada", items.getJSONObject(0).getString("artist"))
    }

    @Test
    fun wlmPlaylistsPreselectTarjaWeeklyTop10() {
        val backend = PlayerBackend(ExplorerEngine(tempFile()), fetch = {
            JSONObject(
                """
                {"playlists":[
                  {"id":"other","title":"Other mix"},
                  {"id":"tarja-top","title":"Tarja Ravenveil Weekly Top 10"}
                ],"genres":[]}
                """.trimIndent(),
            )
        })
        val payload = JSONObject(backend.request("GET", "/galaxy/api/wlm/catalog", ""))
        assertEquals("tarja-top", payload.getString("preferred_playlist_slug"))
    }

    @Test
    fun songVideoIgnoresTheKaraokeFile() {
        val clip = JSONObject()
            .put("title", "Erase Me")
            .put("display_name", "AstraCoronet")
            .put("image_large_url", "https://cdn2.suno.ai/large.jpeg")
            .put("video_url", "https://cdn1.suno.ai/4ee99e68-38b0-4c7e-b7c9-34cddbbca05b.mp4")
            .put("video_cover_url", "https://cdn1.suno.ai/song-video.mp4")
        val media = SunoMedia.fromClip(clip)
        assertEquals("https://cdn1.suno.ai/song-video.mp4", media.getString("video_url"))
        assertEquals("https://cdn2.suno.ai/large.jpeg", media.getString("image_url"))

        val karaokeOnly = SunoMedia.fromClip(
            JSONObject().put("video_url", "https://cdn1.suno.ai/4ee99e68-38b0-4c7e-b7c9-34cddbbca05b.mp4"),
        )
        assertEquals("", karaokeOnly.getString("video_url"))

        val backend = PlayerBackend(
            ExplorerEngine(tempFile()),
            fetch = { JSONObject() },
            loadClip = { clip },
        )
        val payload = JSONObject(
            backend.request("GET", "/galaxy/api/media/4ee99e68-38b0-4c7e-b7c9-34cddbbca05b", ""),
        )
        assertEquals("https://cdn1.suno.ai/song-video.mp4", payload.getString("video_url"))
    }

    private fun tempFile() = File.createTempFile("explorer", ".json")

    private fun playFully(
        engine: ExplorerEngine,
        advance: (Long) -> Unit,
        id: String,
        duration: Double = 180.0,
    ): CompleteResult {
        val listen = engine.startListen(id, duration)
        var position = 0.0
        while (position < duration) {
            advance(12_000)
            position = min(duration, position + 12.0)
            engine.heartbeat(position, false, 1.0)
        }
        return engine.complete(listen)
    }
}
