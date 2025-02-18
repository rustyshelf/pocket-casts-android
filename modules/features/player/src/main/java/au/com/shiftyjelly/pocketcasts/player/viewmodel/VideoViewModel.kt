package au.com.shiftyjelly.pocketcasts.player.viewmodel

import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.LiveDataReactiveStreams
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import au.com.shiftyjelly.pocketcasts.repositories.playback.PlaybackManager
import au.com.shiftyjelly.pocketcasts.repositories.playback.PlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.BackpressureStrategy
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class VideoViewModel @Inject constructor(
    private val playbackManager: PlaybackManager
) : ViewModel() {

    val playbackState: LiveData<PlaybackState> = LiveDataReactiveStreams.fromPublisher(playbackManager.playbackStateRelay.toFlowable(BackpressureStrategy.LATEST))

    private var hideControlsTimer: Disposable? = null
    private var lastTimeHidingControls = 0L

    private var controlsVisibleMutable = MutableLiveData(true)
    val controlsVisible: LiveData<Boolean> get() = controlsVisibleMutable

    fun play() {
        playbackManager.playQueue()
        startHideControlsTimer()
    }

    fun pause() {
        playbackManager.pause()
    }

    fun skipBackward() {
        playbackManager.skipBackward()
        startHideControlsTimer()
    }

    fun skipForward() {
        playbackManager.skipForward()
        startHideControlsTimer()
    }

    fun playPause() {
        if (playbackManager.isPlaying()) {
            pause()
        } else {
            play()
        }
    }

    fun isPlaying(): Boolean {
        return playbackManager.isPlaying()
    }

    fun seekStarted() {
        stopHideControlsTimer()
    }

    fun seekToMs(seekTimeMs: Int) {
        playbackManager.seekToTimeMs(seekTimeMs)
        startHideControlsTimer()
    }

    fun showControls() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q && System.currentTimeMillis() - lastTimeHidingControls < 200L) {
            // Avoids an issue with API 29 and below where extra calls to showControl get triggered after hiding the controls
            return
        }
        controlsVisibleMutable.value = true
        startHideControlsTimer()
    }

    fun hideControls() {
        controlsVisibleMutable.value = false
        lastTimeHidingControls = System.currentTimeMillis()
    }

    fun toggleControls() {
        val showingControls = controlsVisible.value ?: true
        if (showingControls) {
            hideControls()
        } else {
            showControls()
        }
    }

    private fun startHideControlsTimer() {
        stopHideControlsTimer()
        hideControlsTimer = Completable.timer(3, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
            .subscribe {
                if (playbackManager.isPlaying()) {
                    this.hideControls()
                }
            }
    }

    private fun stopHideControlsTimer() {
        hideControlsTimer?.dispose()
    }

    override fun onCleared() {
        super.onCleared()
        stopHideControlsTimer()
    }
}
