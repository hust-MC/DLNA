package com.example.dlna

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.fourthline.cling.binding.annotations.UpnpAction
import org.fourthline.cling.binding.annotations.UpnpInputArgument
import org.fourthline.cling.binding.annotations.UpnpOutputArgument
import org.fourthline.cling.binding.annotations.UpnpService
import org.fourthline.cling.binding.annotations.UpnpServiceId
import org.fourthline.cling.binding.annotations.UpnpServiceType
import org.fourthline.cling.binding.annotations.UpnpStateVariable
import org.fourthline.cling.model.types.UnsignedIntegerFourBytes
import org.fourthline.cling.support.avtransport.lastchange.AVTransportLastChangeParser
import org.fourthline.cling.support.avtransport.lastchange.AVTransportVariable
import org.fourthline.cling.support.lastchange.LastChange
import org.fourthline.cling.support.model.MediaInfo
import org.fourthline.cling.support.model.PositionInfo
import org.fourthline.cling.support.model.TransportState
import org.fourthline.cling.support.renderingcontrol.lastchange.ChannelMute
import org.fourthline.cling.support.renderingcontrol.lastchange.ChannelVolume
import org.fourthline.cling.support.renderingcontrol.lastchange.RenderingControlVariable
import org.seamless.xml.SAXParser
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.concurrent.TimeUnit

/**
 * UPnP媒体渲染服务
 * 
 * 该服务实现了UPnP AVTransport服务规范，用于控制媒体播放和传输。
 * 它处理媒体URI的设置、播放控制（播放、暂停、停止）以及进度控制（跳转）等功能。
 * @author Max
 */
@UpnpService(
    serviceId = UpnpServiceId("AVTransport"),
    serviceType = UpnpServiceType(value = "AVTransport", version = 1)
)
class MediaRendererService(private val context: Context) {
    
    /** 日志标签 */
    private val TAG = "MediaRendererService"
    
    /** 媒体播放器管理器 */
    private var mediaPlayerManager: MediaPlayerManager? = null
    
    // 声明变量
    private var avTransportLastChange: LastChange
    
    init {
        try {
            // 尝试使用标准的AVTransportLastChangeParser初始化LastChange实例
            avTransportLastChange = LastChange(AVTransportLastChangeParser())
            Log.d(TAG, "MediaRendererService实例已创建(使用标准LastChange解析器)")
        } catch (e: Exception) {
            // 使用备用方法创建
            Log.e(TAG, "标准LastChange初始化失败，使用备用方法", e)
            avTransportLastChange = createSafeLastChange()
        }
    }
    
    /**
     * 创建一个安全的LastChange对象
     */
    private fun createSafeLastChange(): LastChange {
        // 创建一个安全的LastChange对象
        val parser = AVTransportLastChangeParser()
        
        try {
            // 预解析一个空的Event来初始化解析器
            val initialXml = INITIAL_EVENT_XML
            parser.parse(initialXml)
            
            // 创建LastChange实例
            return LastChange(parser)
        } catch (e: Exception) {
            Log.e(TAG, "创建安全LastChange失败，使用最基本的实例化方式", e)
            
            // 使用最简单的构造函数
            return LastChange(AVTransportLastChangeParser())
        }
    }

    companion object {
        private const val TAG = "MediaRendererService_Static"
        
        /**
         * 默认的LastChange XML模板
         */
        private const val INITIAL_EVENT_XML = "<Event xmlns=\"urn:schemas-upnp-org:metadata-1-0/AVT/\"/>"
        
        /**
         * LastChange模式URI
         */
        private const val SCHEMA_URI = "urn:schemas-upnp-org:metadata-1-0/AVT/"
        
        // 静态初始化块，设置XML解析器安全属性
        init {
            try {
                // 禁用可能导致问题的XML解析器安全特性
                System.setProperty("jdk.xml.disallowDtd", "false")
                System.setProperty("jdk.xml.enableExtensionFunctions", "true")
                System.setProperty("javax.xml.parsers.SAXParserFactory", "org.apache.harmony.xml.parsers.SAXParserFactoryImpl")
                System.setProperty("org.xml.sax.driver", "org.apache.harmony.xml.parsers.SAXParser")
                Log.d(TAG, "已设置XML解析器安全属性")
            } catch (e: Exception) {
                Log.e(TAG, "设置XML解析器属性失败", e)
            }
        }
        
        /** 静态媒体播放器管理器 */
        private var mediaPlayerManager: MediaPlayerManager? = null
        
        /** 服务实例 */
        private var serviceInstance: MediaRendererService? = null
        
        /**
         * 设置媒体播放器管理器
         */
        fun setMediaPlayerManager(manager: MediaPlayerManager) {
            mediaPlayerManager = manager
            serviceInstance?.initializePlayerListeners()
        }
        
        /**
         * 初始化服务实例
         */
        fun initialize(context: Context) {
            // 确保在创建服务实例前已设置XML安全属性
            try {
                // 禁用可能导致问题的XML解析器安全特性
                System.setProperty("jdk.xml.disallowDtd", "false")
                System.setProperty("jdk.xml.enableExtensionFunctions", "true")
                System.setProperty("javax.xml.parsers.SAXParserFactory", "org.apache.harmony.xml.parsers.SAXParserFactoryImpl")
                System.setProperty("org.xml.sax.driver", "org.apache.harmony.xml.parsers.SAXParser")
            } catch (e: Exception) {
                Log.e(TAG, "设置XML解析器属性失败", e)
            }
            
            serviceInstance = MediaRendererService(context)
        }
        
        /**
         * 格式化时间为00:00:00格式
         * 
         * @param durationMs 毫秒时长
         * @return 格式化后的时间字符串
         */
        fun formatTime(durationMs: Int): String {
            val totalSeconds = durationMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }
        
        /**
         * 解析时间字符串
         * 
         * @param timeStr 时间字符串(HH:MM:SS格式)
         * @return 时间总秒数
         */
        fun parseTimeString(timeStr: String): Int {
            try {
                val parts = timeStr.split(":")
                if (parts.size == 3) {
                    val hours = parts[0].toInt()
                    val minutes = parts[1].toInt()
                    val seconds = parts[2].toInt()
                    return (hours * 3600 + minutes * 60 + seconds)
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析时间字符串失败: $timeStr", e)
            }
            return 0
        }
    }

    /**
     * 与输出参数对应的状态变量
     */
    @UpnpStateVariable(defaultValue = "0", sendEvents = false, name = "InstanceID")
    private var instanceId: UnsignedIntegerFourBytes = UnsignedIntegerFourBytes(0)

    @UpnpStateVariable(defaultValue = "STOPPED")
    private var currentTransportState: String = "STOPPED"
    
    @UpnpStateVariable(defaultValue = "0")
    private var currentTrack: String = "0"
    
    @UpnpStateVariable(defaultValue = "00:00:00")
    private var currentTrackDuration: String = "00:00:00"
    
    @UpnpStateVariable(defaultValue = "00:00:00")
    private var mediaDuration: String = "00:00:00"
    
    @UpnpStateVariable(defaultValue = "00:00:00")
    private var currentMediaDuration: String = "00:00:00"
    
    @UpnpStateVariable(defaultValue = "")
    private var currentURI: String = ""

    @UpnpStateVariable(defaultValue = "")
    private var currentURIMetaData: String = ""
    
    @UpnpStateVariable(defaultValue = "1")
    private var speed: String = "1"
    
    @UpnpStateVariable(defaultValue = "REL_TIME")
    private var unit: String = "REL_TIME"
    
    @UpnpStateVariable(defaultValue = "00:00:00")
    private var target: String = "00:00:00"
    
    @UpnpStateVariable(defaultValue = "00:00:00")
    private var absTime: String = "00:00:00"
    
    @UpnpStateVariable(defaultValue = "00:00:00")
    private var relTime: String = "00:00:00"

    /** 当前媒体信息 */
    private var mediaInfo: MediaInfo? = null
    /** 当前位置信息 */
    private var positionInfo: PositionInfo? = null
    /** UI线程处理器 */
    private val handler = Handler(Looper.getMainLooper())

    /**
     * 设置AV传输URI
     */
    @UpnpAction
    fun setAVTransportURI(
        @UpnpInputArgument(name = "InstanceID") instanceID: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "CurrentURI") currentURI: String,
        @UpnpInputArgument(name = "CurrentURIMetaData") currentURIMetaData: String
    ) {
        Log.d(TAG, "设置AV传输URI: $currentURI")
        Log.d(TAG, "元数据: $currentURIMetaData")
        
        this.currentURI = currentURI
        this.currentURIMetaData = currentURIMetaData
        
        // 清除之前的状态
        this.mediaDuration = "00:00:00"
        this.currentMediaDuration = "00:00:00"
        this.currentTrackDuration = "00:00:00"
        
        try {
            // 检查是否为爱奇艺链接
            if (isIqiyiStream(currentURI)) {
                Log.d(TAG, "检测到爱奇艺链接，使用特殊处理")
                handleIqiyiStream(currentURI)
            } else {
                // 使用自动播放功能设置媒体URI
                MediaRendererService.mediaPlayerManager?.setMediaURIAndPlay(currentURI)
                Log.d(TAG, "已设置媒体URI并将自动播放")
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置媒体URI失败: ${e.message}", e)
        }
    }

    /**
     * 检查是否是爱奇艺流媒体
     */
    private fun isIqiyiStream(uri: String): Boolean {
        return uri.contains("iqiyi.com") || uri.contains("qiyi.com")
    }
    
    /**
     * 处理爱奇艺流媒体
     */
    private fun handleIqiyiStream(uri: String) {
        Log.d(TAG, "开始处理爱奇艺流媒体: $uri")
        
        // 清理链接（移除特殊字符）
        val cleanedUri = uri.replace("&amp;", "&")
            .replace(" ", "%20")
            
        // 直接交给MediaPlayerManager处理，它有针对爱奇艺的特殊处理逻辑
        MediaRendererService.mediaPlayerManager?.setMediaURIAndPlay(cleanedUri)
        Log.d(TAG, "已将爱奇艺流媒体交给播放器处理: $cleanedUri")
    }

    /** 播放 */
    @UpnpAction
    fun play(
        @UpnpInputArgument(name = "InstanceID") instanceID: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Speed") speed: String
    ) {
        Log.d(TAG, "播放命令接收，速度: $speed")
        this.speed = speed
        MediaRendererService.mediaPlayerManager?.play()
    }

    /** 暂停 */
    @UpnpAction
    fun pause(
        @UpnpInputArgument(name = "InstanceID") instanceID: UnsignedIntegerFourBytes
    ) {
        Log.d(TAG, "暂停命令接收")
        MediaRendererService.mediaPlayerManager?.pause()
    }

    /** 停止 */
    @UpnpAction
    fun stop(
        @UpnpInputArgument(name = "InstanceID") instanceID: UnsignedIntegerFourBytes
    ) {
        Log.d(TAG, "停止命令接收")
        MediaRendererService.mediaPlayerManager?.stop()
    }

    /** 跳转到指定位置 */
    @UpnpAction
    fun seek(
        @UpnpInputArgument(name = "InstanceID") instanceID: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Unit") unit: String,
        @UpnpInputArgument(name = "Target") target: String
    ) {
        Log.d(TAG, "跳转命令接收: $unit, $target")
        this.unit = unit
        this.target = target
        
        if (unit == "REL_TIME" || unit == "ABS_TIME") {
            val seconds = MediaRendererService.parseTimeString(target)
            MediaRendererService.mediaPlayerManager?.seekTo(seconds * 1000)
        }
    }

    /** 获取传输信息 */
    @UpnpAction(out = [UpnpOutputArgument(name = "CurrentTransportState")])
    fun getTransportInfo(
        @UpnpInputArgument(name = "InstanceID") instanceID: UnsignedIntegerFourBytes
    ): String {
        Log.d(TAG, "获取传输信息")
        return currentTransportState
    }

    /** 获取媒体信息 */
    @UpnpAction(out = [UpnpOutputArgument(name = "CurrentURI")])
    fun getMediaInfo(
        @UpnpInputArgument(name = "InstanceID") instanceID: UnsignedIntegerFourBytes
    ): String {
        Log.d(TAG, "获取媒体信息")
        return currentURI
    }

    /** 获取位置信息 */
    @UpnpAction(
        out = [
            UpnpOutputArgument(name = "AbsTime"),
            UpnpOutputArgument(name = "RelTime"),
            UpnpOutputArgument(name = "MediaDuration")
        ]
    )
    fun getPositionInfo(
        @UpnpInputArgument(name = "InstanceID") instanceID: UnsignedIntegerFourBytes
    ): Array<String> {
        val currentPosition = MediaRendererService.mediaPlayerManager?.getCurrentPosition() ?: 0
        val formattedPosition = MediaRendererService.formatTime(currentPosition)
        
        this.absTime = formattedPosition
        this.relTime = formattedPosition
        
        Log.d(TAG, "获取位置信息: $formattedPosition / $mediaDuration")
        return arrayOf(absTime, relTime, mediaDuration)
    }

    /**
     * 更新媒体时长
     */
    fun updateMediaDuration(durationMs: Long) {
        // 更新媒体持续时间
        mediaDuration = durationMs.toString()
        val formatted = MediaRendererService.formatTime(durationMs.toInt())
        currentMediaDuration = formatted
        currentTrackDuration = formatted
        
        // 发送持续时间变化通知
        try {
            val eventedValue = AVTransportVariable.CurrentTrackDuration(formatted)
            avTransportLastChange.setEventedValue(
                UnsignedIntegerFourBytes(0),
                eventedValue
            )
            Log.d(TAG, "已更新媒体持续时间: $formatted")
        } catch (e: Exception) {
            Log.e(TAG, "更新媒体持续时间通知失败", e)
        }
    }
    
    /**
     * 更新媒体播放位置
     */
    fun updateMediaPosition(positionMs: Long) {
        // 更新当前播放位置
        val formatted = MediaRendererService.formatTime(positionMs.toInt())
        relTime = formatted
        
        try {
            if (positionMs % 5000 < 1000) { // 仅在每5秒左右发送一次
                val eventedValue = AVTransportVariable.RelativeTimePosition(formatted)
                avTransportLastChange.setEventedValue(
                    UnsignedIntegerFourBytes(0),
                    eventedValue
                )
                Log.d(TAG, "已更新媒体位置: $formatted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新媒体位置通知失败", e)
        }
    }
    
    /**
     * 更新传输状态
     */
    fun updateTransportState(state: TransportState) {
        // 更新播放状态
        currentTransportState = state.value
        
        // 发送状态变化通知
        try {
            val eventedValue = AVTransportVariable.TransportState(state)
            avTransportLastChange.setEventedValue(
                UnsignedIntegerFourBytes(0),
                eventedValue
            )
            Log.d(TAG, "已更新传输状态: ${state.value}")
        } catch (e: Exception) {
            Log.e(TAG, "更新传输状态通知失败", e)
        }
    }

    /**
     * 设置媒体播放器管理器
     * 
     * @param manager MediaPlayerManager实例
     */
    fun setMediaPlayerManager(manager: MediaPlayerManager) {
        Log.d(TAG, "设置MediaPlayerManager")
        mediaPlayerManager = manager
        initializePlayerListeners()
    }

    /**
     * 初始化播放器监听器
     */
    fun initializePlayerListeners() {
        MediaRendererService.mediaPlayerManager?.setStateListener(object : MediaPlayerManager.MediaStateListener {
            override fun onPrepared(durationMs: Int) {
                Log.d(TAG, "媒体准备完成，时长: $durationMs ms")
                val durationFormatted = MediaRendererService.formatTime(durationMs)
                
                // 使用Handler确保在主线程更新
                Handler(Looper.getMainLooper()).post {
                    updateMediaDuration(durationMs.toLong())
                }
            }
            
            override fun onProgressUpdate(positionMs: Int) {
                // 不需要日志记录每次进度更新
                Handler(Looper.getMainLooper()).post {
                    updateMediaPosition(positionMs.toLong())
                }
            }
            
            override fun onPlaybackStateChanged(state: MediaPlayerManager.PlaybackState) {
                Log.d(TAG, "播放状态更新为: $state")
                
                // 使用Handler确保在主线程更新
                Handler(Looper.getMainLooper()).post {
                    when (state) {
                        MediaPlayerManager.PlaybackState.PLAYING -> updateTransportState(TransportState.PLAYING)
                        MediaPlayerManager.PlaybackState.PAUSED -> updateTransportState(TransportState.PAUSED_PLAYBACK)
                        MediaPlayerManager.PlaybackState.STOPPED -> updateTransportState(TransportState.STOPPED)
                        MediaPlayerManager.PlaybackState.ERROR -> {
                            updateTransportState(TransportState.STOPPED)
                            Log.e(TAG, "播放错误，状态更新为停止")
                        }
                    }
                }
            }
            
            override fun onPlaybackCompleted() {
                Log.d(TAG, "播放完成")
                
                // 使用Handler确保在主线程更新
                Handler(Looper.getMainLooper()).post {
                    updateTransportState(TransportState.STOPPED)
                }
            }
            
            override fun onError(errorMsg: String) {
                Log.e(TAG, "播放错误: $errorMsg")
                
                // 使用Handler确保在主线程更新
                Handler(Looper.getMainLooper()).post {
                    updateTransportState(TransportState.STOPPED)
                }
            }
        })
    }
} 