package dev.betsy.galaxy

import android.app.Application
import dev.betsy.galaxy.data.SessionStore
import dev.betsy.galaxy.logic.ExplorerEngine
import dev.betsy.galaxy.logic.PlayerBackend
import dev.betsy.galaxy.net.AppApi
import dev.betsy.galaxy.net.SunoClip
import dev.betsy.galaxy.player.PlaybackDirector
import java.io.File

class GalaxyApp : Application() {
    lateinit var sessions: SessionStore
        private set
    lateinit var engine: ExplorerEngine
        private set
    lateinit var api: AppApi
        private set
    lateinit var backend: PlayerBackend
        private set
    lateinit var director: PlaybackDirector
        private set

    override fun onCreate() {
        super.onCreate()
        sessions = SessionStore(File(filesDir, "session.json"))
        engine = ExplorerEngine(File(filesDir, "explorer.json"))
        api = AppApi { sessions.current }
        backend = PlayerBackend(
            engine,
            fetch = { path -> api.get(path) },
            loadClip = SunoClip::load,
        ) { songs, token ->
            director.adopt(songs, token)
        }
        director = PlaybackDirector(engine, backend, api)
    }
}
