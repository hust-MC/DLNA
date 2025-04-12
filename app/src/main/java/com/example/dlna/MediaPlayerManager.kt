package com.example.dlna

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
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
        private const val MAX_RETRIES = 3
        
        // User-Agent常量
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
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
    private var pendingUri: String? = null

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

    fun setMediaURI(uri: String) {
        try {
            currentUri = uri
            retryCount = 0
            Log.d(TAG, "设置媒体URI: $uri")
            
            // 处理URI，特别是爱奇艺的复杂URI
            val processedUri = processUri(uri)
            Log.d(TAG, "处理后的URI: $processedUri")
            
            // 在主线程上释放播放器和设置新的媒体源
            runOnMainThread {
                try {
                    release()
                    setupExoPlayer("https://sf1-cdn-tos.huoshanstatic.com/obj/media-fe/xgplayer_doc_video/mp4/xgplayer-demo-360p.mp4")
                } catch (e: Exception) {
                    handleError(e)
                }
            }
        } catch (e: Exception) {
            handleError(e)
        }
    }
    
    /**
     * 处理URI
     */
    private fun processUri(uri: String): String {
        Log.d(TAG, "处理URI: ${uri.take(100)}...")
        
        // 处理空URI
        if (uri.isBlank()) {
            Log.e(TAG, "URI为空")
            throw IllegalArgumentException("URI不能为空")
        }
        
        // 爱奇艺链接特殊处理
        if (uri.contains("iqiyi.com") || uri.contains("iqiy") || uri.contains("qiyi.com")) {
            var processedUri = uri
            
            // 1. 移除查询参数以简化URL（这些参数可能导致错误）
            if (uri.contains("?")) {
                processedUri = uri.substring(0, uri.indexOf("?"))
                Log.d(TAG, "已移除爱奇艺URI查询参数: $processedUri")
            }
            
            // 2. 转换为HTTP协议（如果是HTTPS）
            if (processedUri.startsWith("https://")) {
                processedUri = processedUri.replace("https://", "http://")
                Log.d(TAG, "将爱奇艺URI转换为HTTP: $processedUri")
            }
            
            // 3. 移除多余的后缀，如果有(比如.m3u8)
            if (processedUri.contains(".m3u8", ignoreCase = true)) {
                processedUri = processedUri.replace(".m3u8", "", ignoreCase = true)
                Log.d(TAG, "已移除m3u8后缀: $processedUri")
            }
            
            // 4. 检查是否包含已知的问题域名并替换
            val problematicDomains = arrayOf("cache.video.iqiyi.com", "mus.video.iqiyi.com")
            for (domain in problematicDomains) {
                if (processedUri.contains(domain)) {
                    processedUri = processedUri.replace(domain, "data.video.iqiyi.com")
                    Log.d(TAG, "已替换问题域名: $processedUri")
                    break
                }
            }
            
            return processedUri
        }
        
        // 如果URI中包含特殊字符，进行URL编码
        if (uri.contains(" ") || uri.contains("#") || uri.contains("%")) {
            try {
                val encodedUri = java.net.URLEncoder.encode(uri, "UTF-8")
                    .replace("+", "%20") // 替换空格为%20而不是+
                Log.d(TAG, "URL编码后的URI: $encodedUri")
                return encodedUri
            } catch (e: Exception) {
                Log.e(TAG, "URI编码失败", e)
                // 如果编码失败，尝试简单替换
                val simpleEncodedUri = uri.replace(" ", "%20")
                    .replace("#", "%23")
                Log.d(TAG, "简单替换后的URI: $simpleEncodedUri")
                return simpleEncodedUri
            }
        }
        
        return uri
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
                    
                    // 爱奇艺链接特殊处理
                    if (uri.contains("iqiyi.com") || uri.contains("iqiy") || uri.contains("qiyi.com")) {
                        Log.d(TAG, "爱奇艺链接播放错误，尝试其他方式处理")
                        
                        // 完全重置ExoPlayer
                        runOnMainThread {
                            try {
                                // 释放当前播放器
                                exoPlayer?.release()
                                
                                // 创建新播放器
                                exoPlayer = ExoPlayer.Builder(context).build()
                                
                                // 重新设置监听器
                                exoPlayer?.addListener(this)
                                
                                // 设置视频输出
                                if (surface != null) {
                                    exoPlayer?.setVideoSurface(surface)
                                }
                                
                                // 创建纯HTTP数据源工厂
                                val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                                    .setUserAgent(USER_AGENT)
                                    .setAllowCrossProtocolRedirects(true)
                                    .setConnectTimeoutMs(30000)
                                    .setReadTimeoutMs(30000)
                                
                                // 设置请求头
                                val headers = HashMap<String, String>()
                                headers["Referer"] = "https://www.iqiyi.com/"
                                headers["Origin"] = "https://www.iqiyi.com"
                                httpDataSourceFactory.setDefaultRequestProperties(headers)
                                
                                // 创建媒体项，指定类型为其他格式（而不是HLS）
                                val mediaItem = MediaItem.Builder()
                                    .setUri(uri)
                                    .setMimeType("video/mp4") // 强制指定为MP4格式
                                    .build()
                                
                                // 使用默认媒体源工厂（不使用HLS）
                                val mediaSource = DefaultMediaSourceFactory(httpDataSourceFactory)
                                    .createMediaSource(mediaItem)
                                
                                // 设置媒体源并播放
                                exoPlayer?.setMediaSource(mediaSource)
                                exoPlayer?.prepare()
                                
                                if (shouldAutoPlay) {
                                    exoPlayer?.play()
                                }
                                
                                return@runOnMainThread
                            } catch (e: Exception) {
                                Log.e(TAG, "爱奇艺链接特殊处理失败", e)
                            }
                        }
                    }
                    
                    if (retryCount < MAX_RETRIES) {
                        retryCount++
                        Log.d(TAG, "尝试重新播放 ($retryCount/$MAX_RETRIES)")
                        
                        // 常规重试
                        mainHandler.postDelayed({
                            setupExoPlayer(uri)
                        }, 1000)
                    } else {
                        runOnMainThread {
                            currentState = PlaybackState.ERROR
                            stateListener?.onPlaybackStateChanged(currentState)
                            stateListener?.onError("播放错误: ${error.message}")
                        }
                    }
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
            
            // 创建适合的媒体源
            val mediaSource = createMediaSource(uri)
            
            // 设置媒体源并准备播放
            exoPlayer?.setMediaSource(mediaSource)
            exoPlayer?.prepare()
        } catch (e: Exception) {
            handleError(e)
        }
    }
    
    private fun createMediaSource(uri: String): MediaSource {
        // 确保该方法在主线程中调用
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw IllegalStateException("createMediaSource必须在主线程调用")
        }
            
        // 创建HTTP数据源工厂
        val httpDataSourceFactory = createHttpDataSourceFactory(uri)
        
        // 对于爱奇艺的链接，使用特殊处理
        if (uri.contains("iqiyi.com") || uri.contains("iqiy") || uri.contains("qiyi.com")) {
            Log.d(TAG, "爱奇艺链接使用特殊处理媒体源")
            
            // 创建媒体项，明确指定为MP4格式，避免任何HLS解析
            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMimeType("video/mp4") // 强制指定为MP4格式
                .build()
            
            // 使用默认媒体源工厂
            return DefaultMediaSourceFactory(httpDataSourceFactory)
                .createMediaSource(mediaItem)
        }
        
        // 为HLS流(M3U8)创建特殊的媒体源
        if (uri.contains(".m3u8", ignoreCase = true)) {
            return HlsMediaSource.Factory(httpDataSourceFactory)
                .setAllowChunklessPreparation(true)
                .createMediaSource(MediaItem.fromUri(uri))
        }
        
        // 为普通媒体创建默认媒体源
        return DefaultMediaSourceFactory(httpDataSourceFactory)
            .createMediaSource(MediaItem.fromUri(uri))
    }
    
    /**
     * 创建HTTP数据源工厂
     */
    private fun createHttpDataSourceFactory(uri: String): DefaultHttpDataSource.Factory {
        // 创建带有自定义UA和头信息的数据源工厂
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(30000)
            .setReadTimeoutMs(30000)
        
        // 针对爱奇艺链接添加额外的请求头
        if (uri.contains("iqiyi.com") || uri.contains("iqiy") || uri.contains("qiyi.com")) {
            val headers = HashMap<String, String>()
            headers["Referer"] = "https://www.iqiyi.com/"
            headers["Origin"] = "https://www.iqiyi.com"
            httpDataSourceFactory.setDefaultRequestProperties(headers)
        }
        
        return httpDataSourceFactory
    }
    
    private fun handleError(e: Exception) {
        Log.e(TAG, "播放错误", e)
        if (retryCount < MAX_RETRIES) {
            retryCount++
            Log.d(TAG, "发生错误，尝试重试: $retryCount")
            mainHandler.postDelayed({
                setupExoPlayer(currentUri)
            }, 1000L * retryCount)
        } else {
            runOnMainThread {
                currentState = PlaybackState.ERROR
                stateListener?.onPlaybackStateChanged(currentState)
                stateListener?.onError("播放失败: ${e.message}")
            }
        }
    }
    
    private fun startProgressTimer() {
        stopProgressTimer()
        progressTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
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