package com.example.dlna

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import java.util.Timer
import java.util.TimerTask

/**
 * 媒体播放器管理类
 * 使用ExoPlayer替代MediaPlayer，提供更好的M3U8支持
 * @author Max
 */
class MediaPlayerManager(private val context: Context) {
    companion object {
        private const val TAG = "MediaPlayerManager"
    }

    private var exoPlayer: ExoPlayer? = null
    private var stateListener: MediaStateListener? = null
    private var currentState = PlaybackState.STOPPED
    private var currentUri: String = ""
    private var duration: Int = 0
    private var progressTimer: Timer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var retryCount = 0
    private var surface: Surface? = null
    private var shouldAutoPlay: Boolean = false

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
            retryCount = 0
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

            // 创建ExoPlayer实例
            exoPlayer = ExoPlayer.Builder(context).build()

            // 设置播放状态监听
            exoPlayer?.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    runOnMainThread {
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
                                    currentState = PlaybackState.PLAYING
                                    stateListener?.onPlaybackStateChanged(currentState)
                                    startProgressTimer()
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
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "ExoPlayer错误: ${error.message}")
                    handleError(error)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    runOnMainThread {
                        if (isPlaying) {
                            currentState = PlaybackState.PLAYING
                            stateListener?.onPlaybackStateChanged(currentState)
                            startProgressTimer()
                        } else if (exoPlayer?.playbackState == Player.STATE_READY) {
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

            // 设置媒体源并准备播放
            val mediaSource: HlsMediaSource =
                HlsMediaSource.Factory(MediaCacheFactory.getCacheFactory(context))
                    .setAllowChunklessPreparation(true).createMediaSource(MediaItem.fromUri(uri))
//            val mediaSource: ProgressiveMediaSource = ProgressiveMediaSource.Factory(MediaCacheFactory .getCacheFactory(context.applicationContext)).createMediaSource(MediaItem.fromUri(uri))

            exoPlayer?.setMediaSource(mediaSource)
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
        stopProgressTimer()
        progressTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    runOnMainThread {
                        exoPlayer?.let { player ->
                            if (player.isPlaying) {
                                val position = player.currentPosition.toInt()
                                stateListener?.onProgressUpdate(position)
                            }
                        }
                    }
                }
            }, 0, 1000)
        }
    }

    private fun stopProgressTimer() {
        progressTimer?.cancel()
        progressTimer = null
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
        return if (Looper.myLooper() == Looper.getMainLooper()) {
            exoPlayer?.currentPosition?.toInt() ?: 0
        } else {
            // 在非主线程上不能直接访问ExoPlayer
            0
        }
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
            retryCount = 0
        }
    }

    fun releasePlayer() {
        release()
    }
}