package com.example.dlna

import android.content.Context
import android.content.Intent
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
import org.fourthline.cling.support.model.TransportState
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.lang.ref.WeakReference

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
class MediaRendererService {

    init {
        // 在初始化时保存实例引用
        serviceInstance = WeakReference(this)
        Log.d(TAG, getString(R.string.log_media_renderer_service_created))
    }

    companion object {
        private const val TAG = "MediaRendererService"

        /** 应用上下文引用 - 使用applicationContext防止泄漏 */
        private var appContext: WeakReference<Context>? = null

        /** 服务实例引用 */
        private var serviceInstance: WeakReference<MediaRendererService>? = null

        /** 状态变更对象引用 */
        private var lastChange: LastChange? = null

        /** 当前媒体播放器管理器 - 使用弱引用避免内存泄漏 */
        private var mediaPlayerManagerRef: WeakReference<MediaPlayerManager>? = null

        /** 当前活动的播放界面 - 使用弱引用避免内存泄漏 */
        private var playerActivityRef: WeakReference<VideoPlayerActivity>? = null

        /** 主线程Handler */
        private val mainHandler = Handler(Looper.getMainLooper())

        /** 实例ID常量 */
        private val INSTANCE_ID = UnsignedIntegerFourBytes(0)

        /**
         * 获取Context
         *
         * @return applicationContext或null
         */
        private fun getContext(): Context? {
            return appContext?.get()
        }

        /**
         * 获取字符串资源
         */
        private fun getString(resId: Int): String {
            return getContext()?.getString(resId) ?: ""
        }

        /**
         * 获取格式化字符串资源
         */
        private fun getString(resId: Int, vararg formatArgs: Any): String {
            return getContext()?.getString(resId, *formatArgs) ?: ""
        }

        /**
         * 初始化服务
         *
         * 设置应用上下文并初始化媒体播放器
         *
         * @param context 应用上下文
         */
        fun initialize(context: Context) {
            appContext = WeakReference(context.applicationContext)
            Log.d(TAG, getString(R.string.log_media_renderer_service_initialized))

            try {
                // 初始化LastChange - 使用自定义的解析器实现来避免SAX特性不兼容问题
                lastChange = LastChange(CustomAVTransportLastChangeParser())

                // 开始状态更新定时器
                startStatusUpdateTimer()
            } catch (e: Exception) {
                Log.e(TAG, getString(R.string.log_lastchange_init_failed), e)
            }
        }

        /**
         * 设置媒体播放器管理器
         *
         * @param manager 媒体播放器管理器实例
         */
        fun setMediaPlayerManager(manager: MediaPlayerManager) {
            mediaPlayerManagerRef = WeakReference(manager)
            Log.d(TAG, getString(R.string.log_media_player_manager_set))
        }

        /**
         * 获取媒体播放器管理器
         *
         * @return 媒体播放器管理器或null
         */
        fun getMediaPlayerManager(): MediaPlayerManager? {
            return mediaPlayerManagerRef?.get()
        }

        /**
         * 设置当前播放Activity
         *
         * @param activity 视频播放Activity实例
         */
        fun setPlayerActivity(activity: VideoPlayerActivity) {
            playerActivityRef = WeakReference(activity)
            Log.d(TAG, getString(R.string.log_current_player_activity_set))
        }

        /**
         * 获取当前播放Activity
         *
         * @return 播放Activity或null
         */
        fun getPlayerActivity(): VideoPlayerActivity? {
            return playerActivityRef?.get()
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
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        }

        /**
         * 解析时间字符串
         *
         * @param timeStr 时间字符串(HH:MM:SS格式)
         * @return 时间总毫秒数
         */
        fun parseTimeString(timeStr: String): Int {
            try {
                val parts = timeStr.split(":")
                if (parts.size == 3) {
                    val hours = parts[0].toInt()
                    val minutes = parts[1].toInt()
                    val seconds = parts[2].toInt()
                    return (hours * 3600 + minutes * 60 + seconds) * 1000
                }
            } catch (e: Exception) {
                Log.e(TAG, getString(R.string.log_parse_time_string_failed, timeStr), e)
            }
            return 0
        }

        // 状态更新定时器
        private var statusUpdateTimer: Timer? = null

        /**
         * 开始状态更新定时器
         * 用于定期更新播放状态和进度信息
         */
        private fun startStatusUpdateTimer() {
            statusUpdateTimer?.cancel()
            statusUpdateTimer = Timer().apply {
                schedule(object : TimerTask() {
                    override fun run() {
                        updatePlaybackStatus()
                    }
                }, 0, 1000) // 每秒更新一次
            }
        }

        /**
         * 更新播放状态和进度
         */
        private fun updatePlaybackStatus() {
            try {
                val service = serviceInstance?.get() ?: return
                val player = mediaPlayerManagerRef?.get() ?: return

                // 使用Handler确保在主线程中获取播放状态
                mainHandler.post {
                    // 更新播放状态
                    val state = when (player.getCurrentState()) {
                        MediaPlayerManager.PlaybackState.PLAYING -> {
                            service.currentTransportState = "PLAYING"
                            TransportState.PLAYING
                        }

                        MediaPlayerManager.PlaybackState.PAUSED -> {
                            service.currentTransportState = "PAUSED_PLAYBACK"
                            TransportState.PAUSED_PLAYBACK
                        }

                        MediaPlayerManager.PlaybackState.STOPPED -> {
                            service.currentTransportState = "STOPPED"
                            TransportState.STOPPED
                        }

                        MediaPlayerManager.PlaybackState.ERROR -> {
                            service.currentTransportState = "ERROR"
                            TransportState.STOPPED
                        }

                        else -> TransportState.STOPPED
                    }

                    // 更新LastChange状态
                    try {
                        lastChange?.let { lc ->
                            service.lastChange = lc.toString()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, getString(R.string.log_update_lastchange_status_error), e)
                    }

                    // 更新进度信息
                    val position = player.getCurrentPosition()
                    val duration = player.getDuration()

                    if (duration > 0) {
                        val formattedDuration = formatTime(duration)
                        service.mediaDuration = formattedDuration
                        service.currentMediaDuration = formattedDuration
                        service.currentTrackDuration = formattedDuration

                        val formattedPosition = formatTime(position)
                        service.absTime = formattedPosition
                        service.relTime = formattedPosition

                        Log.d(
                            TAG,
                            getString(
                                R.string.log_playback_position_update,
                                formattedPosition,
                                formattedDuration
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, getString(R.string.log_update_playback_status_error), e)
            }
        }

        /**
         * 自定义的LastChange解析器类，用于解决Android上的XML解析器兼容性问题
         */
        private class CustomAVTransportLastChangeParser : AVTransportLastChangeParser() {
            // 禁用架构验证
            override fun getSchemaSources() = null

            // 重写create方法完全替换原始SAXParser中的实现，避免设置不兼容的XML解析器特性
            override fun create(): org.xml.sax.XMLReader {
                try {
                    val factory = javax.xml.parsers.SAXParserFactory.newInstance()
                    factory.isNamespaceAware = true

                    // 不尝试设置不兼容的特性http://apache.org/xml/features/disallow-doctype-decl
                    // 但仍设置其他重要安全特性（如果支持）
                    try {
                        // 禁用外部实体处理，防止XXE攻击
                        factory.setFeature(
                            "http://xml.org/sax/features/external-general-entities",
                            false
                        )
                        factory.setFeature(
                            "http://xml.org/sax/features/external-parameter-entities",
                            false
                        )
                    } catch (e: Exception) {
                        // 如果特性不被支持，记录警告但继续执行
                        Log.w(TAG, "XML解析器不支持禁用外部实体特性: ${e.message}")
                    }

                    // 创建XMLReader而不设置不兼容的特性
                    val reader = factory.newSAXParser().xmlReader

                    // 设置XMLReader的特性（如果支持）
                    try {
                        reader.setFeature(
                            "http://xml.org/sax/features/external-general-entities",
                            false
                        )
                        reader.setFeature(
                            "http://xml.org/sax/features/external-parameter-entities",
                            false
                        )
                    } catch (e: Exception) {
                        // 如果特性不被支持，记录警告但继续执行
                        Log.w(TAG, "XMLReader不支持禁用外部实体特性: ${e.message}")
                    }

                    return reader
                } catch (e: Exception) {
                    // 如果创建失败，记录错误并抛出运行时异常
                    Log.e(TAG, "创建XMLReader失败", e)
                    throw RuntimeException("创建XMLReader失败", e)
                }
            }
        }
    }

    /**
     * 与输出参数对应的状态变量
     */
    @UpnpStateVariable(defaultValue = "0", sendEvents = false, name = "InstanceID")
    private var instanceId: UnsignedIntegerFourBytes? = null

    @UpnpStateVariable(sendEvents = true)
    private var lastChange = ""

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

    /**
     * 设置URI动作
     *
     * 设置要播放的媒体资源URI
     *
     * @param instanceId 实例ID
     * @param uri 媒体资源URI
     * @param metadata 媒体资源元数据
     */
    @UpnpAction
    fun setAVTransportURI(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "CurrentURI") uri: String,
        @UpnpInputArgument(name = "CurrentURIMetaData") metadata: String
    ) {
        Log.d(TAG, "接收到设置URI请求: $uri")

        this.instanceId = instanceId
        this.currentURI = uri
        this.currentURIMetaData = metadata

        // 重置播放状态
        this.currentTransportState = "STOPPED"
    }

    /**
     * 播放动作
     *
     * 开始播放当前设置的媒体
     *
     * @param instanceId 实例ID
     * @param speed 播放速度
     */
    @UpnpAction
    fun play(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Speed") speed: String
    ) {
        Log.d(TAG, getString(R.string.log_play_request, speed, currentURI))
        this.speed = speed

        // 更新状态
        this.currentTransportState = "PLAYING"

        // 使用Handler确保在主线程上调用播放
        Handler(Looper.getMainLooper()).post {
            try {
                // 如果尚未打开播放页面，则启动视频播放页面
                if (currentURI.isNotEmpty()) {
                    appContext?.let { context ->
                        getContext()?.let { VideoPlayerActivity.start(it, currentURI) }
                        Log.d(TAG, "播放时启动视频播放页面")
                    }
                } else {
                    val player = getMediaPlayerManager()
                    val activity = getPlayerActivity()

                    if (player != null) {
                        // 如果URI为空但已经有播放器实例，尝试通过现有播放器播放
                        Log.d(TAG, getString(R.string.log_no_uri_try_existing_player))
                        player.play()
                        activity?.playFromDLNA()
                    } else {
                        Log.e(TAG, getString(R.string.log_cannot_play_no_uri_no_player))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, getString(R.string.log_play_failed, e.message ?: ""), e)
            }
        }
    }

    /**
     * 暂停动作
     *
     * 暂停当前播放的媒体
     *
     * @param instanceId 实例ID
     */
    @UpnpAction
    fun pause(@UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes) {
        Log.d(TAG, "接收到暂停请求")

        // 更新状态
        currentTransportState = "PAUSED_PLAYBACK"

        // 在主线程上暂停播放
        Handler(Looper.getMainLooper()).post {
            mediaPlayerManagerRef?.get()?.pause()
        }
    }

    /**
     * 停止动作
     *
     * 停止当前播放的媒体
     *
     * @param instanceId 实例ID
     */
    @UpnpAction
    fun stop(@UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes) {
        Log.d(TAG, "接收到停止请求")
        // 更新状态
        currentTransportState = "STOPPED"

        // 在主线程上停止播放
        Handler(Looper.getMainLooper()).post {
            mediaPlayerManagerRef?.get()?.stop()
        }
    }


    /**
     * 跳转动作
     *
     * 将播放位置跳转到指定时间
     *
     * @param instanceId 实例ID
     * @param unit 时间单位
     * @param target 目标位置
     */
    @UpnpAction
    fun seek(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Unit") unit: String,
        @UpnpInputArgument(name = "Target") target: String
    ) {
        Log.d(TAG, "接收到跳转请求: 单位=$unit, 目标=$target")

        this.unit = unit
        this.target = target

        if (unit == "REL_TIME" || unit == "ABS_TIME") {
            val timeMs = parseTimeString(target)

            // 在主线程上执行跳转
            Handler(Looper.getMainLooper()).post {
                mediaPlayerManagerRef?.get()?.seekTo(timeMs)
            }

            Log.d(TAG, "已跳转到 ${timeMs / 1000}秒")
        }
    }

    /** 获取传输信息 */
    @UpnpAction(out = [UpnpOutputArgument(name = "CurrentTransportState")])
    fun getTransportInfo(@UpnpInputArgument(name = "InstanceID") instanceID: UnsignedIntegerFourBytes): String {
        return currentTransportState
    }

    /** 获取媒体信息 */
    @UpnpAction(out = [UpnpOutputArgument(name = "CurrentURI")])
    fun getMediaInfo(@UpnpInputArgument(name = "InstanceID") instanceID: UnsignedIntegerFourBytes): String {
        return currentURI
    }

    /** 获取位置信息 */
    @UpnpAction(
        out = [UpnpOutputArgument(name = "AbsTime"), UpnpOutputArgument(name = "RelTime"), UpnpOutputArgument(
            name = "MediaDuration"
        )]
    )
    fun getPositionInfo(
        @UpnpInputArgument(name = "InstanceID") instanceID: UnsignedIntegerFourBytes
    ): Array<String> {
        // 直接返回缓存的时间值，避免线程问题
        // 实际播放位置由updatePlaybackStatus方法定期更新
        Log.d(TAG, "获取位置信息: $absTime / $mediaDuration")
        return arrayOf(absTime, relTime, mediaDuration)
    }
} 