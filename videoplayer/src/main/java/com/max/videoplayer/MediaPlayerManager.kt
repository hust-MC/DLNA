package com.max.videoplayer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * 媒体播放器管理类
 * 使用ExoPlayer替代MediaPlayer，提供更好的M3U8支持
 * @author Max
 */
@OptIn(UnstableApi::class)
class MediaPlayerManager(private val context: Context) {
    companion object {
        private const val TAG = "MediaPlayerManager"
        private const val PROGRESS_UPDATE_INTERVAL_MS = 1000L
    }

    private var exoPlayer: ExoPlayer? = null
    private var stateListener: MediaStateListener? = null
    private var currentState = PlaybackState.STOPPED
    private var currentUri: String = ""
    private var duration: Int = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var surface: Surface? = null
    private var shouldAutoPlay: Boolean = false

    // 使用 Handler 替代 Timer 来更新播放进度，避免创建新线程
    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            exoPlayer?.let { player ->
                if (player.isPlaying) {
                    val position = player.currentPosition.toInt()
                    stateListener?.onProgressUpdate(position)
                }
            }
            mainHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS)
        }
    }

    enum class PlaybackState {
        STOPPED, PLAYING, PAUSED, ERROR
    }

    interface MediaStateListener {
        fun onPrepared(durationMs: Int)
        fun onProgressUpdate(positionMs: Int)
        fun onPlaybackStateChanged(state: PlaybackState)
        fun onPlaybackCompleted()
        fun onError(errorMsg: String)
        fun onBufferingUpdate(percent: Int)
    }

    fun setStateListener(listener: MediaStateListener) {
        stateListener = listener
    }

    fun setSurface(surface: Surface?) {
        this.surface = surface
        runOnMainThread {
            exoPlayer?.setVideoSurface(surface)
        }
    }

    /**
     * 设置媒体URI并自动播放
     */
    fun setMediaURIAndPlay(uri: String) {
        shouldAutoPlay = true
        setMediaURI(uri)
    }

    private fun setMediaURI(uri: String) {
        try {
            currentUri = uri
            Log.d(TAG, "设置媒体URI: $uri")

            // 在主线程上释放播放器和设置新的媒体源
            runOnMainThread {
                try {
                    release()
                    setupExoPlayer(uri)
                } catch (e: Exception) {
                    handleError(e)
                }
            }
        } catch (e: Exception) {
            handleError(e)
        }
    }

    private fun setupExoPlayer(uri: String) {
        try {
            // 确保该方法在主线程中调用
            if (Looper.myLooper() != Looper.getMainLooper()) {
                Log.w(TAG, "setupExoPlayer在非主线程调用，转到主线程")
                runOnMainThread { setupExoPlayer(uri) }
                return
            }

            // Media3推荐的做法：使用DefaultMediaSourceFactory自动处理媒体源
            // 并将 MediaCacheFactory 中创建好的带缓存的 DataSource.Factory 设置进去
            val mediaSourceFactory = DefaultMediaSourceFactory(context)
                .setDataSourceFactory(MediaCacheFactory.getCacheFactory(context))

            // 创建ExoPlayer实例，并传入 MediaSourceFactory
            exoPlayer = ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()

            // 设置播放状态监听
            exoPlayer?.addListener(object : Player.Listener {
                // Player.Listener 的回调默认在主线程执行，无需额外切换
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            Log.d(TAG, "ExoPlayer状态: 就绪")

                            // 获取并报告媒体时长
                            val durationMs = exoPlayer?.duration?.toInt() ?: 0
                            if (durationMs > 0) {
                                duration = durationMs
                                stateListener?.onPrepared(durationMs)
                            }

                            // 如果设置了自动播放
                            if (shouldAutoPlay) {
                                exoPlayer?.play()
                                // isPlayingChanged回调会处理播放状态和进度条
                                shouldAutoPlay = false
                            }
                        }

                        Player.STATE_ENDED -> {
                            Log.d(TAG, "ExoPlayer状态: 播放结束")
                            currentState = PlaybackState.STOPPED
                            stateListener?.onPlaybackStateChanged(currentState)
                            stateListener?.onPlaybackCompleted()
                            stopProgressTimer()
                        }

                        Player.STATE_BUFFERING -> {
                            Log.d(TAG, "ExoPlayer状态: 缓冲中")
                            stateListener?.onBufferingUpdate(50) // 缓冲中
                        }

                        Player.STATE_IDLE -> {
                            Log.d(TAG, "ExoPlayer状态: 空闲")
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "ExoPlayer错误: ${error.message}")
                    handleError(error)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        currentState = PlaybackState.PLAYING
                        stateListener?.onPlaybackStateChanged(currentState)
                        startProgressTimer()
                    } else {
                        // 播放器不在播放状态，可能是暂停或已结束
                        // STATE_ENDED会处理停止状态，这里只处理暂停
                        if (exoPlayer?.playbackState == Player.STATE_READY) {
                            currentState = PlaybackState.PAUSED
                            stateListener?.onPlaybackStateChanged(currentState)
                            stopProgressTimer()
                        }
                    }
                }
            })

            // 设置视频输出surface
            if (surface != null) {
                exoPlayer?.setVideoSurface(surface)
            }

            // 设置媒体项，ExoPlayer会使用MediaSourceFactory自动创建合适的MediaSource
            val mediaItem = MediaItem.fromUri(uri)
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.prepare()
        } catch (e: Exception) {
            handleError(e)
        }
    }

    private fun handleError(e: Exception) {
        Log.e(TAG, "播放错误", e)

        runOnMainThread {
            currentState = PlaybackState.ERROR
            stateListener?.onPlaybackStateChanged(currentState)
            stateListener?.onError("播放失败: ${e.message}")
        }
    }

    private fun startProgressTimer() {
        // 移除旧的回调，防止重复
        stopProgressTimer()
        mainHandler.post(progressUpdateRunnable)
    }

    private fun stopProgressTimer() {
        mainHandler.removeCallbacks(progressUpdateRunnable)
    }

    /**
     * 确保在主线程上运行代码
     */
    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    fun play() {
        runOnMainThread {
            exoPlayer?.play()
        }
    }

    fun pause() {
        runOnMainThread {
            exoPlayer?.pause()
        }
    }

    fun stop() {
        runOnMainThread {
            exoPlayer?.stop()
            currentState = PlaybackState.STOPPED
            stateListener?.onPlaybackStateChanged(currentState)
            stopProgressTimer()
        }
    }

    fun seekTo(positionMs: Int) {
        runOnMainThread {
            exoPlayer?.seekTo(positionMs.toLong())
        }
    }

    fun getCurrentPosition(): Int {
        return exoPlayer?.currentPosition?.toInt() ?: 0
    }

    fun getDuration(): Int {
        return duration
    }

    fun getCurrentState(): PlaybackState {
        return currentState
    }

    fun setVolume(volume: Float) {
        runOnMainThread {
            exoPlayer?.volume = volume
        }
    }

    fun release() {
        runOnMainThread {
            stopProgressTimer()
            exoPlayer?.release()
            exoPlayer = null
            currentState = PlaybackState.STOPPED
        }
    }
}