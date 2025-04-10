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
import java.util.regex.Pattern

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
        
        // 爱奇艺域名匹配正则 - 增强版
        private val IQIYI_PATTERN = Pattern.compile("(iqiyi\\.com|qiyi\\.com|pps\\.tv)", Pattern.CASE_INSENSITIVE)
        
        // 爱奇艺特殊处理参数
        private const val IQIYI_REFERER = "https://www.iqiyi.com/"
        
        // 爱奇艺流媒体URL匹配正则
        private val IQIYI_STREAM_PATTERN = Pattern.compile("(https?://[^/]*\\.iqiyi\\.com/[^\\s\"]+\\.m3u8[^\\s\"]*)", Pattern.CASE_INSENSITIVE)
        
        // 爱奇艺TS文件URL匹配正则
        private val IQIYI_TS_PATTERN = Pattern.compile("(https?://[^/]*\\.iqiyi\\.com/[^\\s\"]+\\.ts[^\\s\"]*)", Pattern.CASE_INSENSITIVE)
        
        // 通用M3U8匹配正则
        private val M3U8_PATTERN = Pattern.compile("(https?://[^\\s\"]+\\.m3u8[^\\s\"]*)", Pattern.CASE_INSENSITIVE)
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
    /** 重试计数 */
    private var retryCount = 0
    /** 最大重试次数 */
    private val maxRetries = 3
    /** 是否已尝试提取真实流链接 */
    private var hasTriedExtractingRealStream = false

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
            hasTriedExtractingRealStream = false
            Log.d(TAG, "准备设置媒体URI: $uri")
            
            // 检查是否是爱奇艺链接 - 增强检测
            val isIqiyi = isIqiyiStream(uri)
            
            // 特殊处理爱奇艺链接
            if (isIqiyi && !uri.startsWith("file://")) {
                Log.d(TAG, "检测到爱奇艺链接，尝试提取流地址")
                extractIqiyiStreamUrl(uri)
                return
            }
            
            // 处理URI，特别是爱奇艺的复杂URI
            val processedUri = processUri(uri)
            Log.d(TAG, "处理后的URI: $processedUri")
            
            // 创建新的MediaPlayer实例
            setupMediaPlayer(processedUri)
        } catch (e: Exception) {
            Log.e(TAG, "设置媒体URI失败", e)
            
            // 如果是爱奇艺链接，尝试使用替代方法
            if (isIqiyiStream(uri) && retryCount < maxRetries) {
                retryCount++
                Log.d(TAG, "检测到爱奇艺链接出错，尝试替代方法, 重试次数: $retryCount")
                tryWithHttpProxy(uri)
                return
            }
            
            currentState = PlaybackState.ERROR
            stateListener?.onError("设置媒体失败: ${e.message}")
            stateListener?.onPlaybackStateChanged(currentState)
        }
    }
    
    /**
     * 设置MediaPlayer实例
     */
    private fun setupMediaPlayer(uri: String) {
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
                retryCount = 0 // 重置重试计数
                
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
                // 检查是否是爱奇艺流媒体
                val isIqiyi = isIqiyiStream(uri)
                
                if (uri.startsWith("file://")) {
                    // 本地文件（可能是我们自己缓存的文件）
                    Log.d(TAG, "使用本地文件: $uri")
                    setDataSource(uri)
                } else if (uri.startsWith("http")) {
                    // 网络文件，添加代理头
                    Log.d(TAG, "使用网络URI: $uri")
                    val headers = HashMap<String, String>()
                    headers["User-Agent"] = USER_AGENT
                    headers["Connection"] = "keep-alive"
                    headers["Accept"] = "*/*"
                    
                    // 针对爱奇艺添加特殊处理
                    if (isIqiyi) {
                        Log.d(TAG, "检测到爱奇艺流媒体，添加特殊处理")
                        headers["Referer"] = IQIYI_REFERER
                        headers["Origin"] = "https://www.iqiyi.com"
                    }
                    
                    setDataSource(context, Uri.parse(uri), headers)
                } else {
                    // 其他类型的URI，直接设置
                    Log.d(TAG, "使用其他类型URI: $uri")
                    setDataSource(uri)
                }
                Log.d(TAG, "设置数据源成功")
            } catch (e: Exception) {
                Log.e(TAG, "设置数据源失败: ${e.message}", e)
                throw e
            }
            
            Log.d(TAG, "开始异步准备媒体播放器")
            prepareAsync()
        }
    }
    
    /**
     * 提取爱奇艺真实流URL
     */
    private fun extractIqiyiStreamUrl(uri: String) {
        if (hasTriedExtractingRealStream) {
            Log.d(TAG, "已尝试过提取真实流地址，直接继续常规播放流程")
            tryWithHttpProxy(uri)
            return
        }
        
        hasTriedExtractingRealStream = true
        
        // 异步处理，避免阻塞主线程
        Thread {
            try {
                Log.d(TAG, "开始提取爱奇艺真实流地址: $uri")
                
                // 创建请求获取页面内容
                val request = Request.Builder()
                    .url(uri)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", IQIYI_REFERER)
                    .build()
                
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw IOException("请求失败: ${response.code}")
                }
                
                val content = response.body?.string() ?: ""
                Log.d(TAG, "获取到内容长度: ${content.length}")
                
                // 尝试在内容中查找m3u8链接
                val m3u8Urls = extractM3u8UrlsFromContent(content)
                if (m3u8Urls.isNotEmpty()) {
                    Log.d(TAG, "找到${m3u8Urls.size}个M3U8链接，第一个: ${m3u8Urls.first()}")
                    
                    // 使用找到的第一个M3U8链接
                    val realStreamUrl = m3u8Urls.first()
                    
                    // 在主线程中设置媒体URI
                    handler.post {
                        setMediaURI(realStreamUrl)
                    }
                    return@Thread
                }
                
                Log.d(TAG, "未找到M3U8链接，尝试常规方法")
                handler.post {
                    tryWithHttpProxy(uri)
                }
            } catch (e: Exception) {
                Log.e(TAG, "提取爱奇艺真实流地址失败", e)
                handler.post {
                    tryWithHttpProxy(uri)
                }
            }
        }.start()
    }
    
    /**
     * 从内容中提取M3U8链接
     */
    private fun extractM3u8UrlsFromContent(content: String): List<String> {
        val m3u8Urls = mutableListOf<String>()
        
        // 先尝试匹配爱奇艺特定的流地址格式
        val iqiyiMatcher = IQIYI_STREAM_PATTERN.matcher(content)
        while (iqiyiMatcher.find()) {
            val foundUrl = iqiyiMatcher.group(1)
            if (foundUrl != null) {
                m3u8Urls.add(foundUrl)
            }
        }
        
        // 如果没有找到爱奇艺特定格式，尝试更通用的M3U8格式
        if (m3u8Urls.isEmpty()) {
            val m3u8Matcher = M3U8_PATTERN.matcher(content)
            while (m3u8Matcher.find()) {
                val foundUrl = m3u8Matcher.group(1)
                if (foundUrl != null) {
                    m3u8Urls.add(foundUrl)
                }
            }
        }
        
        return m3u8Urls
    }
    
    /**
     * 检查是否是爱奇艺流媒体
     */
    private fun isIqiyiStream(uri: String): Boolean {
        return IQIYI_PATTERN.matcher(uri).find()
    }
    
    /**
     * 尝试使用HTTP代理方法解决-38错误
     */
    private fun tryWithHttpProxy(uri: String) {
        // 先通知用户正在尝试代替方案
        handler.post {
            stateListener?.onError("检测到格式问题，正在尝试替代播放方案... (${retryCount}/${maxRetries})")
        }
        
        Thread {
            try {
                Log.d(TAG, "开始HTTP代理尝试: $uri")
                
                // 创建请求获取媒体内容
                val request = Request.Builder()
                    .url(uri)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", if (isIqiyiStream(uri)) IQIYI_REFERER else uri)
                    .build()
                
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw IOException("请求失败: ${response.code}")
                }
                
                // 检查内容类型
                val contentType = response.header("Content-Type", "")!!
                Log.d(TAG, "媒体内容类型: $contentType")
                
                // 根据内容类型处理不同的情况
                when {
                    contentType.contains("mpegurl") || uri.lowercase().contains(".m3u8") -> {
                        // 处理M3U8
                        handleM3U8Content(response, uri)
                    }
                    contentType.contains("mp4") || contentType.contains("video") -> {
                        // 处理直接媒体文件
                        handleDirectMediaContent(response)
                    }
                    else -> {
                        // 未知类型，尝试作为m3u8处理
                        handleM3U8Content(response, uri)
                    }
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
     * 处理M3U8内容
     */
    private fun handleM3U8Content(response: Response, originalUri: String) {
        val m3u8Content = response.body?.string() ?: throw IOException("M3U8内容为空")
        Log.d(TAG, "获取到M3U8内容长度: ${m3u8Content.length}")
        
        // 解析M3U8获取TS文件列表
        val tsFiles = extractTsUrls(m3u8Content, originalUri)
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
    }
    
    /**
     * 处理直接媒体内容
     */
    private fun handleDirectMediaContent(response: Response) {
        // 直接保存媒体内容到本地文件
        val extension = if (response.header("Content-Type", "")!!.contains("mp4")) "mp4" else "ts"
        val cacheFile = File(context.cacheDir, "temp_media_${System.currentTimeMillis()}.$extension")
        
        response.body?.byteStream()?.use { input ->
            FileOutputStream(cacheFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("响应体为空")
        
        currentCacheFile = cacheFile
        
        // 使用本地文件URI重新设置媒体
        val fileUri = "file://${cacheFile.absolutePath}"
        Log.d(TAG, "使用本地缓存文件: $fileUri")
        
        handler.post {
            setMediaURI(fileUri)
        }
    }
    
    /**
     * 下载媒体文件
     */
    private fun downloadMediaFile(url: String, outputFile: File) {
        Log.d(TAG, "开始下载媒体文件: $url 到 ${outputFile.absolutePath}")
        
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", if (isIqiyiStream(url)) IQIYI_REFERER else url)
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
            
            // 爱奇艺特殊处理：检查#EXT-X-KEY
            if (trimmedLine.startsWith("#EXT-X-KEY") && isIqiyiStream(baseUrl)) {
                Log.d(TAG, "检测到爱奇艺的加密密钥行: $trimmedLine")
                // 这里可以添加特殊处理逻辑
            }
        }
        
        return tsUrls
    }
    
    /**
     * 处理URI，解决特殊格式问题
     */
    private fun processUri(uri: String): String {
        // 处理爱奇艺的URI
        if (isIqiyiStream(uri)) {
            // 爱奇艺链接需要特殊处理
            val cleaned = uri.replace("&amp;", "&")
                .replace(" ", "%20")
            
            Log.d(TAG, "处理爱奇艺URI: $cleaned")
            return cleaned
        }
        
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
                        if (retryCount < maxRetries) {
                            retryCount++
                            tryWithHttpProxy(currentUri)
                            return@setOnErrorListener true
                        }
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