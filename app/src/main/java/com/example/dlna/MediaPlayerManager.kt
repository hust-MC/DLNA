package com.example.dlna

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit

/**
 * 媒体播放器管理类
 * 控制媒体的播放、暂停、停止等操作，并提供状态更新回调
 * @author Max
 */
class MediaPlayerManager(private val context: Context) {
    companion object {
        private const val TAG = "MediaPlayerManager"
        
        // User-Agent常量
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        
        // 用于解决-38错误的HTTP客户端
        private val okHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }

    /** 媒体播放器实例 */
    private var mediaPlayer: MediaPlayer? = null
    /** 播放状态监听器 */
    private var stateListener: MediaStateListener? = null
    /** 是否自动播放 */
    private var shouldAutoPlay: Boolean = false
    /** 当前状态 */
    private var currentState = PlaybackState.STOPPED
    /** 当前URI */
    private var currentUri: String = ""
    /** 当前媒体URI */
    private var currentMediaUri: String? = null
    /** 媒体总时长(毫秒) */
    private var duration: Int = 0
    /** 进度定时器 */
    private var progressTimer: Timer? = null
    /** UI更新处理器 */
    private val handler = Handler(Looper.getMainLooper())
    /** 当前正在使用的缓存文件 */
    private var currentCacheFile: File? = null

    /** 播放状态 */
    enum class PlaybackState {
        STOPPED,
        PLAYING,
        PAUSED,
        ERROR
    }

    /** 媒体状态监听接口 */
    interface MediaStateListener {
        /** 准备完成 */
        fun onPrepared(durationMs: Int)
        /** 进度更新 */
        fun onProgressUpdate(positionMs: Int)
        /** 状态变化 */
        fun onPlaybackStateChanged(state: PlaybackState)
        /** 播放完成 */
        fun onPlaybackCompleted()
        /** 播放错误 */
        fun onError(errorMsg: String)
    }

    /**
     * 设置状态监听器
     */
    fun setStateListener(listener: MediaStateListener) {
        stateListener = listener
    }

    /**
     * 设置媒体URI并自动播放
     */
    fun setMediaURIAndPlay(uri: String) {
        shouldAutoPlay = true
        setMediaURI(uri)
    }

    /**
     * 设置媒体URI
     */
    fun setMediaURI(uri: String) {
        try {
            // 释放现有播放器资源
            release()
            
            currentUri = uri
            currentMediaUri = uri
            Log.d(TAG, "准备设置媒体URI: $uri")
            
            // 处理URI，特别是爱奇艺的复杂URI
            val processedUri = processUri(uri)
            Log.d(TAG, "处理后的URI: $processedUri")
            
            // 创建新的MediaPlayer实例
            mediaPlayer = MediaPlayer().apply {
                // 避免使用已过时的方法
                try {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    Log.d(TAG, "使用新的AudioAttributes API")
                } catch (e: Exception) {
                    // 如果新API不可用，使用旧的方法
                    @Suppress("DEPRECATION")
                    setAudioStreamType(AudioManager.STREAM_MUSIC)
                    Log.d(TAG, "使用旧的setAudioStreamType API")
                }
                
                // 设置错误监听器
                setPlayerErrorListener(this)
                
                // 设置其他监听器
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
                
                setOnInfoListener { _, what, _ ->
                    Log.d(TAG, "媒体信息事件: $what")
                    true
                }
                
                try {
                    if (processedUri.startsWith("file://")) {
                        // 本地文件（可能是我们自己缓存的文件）
                        Log.d(TAG, "使用本地文件: $processedUri")
                        setDataSource(processedUri)
                    } else if (processedUri.startsWith("http")) {
                        // 网络文件，添加代理头
                        Log.d(TAG, "使用网络URI: $processedUri")
                        val headers = HashMap<String, String>()
                        headers["User-Agent"] = USER_AGENT
                        headers["Connection"] = "keep-alive"
                        headers["Accept"] = "*/*"
                        
                        setDataSource(context, Uri.parse(processedUri), headers)
                    } else {
                        // 其他类型的URI，直接设置
                        Log.d(TAG, "使用其他类型URI: $processedUri")
                        setDataSource(processedUri)
                    }
                    Log.d(TAG, "设置数据源成功")
                } catch (e: Exception) {
                    Log.e(TAG, "设置数据源失败: ${e.message}", e)
                    throw e
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
     * 尝试使用HTTP代理方法解决-38错误
     */
    private fun tryWithHttpProxy(uri: String) {
        // 先通知用户正在尝试代替方案
        handler.post {
            stateListener?.onError("检测到格式问题，正在尝试替代播放方案...")
        }
        
        Thread {
            try {
                Log.d(TAG, "开始HTTP代理尝试: $uri")
                
                // 检查是否为m3u8文件
                if (!uri.lowercase().contains(".m3u8")) {
                    Log.d(TAG, "不是m3u8文件，尝试直接下载媒体文件")
                    // 对非m3u8文件，尝试直接下载
                    val cacheFile = File(context.cacheDir, "temp_media_${System.currentTimeMillis()}.mp4")
                    downloadMediaFile(uri, cacheFile)
                    currentCacheFile = cacheFile
                    
                    // 使用本地文件URI重新设置媒体
                    val fileUri = "file://${cacheFile.absolutePath}"
                    Log.d(TAG, "使用本地缓存文件: $fileUri")
                    
                    handler.post {
                        setMediaURI(fileUri)
                    }
                    return@Thread
                }
                
                // 创建请求获取m3u8内容
                val request = Request.Builder()
                    .url(uri)
                    .addHeader("User-Agent", USER_AGENT)
                    .build()
                
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw IOException("M3U8请求失败: ${response.code}")
                }
                
                val m3u8Content = response.body?.string() ?: throw IOException("M3U8内容为空")
                Log.d(TAG, "获取到M3U8内容: ${m3u8Content.take(100)}...")
                
                // 解析M3U8获取TS文件列表
                val tsFiles = extractTsUrls(m3u8Content, uri)
                if (tsFiles.isEmpty()) {
                    throw IOException("M3U8文件中没有找到TS文件")
                }
                
                Log.d(TAG, "解析到${tsFiles.size}个TS文件，第一个: ${tsFiles.first()}")
                
                // 下载第一个TS文件
                val cacheFile = File(context.cacheDir, "temp_media_${System.currentTimeMillis()}.ts")
                downloadMediaFile(tsFiles.first(), cacheFile)
                currentCacheFile = cacheFile
                
                // 使用本地文件URI重新设置媒体
                val fileUri = "file://${cacheFile.absolutePath}"
                Log.d(TAG, "使用本地缓存文件: $fileUri")
                
                handler.post {
                    setMediaURI(fileUri)
                }
            } catch (e: Exception) {
                Log.e(TAG, "HTTP代理尝试失败", e)
                handler.post {
                    stateListener?.onError("替代播放方案失败: ${e.message}")
                    currentState = PlaybackState.ERROR
                    stateListener?.onPlaybackStateChanged(currentState)
                }
            }
        }.start()
    }
    
    /**
     * 下载媒体文件
     */
    private fun downloadMediaFile(url: String, outputFile: File) {
        Log.d(TAG, "开始下载媒体文件: $url 到 ${outputFile.absolutePath}")
        
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)
            .build()
        
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("下载请求失败: ${response.code}")
        }
        
        response.body?.byteStream()?.use { input ->
            FileOutputStream(outputFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L
                
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                }
                
                Log.d(TAG, "下载完成，总字节数: $totalBytesRead")
            }
        } ?: throw IOException("响应体为空")
    }
    
    /**
     * 从M3U8内容中提取TS文件URL
     */
    private fun extractTsUrls(m3u8Content: String, baseUrl: String): List<String> {
        val lines = m3u8Content.lines()
        val tsUrls = mutableListOf<String>()
        val baseUri = try {
            URL(baseUrl)
        } catch (e: Exception) {
            URL("http://example.com")
        }
        
        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#")) {
                val tsUrl = if (trimmedLine.startsWith("http")) {
                    trimmedLine
                } else if (trimmedLine.startsWith("/")) {
                    "${baseUri.protocol}://${baseUri.host}${trimmedLine}"
                } else {
                    val baseUrlPath = baseUrl.substringBeforeLast("/", "")
                    "$baseUrlPath/$trimmedLine"
                }
                tsUrls.add(tsUrl)
            }
        }
        
        return tsUrls
    }
    
    /**
     * 处理URI，解决特殊格式问题
     */
    private fun processUri(uri: String): String {
        // 简单的URI处理，返回原始URI
        return uri
    }
    
    /**
     * 设置播放器错误监听器
     */
    private fun setPlayerErrorListener(player: MediaPlayer) {
        player.setOnErrorListener { _, what, extra ->
            Log.e(TAG, "播放器错误: what=$what, extra=$extra")
            
            // 处理特定错误
            when (what) {
                MediaPlayer.MEDIA_ERROR_UNKNOWN -> {
                    if (extra == -38) {
                        // 对于-38错误，尝试使用HTTP代理方法
                        Log.d(TAG, "检测到-38错误，尝试使用HTTP代理方法")
                        tryWithHttpProxy(currentUri)
                        return@setOnErrorListener true
                    }
                }
            }
            
            // 通知错误
            currentState = PlaybackState.ERROR
            stateListener?.onError("播放器错误: $what, $extra")
            stateListener?.onPlaybackStateChanged(currentState)
            
            true // 返回true表示我们已处理错误
        }
    }
    
    /**
     * 开始播放
     */
    fun play() {
        try {
            mediaPlayer?.let {
                it.start()
                currentState = PlaybackState.PLAYING
                stateListener?.onPlaybackStateChanged(currentState)
                startProgressUpdates()
                Log.d(TAG, "开始播放")
            } ?: run {
                Log.e(TAG, "播放失败: MediaPlayer为null")
                stateListener?.onError("播放失败: 播放器未初始化")
            }
        } catch (e: Exception) {
            Log.e(TAG, "播放异常", e)
            currentState = PlaybackState.ERROR
            stateListener?.onError("播放异常: ${e.message}")
            stateListener?.onPlaybackStateChanged(currentState)
        }
    }
    
    /**
     * 暂停播放
     */
    fun pause() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    currentState = PlaybackState.PAUSED
                    stateListener?.onPlaybackStateChanged(currentState)
                    stopProgressUpdates()
                    Log.d(TAG, "暂停播放")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "暂停异常", e)
            stateListener?.onError("暂停异常: ${e.message}")
        }
    }
    
    /**
     * 停止播放
     */
    fun stop() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                currentState = PlaybackState.STOPPED
                stateListener?.onPlaybackStateChanged(currentState)
                stopProgressUpdates()
                Log.d(TAG, "停止播放")
            }
        } catch (e: Exception) {
            Log.e(TAG, "停止异常", e)
            stateListener?.onError("停止异常: ${e.message}")
        }
    }
    
    /**
     * 释放播放器资源
     */
    fun release() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                it.release()
                mediaPlayer = null
                currentState = PlaybackState.STOPPED
                stateListener?.onPlaybackStateChanged(currentState)
                stopProgressUpdates()
                Log.d(TAG, "释放播放器资源")
            }
            
            // 清理缓存文件
            currentCacheFile?.let {
                if (it.exists()) {
                    it.delete()
                    Log.d(TAG, "删除缓存文件: ${it.absolutePath}")
                }
                currentCacheFile = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "释放资源异常", e)
        }
    }
    
    /**
     * 跳转到指定位置
     */
    fun seekTo(position: Int) {
        try {
            mediaPlayer?.let {
                // 确保位置在有效范围内
                val validPosition = when {
                    position < 0 -> 0
                    position > duration -> duration
                    else -> position
                }
                
                it.seekTo(validPosition)
                Log.d(TAG, "跳转到: $validPosition ms")
                stateListener?.onProgressUpdate(validPosition)
            }
        } catch (e: Exception) {
            Log.e(TAG, "跳转异常", e)
            stateListener?.onError("跳转异常: ${e.message}")
        }
    }
    
    /**
     * 获取当前播放位置
     */
    fun getCurrentPosition(): Int {
        return try {
            mediaPlayer?.currentPosition ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "获取当前位置异常", e)
            0
        }
    }
    
    /**
     * 获取媒体时长
     */
    fun getDuration(): Int {
        return duration
    }
    
    /**
     * 获取当前播放状态
     */
    fun getState(): PlaybackState {
        return currentState
    }
    
    /**
     * 是否正在播放
     */
    fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 开始进度更新
     */
    private fun startProgressUpdates() {
        stopProgressUpdates() // 确保先停止现有定时器
        
        progressTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    try {
                        val position = getCurrentPosition()
                        handler.post {
                            stateListener?.onProgressUpdate(position)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "进度更新异常", e)
                        cancel()
                    }
                }
            }, 0, 1000) // 每秒更新一次
        }
    }
    
    /**
     * 停止进度更新
     */
    private fun stopProgressUpdates() {
        progressTimer?.cancel()
        progressTimer = null
    }
}