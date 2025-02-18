package au.com.shiftyjelly.pocketcasts.repositories.playback.auto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.util.Log
import androidx.annotation.DrawableRes
import au.com.shiftyjelly.pocketcasts.localization.helper.RelativeDateFormatter
import au.com.shiftyjelly.pocketcasts.localization.helper.tryToLocaliseFilters
import au.com.shiftyjelly.pocketcasts.models.entity.Episode
import au.com.shiftyjelly.pocketcasts.models.entity.Folder
import au.com.shiftyjelly.pocketcasts.models.entity.Playable
import au.com.shiftyjelly.pocketcasts.models.entity.Playlist
import au.com.shiftyjelly.pocketcasts.models.entity.Podcast
import au.com.shiftyjelly.pocketcasts.models.type.EpisodePlayingStatus
import au.com.shiftyjelly.pocketcasts.repositories.extensions.autoDrawableId
import au.com.shiftyjelly.pocketcasts.repositories.extensions.automotiveDrawableId
import au.com.shiftyjelly.pocketcasts.repositories.extensions.getArtworkUrl
import au.com.shiftyjelly.pocketcasts.repositories.extensions.getSummaryText
import au.com.shiftyjelly.pocketcasts.repositories.images.PodcastImageLoader
import au.com.shiftyjelly.pocketcasts.repositories.playback.EXTRA_CONTENT_STYLE_GROUP_TITLE_HINT
import au.com.shiftyjelly.pocketcasts.repositories.playback.FOLDER_ROOT_PREFIX
import au.com.shiftyjelly.pocketcasts.utils.Util
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import java.io.File
import au.com.shiftyjelly.pocketcasts.images.R as IR
import au.com.shiftyjelly.pocketcasts.localization.R as LR

data class AutoMediaId(
    val playableId: String,
    val sourceId: String?
) {
    companion object {
        private const val DIVIDER = "#"
        fun fromMediaId(mediaId: String): AutoMediaId {
            val components = mediaId.split(DIVIDER)
            return if (components.size == 2) {
                AutoMediaId(components[1], components[0])
            } else {
                AutoMediaId(mediaId, null)
            }
        }
    }

    fun toMediaId(): String {
        return "$sourceId$DIVIDER$playableId"
    }
}

object AutoConverter {

    private const val THUMBNAIL_IMAGE_SIZE = 200
    private const val FULL_IMAGE_SIZE = 800

    // bundle extras giving Android Auto more info about an episode
    private const val EXTRA_IS_EXPLICIT = "android.media.IS_EXPLICIT"
    private const val EXTRA_IS_DOWNLOADED = "android.media.extra.DOWNLOAD_STATUS"

    // Playback Status, being one of the 3 constants below it
    private const val EXTRA_PLAY_COMPLETION_STATE = "android.media.extra.PLAYBACK_STATUS"
    private const val EXTRA_PLAY_STATE_VALUE_UNPLAYED = 0
    private const val EXTRA_PLAY_STATE_VALUE_PARTIAL = 1
    private const val EXTRA_PLAY_STATE_VALUE_COMPLETED = 2

    fun convertEpisodeToMediaItem(context: Context, episode: Playable, parentPodcast: Podcast, groupTrailers: Boolean = false, sourceId: String = parentPodcast.uuid): MediaBrowserCompat.MediaItem {
        val localUri = getBitmapUriForPodcast(parentPodcast, context)

        val extrasForEpisode = extrasForEpisode(episode)
        if (groupTrailers) {
            val groupTitle = if (episode is Episode && episode.episodeType is Episode.EpisodeType.Trailer) LR.string.episode_trailer else LR.string.episodes
            extrasForEpisode.putString(EXTRA_CONTENT_STYLE_GROUP_TITLE_HINT, context.resources.getString(groupTitle))
        }
        val mediaId = AutoMediaId(episode.uuid, sourceId).toMediaId()
        val episodeDesc = MediaDescriptionCompat.Builder()
            .setDescription(episode.episodeDescription)
            .setTitle(episode.title)
            .setSubtitle(episode.getSummaryText(dateFormatter = RelativeDateFormatter(context), tintColor = Color.WHITE, showDuration = true, context = context).toString())
            .setMediaId(mediaId)
            .setExtras(extrasForEpisode)
            .setIconUri(localUri)
            .build()

        return MediaBrowserCompat.MediaItem(episodeDesc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }

    fun convertPodcastToMediaItem(podcast: Podcast, context: Context): MediaBrowserCompat.MediaItem? {
        return try {
            val localUri = getBitmapUriForPodcast(podcast, context)

            val podcastDesc = MediaDescriptionCompat.Builder()
                .setTitle(podcast.title)
                .setMediaId(podcast.uuid)
                .setIconUri(localUri)
                .build()

            MediaBrowserCompat.MediaItem(podcastDesc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
        } catch (e: Exception) {
            null
        }
    }

    fun convertFolderToMediaItem(context: Context, folder: Folder): MediaBrowserCompat.MediaItem? {
        return try {
            val localUri = getBitmapUriForFolder(context, folder)

            val podcastDesc = MediaDescriptionCompat.Builder()
                .setTitle(folder.name)
                .setMediaId(FOLDER_ROOT_PREFIX + folder.uuid)
                .setIconUri(localUri)
                .build()

            MediaBrowserCompat.MediaItem(podcastDesc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
        } catch (e: Exception) {
            null
        }
    }

    fun convertPlaylistToMediaItem(context: Context, playlist: Playlist): MediaBrowserCompat.MediaItem {
        val mediaDescription = MediaDescriptionCompat.Builder()
            .setTitle(playlist.title.tryToLocaliseFilters(context.resources))
            .setMediaId(playlist.uuid)
            .setIconUri(getPlaylistBitmapUri(playlist, context))
            .build()

        return MediaBrowserCompat.MediaItem(mediaDescription, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
    }

    fun getBitmapUriForPodcast(podcast: Podcast?, context: Context): Uri? {
        if (podcast == null) return null

        val podcastArtUri = Uri.parse(podcast.getArtworkUrl(480))
        return getArtworkUriForContentProvider(podcastArtUri, context)
    }

    private fun getBitmapUriForFolder(context: Context, folder: Folder?): Uri? {
        if (folder == null) return null

        return getBitmapUri(drawable = folder.automotiveDrawableId, context = context)
    }

    val autoImageLoaderListener = object : RequestListener<File> {
        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<File>?, isFirstResource: Boolean): Boolean {
            Log.e("AutoConverter", "Could not load image in automotive $e")
            return false
        }

        override fun onResourceReady(resource: File?, model: Any?, target: Target<File>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
            return true
        }
    }

    /**
     * This creates a Uri that will call the AlbumArtContentProvider to download and cache the artwork
     */
    fun getArtworkUriForContentProvider(podcastArtUri: Uri?, context: Context): Uri? {
        return podcastArtUri?.asAlbumArtContentUri(context)
    }

    fun getBitmapForPodcast(podcast: Podcast?, useThumbnail: Boolean, context: Context): Bitmap? {
        if (podcast == null) {
            return null
        }

        val size = if (useThumbnail) THUMBNAIL_IMAGE_SIZE else FULL_IMAGE_SIZE
        val imageLoader = PodcastImageLoader(context = context, isDarkTheme = true, transformations = emptyList()).smallPlaceholder()
        return imageLoader.getBitmap(podcast, size)
    }

    fun getPodcastsBitmapUri(context: Context): Uri {
        return getBitmapUri(drawable = IR.drawable.auto_tab_podcasts, context = context)
    }

    fun getPlaylistBitmapUri(playlist: Playlist?, context: Context): Uri {
        val drawableId = if (Util.isAutomotive(context)) {
            // the Automotive UI displays the icon in a list that requires more padding around the icon
            playlist?.automotiveDrawableId ?: IR.drawable.automotive_filter_play
        } else {
            playlist?.autoDrawableId ?: IR.drawable.auto_filter_play
        }
        return getBitmapUri(drawableId, context)
    }

    fun getDownloadsBitmapUri(context: Context): Uri {
        return getBitmapUri(drawable = IR.drawable.auto_filter_downloaded, context = context)
    }

    fun getFilesBitmapUri(context: Context): Uri {
        return getBitmapUri(drawable = IR.drawable.auto_files, context = context)
    }

    /**
     * Convert a drawable into a Uri
     * Use the drawable id so Proguard doesn't remove the asset in the production build.
     */
    fun getBitmapUri(@DrawableRes drawable: Int, context: Context): Uri {
        val drawableName = context.resources.getResourceEntryName(drawable)
        return Uri.parse("android.resource://" + context.packageName + "/drawable/" + drawableName)
    }

    private fun extrasForEpisode(episode: Playable): Bundle {
        val extras = Bundle(2)
        extras.putLong(EXTRA_IS_DOWNLOADED, (if (episode.isDownloaded) 1 else 0).toLong())

        val playbackStatus = episode.playingStatus
        var androidAutoPlaybackStatus = EXTRA_PLAY_STATE_VALUE_UNPLAYED
        if (playbackStatus == EpisodePlayingStatus.IN_PROGRESS) {
            androidAutoPlaybackStatus = EXTRA_PLAY_STATE_VALUE_PARTIAL
        } else if (playbackStatus == EpisodePlayingStatus.COMPLETED) {
            androidAutoPlaybackStatus = EXTRA_PLAY_STATE_VALUE_COMPLETED
        }

        extras.putInt(EXTRA_PLAY_COMPLETION_STATE, androidAutoPlaybackStatus)

        return extras
    }
}
