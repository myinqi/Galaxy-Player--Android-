package dev.betsy.galaxy.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import dev.betsy.galaxy.GalaxyApp
import dev.betsy.galaxy.MainActivity
import dev.betsy.galaxy.logic.MediaIds
import java.util.concurrent.Executors

class GalaxyPlaybackService : MediaLibraryService() {
    private var librarySession: MediaLibraryService.MediaLibrarySession? = null
    private val io: ListeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun onCreate() {
        super.onCreate()
        val app = application as GalaxyApp
        val player = app.director.ensurePlayer(this)
        val activity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        librarySession = MediaLibraryService.MediaLibrarySession.Builder(this, app.director.controls(), LibraryCallback(app, io))
            .setId("galaxy-player")
            .setSessionActivity(activity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibraryService.MediaLibrarySession? = librarySession

    override fun onDestroy() {
        librarySession?.release()
        librarySession = null
        io.shutdownNow()
        super.onDestroy()
    }

    private class LibraryCallback(
        private val app: GalaxyApp,
        private val io: ListeningExecutorService,
    ) : MediaLibraryService.MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            if (!app.sessions.ready()) {
                val metadata = MediaMetadata.Builder()
                    .setTitle("Open Galaxy Player and save the app token")
                    .setIsBrowsable(false)
                    .setIsPlayable(false)
                    .build()
                val item = MediaItem.Builder().setMediaId(MediaIds.folder("offline")).setMediaMetadata(metadata).build()
                return Futures.immediateFuture(LibraryResult.ofItem(item, params))
            }
            val metadata = MediaMetadata.Builder()
                .setTitle("Galaxy Player")
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
            val root = MediaItem.Builder().setMediaId(MediaIds.folder("root")).setMediaMetadata(metadata).build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (!app.sessions.ready()) {
                return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
            }
            return io.submit<LibraryResult<ImmutableList<MediaItem>>> {
                try {
                    val items = app.director.children(parentId)
                    val from = (page.coerceAtLeast(0) * pageSize.coerceAtLeast(1))
                    val slice = if (from >= items.size) emptyList() else items.subList(from, minOf(items.size, from + pageSize.coerceAtLeast(1)))
                    LibraryResult.ofItemList(ImmutableList.copyOf(slice), params)
                } catch (error: Exception) {
                    LibraryResult.ofError(SessionError.ERROR_IO)
                }
            }
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val mediaId = mediaItems.getOrNull(startIndex.coerceAtLeast(0))?.mediaId ?: mediaItems.firstOrNull()?.mediaId
            if (mediaId.isNullOrBlank()) {
                return Futures.immediateFailedFuture(IllegalArgumentException("Choose a song."))
            }
            return io.submit<MediaSession.MediaItemsWithStartPosition> {
                val (songs, index) = app.director.resolve(mediaId)
                app.director.arm(index, songs, "auto")
                MediaSession.MediaItemsWithStartPosition(
                    listOf(app.director.mediaItem(songs[index])),
                    0,
                    startPositionMs.coerceAtLeast(0),
                )
            }
        }
    }
}
