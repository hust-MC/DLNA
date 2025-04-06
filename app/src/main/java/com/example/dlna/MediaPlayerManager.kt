package com.example.dlna

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Timer
import java.util.TimerTask

/**
 * 媒体播放器管理类
 *
 * 该类负责实际控制媒体的播放、暂停、停止等操作，
 * 并提供媒体播放状态更新和回调功能。
 * @author Max
 */
class MediaPlayerManager(private val context: Context) {
    companion object {
        private const val TAG = "MediaPlayerManager"
    }

    /** 媒体播放器实例 */
    private var mediaPlayer: MediaPlayer? = null

    /** 播放状态监听器 */
    private var stateListener: MediaStateListener? = null

    /** 是否在准备完成后自动播放 */
    private var shouldAutoPlay: Boolean = false

    /** 播放器当前状态 */
    private var currentState = PlaybackState.STOPPED

    /** 当前播放的媒体URI */
    private var currentUri: String = ""

    /** 当前媒体的总时长（毫秒） */
    private var duration: Int = 0

    /** 播放进度定时器 */
    private var progressTimer: Timer? = null

    /** UI更新处理器 */
    private val handler = Handler(Looper.getMainLooper())

    /** 播放状态枚举 */
    enum class PlaybackState {
        STOPPED,
        PLAYING,
        PAUSED,
        ERROR
    }

    /** 媒体状态监听接口 */
    interface MediaStateListener {
        /** 播放器准备完成回调 */
        fun onPrepared(durationMs: Int)

        /** 播放进度更新回调 */
        fun onProgressUpdate(positionMs: Int)

        /** 播放状态变化回调 */
        fun onPlaybackStateChanged(state: PlaybackState)

        /** 播放完成回调 */
        fun onPlaybackCompleted()

        /** 播放错误回调 */
        fun onError(errorMsg: String)
    }

    /**
     * 设置播放状态监听器
     *
     * @param listener 实现了MediaStateListener接口的监听器对象
     */
    fun setStateListener(listener: MediaStateListener) {
        stateListener = listener
    }

    /**
     * 设置媒体URI并自动播放
     *
     * 准备播放指定URI的媒体资源，并在准备完成后自动开始播放
     *
     * @param uri 媒体资源的URI
     */
    fun setMediaURIAndPlay(uri: String) {
        shouldAutoPlay = true
        setMediaURI(uri)
    }

    /**
     * 设置媒体URI
     *
     * 准备播放指定URI的媒体资源
     *
     * @param uri 媒体资源的URI
     */
    fun setMediaURI(uri: String) {
        try {
            // 释放现有播放器资源
            releasePlayer()
            
            currentUri = uri
            Log.d(TAG, "准备设置媒体URI: $uri")
            
            // 处理URI，特别是爱奇艺的复杂URI
            val processedUri = processUri(uri)
            Log.d(TAG, "处理后的URI: $processedUri")
            
            // 创建新的MediaPlayer实例
            mediaPlayer = MediaPlayer().apply {
                // 避免使用已过时的方法
                try {
                    javaClass.getMethod("setAudioAttributes", android.media.AudioAttributes::class.java)
                        .invoke(this, android.media.AudioAttributes.Builder()
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .build())
                    Log.d(TAG, "使用新的AudioAttributes API")
                } catch (e: Exception) {
                    // 如果新API不可用，使用旧的方法
                    @Suppress("DEPRECATION")
                    setAudioStreamType(AudioManager.STREAM_MUSIC)
                    Log.d(TAG, "使用旧的setAudioStreamType API")
                }
                
                // 设置监听器
                setOnPreparedListener { mp ->
                    Log.d(TAG, "媒体准备完成")
                    this@MediaPlayerManager.duration = mp.duration
                    // 如果时长为0，可能是流媒体，设置一个默认值
                    if (duration <= 0) {
                        this@MediaPlayerManager.duration = 3600000 // 1小时
                        Log.d(TAG, "媒体时长为0，可能是流媒体，设置默认时长1小时")
                    }
                    
                    currentState = PlaybackState.STOPPED
                    stateListener?.onPrepared(duration)
                    
                    // 有时候准备完成后需要立即开始播放
                    if (shouldAutoPlay) {
                        try {
                            start()
                            currentState = PlaybackState.PLAYING
                            stateListener?.onPlaybackStateChanged(currentState)
                            startProgressUpdates()
                            shouldAutoPlay = false
                            Log.d(TAG, "自动播放已启动")
                        } catch (e: Exception) {
                            Log.e(TAG, "自动播放失败", e)
                        }
                    }
                }
                
                setOnCompletionListener {
                    Log.d(TAG, "媒体播放完成")
                    stopProgressUpdates()
                    currentState = PlaybackState.STOPPED
                    stateListener?.onPlaybackCompleted()
                    stateListener?.onPlaybackStateChanged(currentState)
                }
                
                setOnErrorListener { _, what, extra ->
                    val errorMsg = when (what) {
                        MediaPlayer.MEDIA_ERROR_UNKNOWN -> "未知错误"
                        MediaPlayer.MEDIA_ERROR_SERVER_DIED -> "服务器错误"
                        MediaPlayer.MEDIA_ERROR_IO -> "IO错误"
                        MediaPlayer.MEDIA_ERROR_MALFORMED -> "格式错误"
                        MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> "不支持的格式"
                        MediaPlayer.MEDIA_ERROR_TIMED_OUT -> "超时"
                        -38 -> "解码错误-38，可能是格式不支持"
                        else -> "错误: $what"
                    }
                    
                    val extraInfo = when (extra) {
                        MediaPlayer.MEDIA_ERROR_TIMED_OUT -> "超时"
                        MediaPlayer.MEDIA_ERROR_IO -> "IO错误"
                        MediaPlayer.MEDIA_ERROR_MALFORMED -> "格式错误"
                        MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> "不支持的格式"
                        else -> "附加信息: $extra"
                    }
                    
                    Log.e(TAG, "媒体播放错误: $errorMsg, $extraInfo (URI: ${currentUri.take(100)}...)")
                    currentState = PlaybackState.ERROR
                    stateListener?.onError("播放错误: $errorMsg ($extraInfo)")
                    stateListener?.onPlaybackStateChanged(currentState)
                    true
                }
                
                // 设置数据源需要有重试机制
                var retryCount = 0
                val maxRetries = 3
                
                while (retryCount < maxRetries) {
                    try {
                        val headers = HashMap<String, String>()
                        headers["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
                        headers["Range"] = "bytes=0-"
                        headers["Connection"] = "keep-alive"
                        headers["Accept"] = "*/*"
                        
                        // 尝试使用headers设置数据源
                        try {
                            val property = MediaPlayer::class.java.getMethod("setDataSource", 
                                String::class.java,
                                java.util.Map::class.java)
                            property.invoke(this, processedUri, headers)
                            Log.d(TAG, "使用headers设置数据源成功")
                            break
                        } catch (e: Exception) {
                            Log.w(TAG, "使用headers设置数据源失败，尝试直接设置: ${e.message}")
                            setDataSource(processedUri)
                            Log.d(TAG, "直接设置数据源成功")
                            break
                        }
                    } catch (e: Exception) {
                        retryCount++
                        Log.e(TAG, "设置数据源失败 (尝试 $retryCount/$maxRetries): ${e.message}")
                        if (retryCount >= maxRetries) {
                            throw e // 重试次数用完，抛出异常
                        }
                        // 短暂等待后重试
                        Thread.sleep(500)
                    }
                }
                
                setOnInfoListener { _, what, _ ->
                    Log.d(TAG, "媒体信息事件: $what")
                    true
                }
                
                Log.d(TAG, "开始异步准备媒体播放器")
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置媒体URI失败", e)
            currentState = PlaybackState.ERROR
            stateListener?.onError("设置媒体失败: ${e.message}")
            stateListener?.onPlaybackStateChanged(currentState)
        }
    }
    
    /**
     * 处理URI
     *
     * 处理不同来源的媒体URI，确保它们能正确播放
     *
     * @param uri 原始URI
     * @return 处理后的URI
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
            
            // 1. 移除查询参数以简化URL（这些参数可能导致-38错误）
            if (uri.contains("?")) {
                processedUri = uri.substring(0, uri.indexOf("?"))
                Log.d(TAG, "已移除爱奇艺URI查询参数: $processedUri")
            }
            
            // 2. 特殊处理爱奇艺的加密参数
            if (processedUri.contains("/dash?")) {
                processedUri = processedUri.replace("/dash?", "/dash_simple?")
                Log.d(TAG, "处理爱奇艺dash链接: $processedUri")
            }
            
            // 3. 转换为HTTP协议（如果是HTTPS）
            if (processedUri.startsWith("https://")) {
                processedUri = processedUri.replace("https://", "http://")
                Log.d(TAG, "将爱奇艺URI转换为HTTP: $processedUri")
            }
            
            return processedUri
        }
        
        // 处理其他常见视频站点
        if (uri.contains("youku") || uri.contains("qq.com") || uri.contains("bilibili")) {
            val baseUri = if (uri.contains("?")) {
                uri.substring(0, uri.indexOf("?"))
            } else {
                uri
            }
            Log.d(TAG, "已处理视频URI: $baseUri")
            return baseUri
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
        
        // 如果是HTTP URI，确保它以http://或https://开头
        if (uri.contains("://")) {
            if (!uri.startsWith("http://") && !uri.startsWith("https://") && 
                !uri.startsWith("rtsp://") && !uri.startsWith("content://")) {
                Log.d(TAG, "URI没有有效的协议头，添加https://")
                return "https://$uri"
            }
        }
        
        // 如果是本地文件，确保使用file://
        if (uri.startsWith("/") && !uri.startsWith("file://")) {
            Log.d(TAG, "添加file://前缀到本地文件URI")
            return "file://$uri"
        }
        
        return uri
    }
    
    /**
     * 开始播放
     *
     * 开始或继续播放当前设置的媒体
     */
    fun play() {
        Log.d(TAG, "尝试开始播放，当前状态: $currentState")
        mediaPlayer?.let {
            try {
                // 检查播放器状态，如果是错误状态，尝试重新准备
                if (currentState == PlaybackState.ERROR) {
                    Log.d(TAG, "播放器处于错误状态，尝试重新初始化")
                    setMediaURI(currentUri)
                    return
                }
                
                it.start()
                currentState = PlaybackState.PLAYING
                stateListener?.onPlaybackStateChanged(currentState)
                startProgressUpdates()
            } catch (e: Exception) {
                Log.e(TAG, "播放失败: ${e.message}", e)
                currentState = PlaybackState.ERROR
                stateListener?.onError("播放失败: ${e.message}")
                stateListener?.onPlaybackStateChanged(currentState)
            }
        } ?: run {
            Log.e(TAG, "播放器未初始化")
            stateListener?.onError("播放器未初始化")
        }
    }

    /**
     * 暂停播放
     *
     * 暂停当前正在播放的媒体
     */
    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                currentState = PlaybackState.PAUSED
                stateListener?.onPlaybackStateChanged(currentState)
                stopProgressUpdates()
            }
        }
    }

    /**
     * 停止播放
     *
     * 停止当前媒体的播放并释放资源
     */
    fun stop() {
        stopProgressUpdates()
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                currentState = PlaybackState.STOPPED
                stateListener?.onPlaybackStateChanged(currentState)
            } catch (e: Exception) {
                Log.e(TAG, "停止播放失败", e)
            }
        }
    }

    /**
     * 跳转到指定位置
     *
     * @param positionMs 目标位置（毫秒）
     */
    fun seekTo(positionMs: Int) {
        mediaPlayer?.let {
            try {
                it.seekTo(positionMs)
            } catch (e: Exception) {
                Log.e(TAG, "跳转失败", e)
                stateListener?.onError("跳转失败: ${e.message}")
            }
        }
    }

    /**
     * 获取当前播放位置
     *
     * @return 当前播放位置（毫秒）
     */
    fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition ?: 0
    }

    /**
     * 获取媒体总时长
     *
     * @return 媒体总时长（毫秒）
     */
    fun getDuration(): Int {
        return duration
    }

    /**
     * 获取当前播放状态
     *
     * @return 当前的PlaybackState枚举值
     */
    fun getCurrentState(): PlaybackState {
        return currentState
    }

    /**
     * 设置音量
     *
     * @param volume 音量值（0.0到1.0）
     */
    fun setVolume(volume: Float) {
        val normalizedVolume = volume.coerceIn(0f, 1f)
        mediaPlayer?.setVolume(normalizedVolume, normalizedVolume)
    }

    /**
     * 开始进度更新
     *
     * 启动定时器以定期更新播放进度
     */
    private fun startProgressUpdates() {
        progressTimer?.cancel()
        progressTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    mediaPlayer?.let {
                        if (it.isPlaying) {
                            val position = it.currentPosition
                            handler.post {
                                stateListener?.onProgressUpdate(position)
                            }
                        }
                    }
                }
            }, 0, 1000) // 每秒更新一次
        }
    }

    /**
     * 停止进度更新
     *
     * 取消进度更新定时器
     */
    private fun stopProgressUpdates() {
        progressTimer?.cancel()
        progressTimer = null
    }

    /**
     * 释放播放器资源
     *
     * 停止播放并释放媒体播放器资源
     */
    fun releasePlayer() {
        stopProgressUpdates()
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                it.release()
            } catch (e: Exception) {
                Log.e(TAG, "释放播放器资源失败", e)
            }
        }
        mediaPlayer = null
        currentState = PlaybackState.STOPPED
    }
}