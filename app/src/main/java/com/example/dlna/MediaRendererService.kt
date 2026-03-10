package com.example.dlna

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.max.videoplayer.MediaPlayerManager
import org.fourthline.cling.binding.annotations.UpnpAction
import org.fourthline.cling.binding.annotations.UpnpInputArgument
import org.fourthline.cling.binding.annotations.UpnpOutputArgument
import org.fourthline.cling.binding.annotations.UpnpService
import org.fourthline.cling.binding.annotations.UpnpServiceId
import org.fourthline.cling.binding.annotations.UpnpServiceType
import org.fourthline.cling.binding.annotations.UpnpStateVariable
import org.fourthline.cling.model.types.UnsignedIntegerFourBytes
import org.fourthline.cling.support.avtransport.lastchange.AVTransportVariable
import org.fourthline.cling.support.lastchange.LastChange
import org.fourthline.cling.support.lastchange.LastChangeDelegator
import org.fourthline.cling.support.model.TransportState
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

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
class MediaRendererService : LastChangeDelegator {

    /** PropertyChangeSupport用于Cling框架的GENA事件通知机制 */
    private val propertyChangeSupport = java.beans.PropertyChangeSupport(this)

    /** LastChange管理器，用于累积AVTransport状态变化并推送给订阅者 */
    private val lastChange = LastChange(CustomAVTransportLastChangeParser.INSTANCE)

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

        /** 当前媒体播放器管理器 - 使用弱引用避免内存泄漏 */
        private var mediaPlayerManagerRef: WeakReference<MediaPlayerManager>? = null

        /** 当前活动的播放界面 - 使用弱引用避免内存泄漏 */
        private var playerActivityRef: WeakReference<VideoPlayerActivity>? = null

        /** 主线程Handler */
        private val mainHandler = Handler(Looper.getMainLooper())

        /** 实例ID常量 */
        private val INSTANCE_ID = UnsignedIntegerFourBytes(0)

        /**
         * 获取应用上下文，用于 getString 等。
         *
         * @return applicationContext，未初始化时为 null
         */
        private fun getContext(): Context? {
            return appContext?.get()
        }

        /**
         * 获取字符串资源。
         *
         * @param resId 字符串资源 id
         * @return 对应文案，无 Context 时返回空串
         */
        private fun getString(resId: Int): String {
            return getContext()?.getString(resId) ?: ""
        }

        /**
         * 获取带占位符的字符串资源。
         *
         * @param resId      字符串资源 id
         * @param formatArgs 占位符参数
         * @return 格式化后的文案
         */
        private fun getString(resId: Int, vararg formatArgs: Any): String {
            return getContext()?.getString(resId, *formatArgs) ?: ""
        }

        /**
         * 初始化服务：设置应用上下文并启动状态更新定时器。
         *
         * @param context 应用上下文（建议 ApplicationContext）
         * 无返回值。
         */
        fun initialize(context: Context) {
            appContext = WeakReference(context.applicationContext)
            Log.d(TAG, getString(R.string.log_media_renderer_service_initialized))

            try {
                // 开始状态更新定时器
                startStatusUpdateTimer()
            } catch (e: Exception) {
                Log.e(TAG, getString(R.string.log_lastchange_init_failed), e)
            }
        }

        /**
         * 设置媒体播放器管理器，供播放/暂停/停止/跳转等动作使用。
         *
         * @param manager 媒体播放器管理器实例
         * 无返回值。
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
         * 设置当前播放 Activity，供 play 等动作启动或联动界面。
         *
         * @param activity 视频播放 Activity 实例
         * 无返回值。
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
         * 启动状态更新定时器，每秒从播放器拉取状态与进度并写入 UPnP 状态变量。
         * 无参数，无返回值。
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
         * 在主线程从播放器读取状态与进度，更新 currentTransportState、relativeTimePosition 等状态变量。
         * 无参数，无返回值。
         */
        private fun updatePlaybackStatus() {
            try {
                val service = serviceInstance?.get() ?: return
                val player = mediaPlayerManagerRef?.get() ?: return

                // 使用Handler确保在主线程中获取播放状态
                mainHandler.post {
                    // 更新播放状态
                    when (player.getCurrentState()) {
                        MediaPlayerManager.PlaybackState.PLAYING -> {
                            service.currentTransportState = "PLAYING"
                        }
                        MediaPlayerManager.PlaybackState.PAUSED -> {
                            service.currentTransportState = "PAUSED_PLAYBACK"
                        }
                        MediaPlayerManager.PlaybackState.STOPPED -> {
                            service.currentTransportState = "STOPPED"
                        }
                        MediaPlayerManager.PlaybackState.ERROR -> {
                            service.currentTransportState = "ERROR"
                        }
                        else -> {
                            service.currentTransportState = "STOPPED"
                        }
                    }

                    // 更新进度信息
                    val position = player.getCurrentPosition()
                    val duration = player.getDuration()

                    if (duration > 0) {
                        val formattedDuration = formatTime(duration)
                        val formattedPosition = formatTime(position)

                        // 更新时长状态变量（供GetPositionInfo/GetMediaInfo返回）
                        service.currentMediaDuration = formattedDuration
                        service.currentTrackDuration = formattedDuration

                        // 更新播放位置状态变量（供GetPositionInfo返回）⭐ 核心：爱奇艺轮询读取这里
                        service.relativeTimePosition = formattedPosition
                        service.absoluteTimePosition = formattedPosition
                        service.currentTrackURI = service.avTransportURI

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
    }

    /**
     * 与输出参数对应的状态变量
     */
    @UpnpStateVariable(defaultValue = "0", sendEvents = false, name = "InstanceID")
    private var instanceId: UnsignedIntegerFourBytes? = null

    /** LastChange事件状态变量（UPnP规范要求），用于GENA事件推送 */
    @UpnpStateVariable(sendEvents = true, eventMaximumRateMilliseconds = 200)
    private var lastChangeValue = ""

    /** 当前传输状态（STOPPED/PLAYING/PAUSED_PLAYBACK等） */
    @UpnpStateVariable(defaultValue = "STOPPED", name = "TransportState")
    private var currentTransportState: String = "STOPPED"

    @UpnpStateVariable(defaultValue = "OK", name = "TransportStatus")
    private var currentTransportStatus: String = "OK"

    /** 播放速度（通常为"1"） */
    @UpnpStateVariable(defaultValue = "1", name = "TransportPlaySpeed")
    private var currentTransportPlaySpeed: String = "1"

    /** 轨道总数（单视频播放时为0或1） */
    @UpnpStateVariable(defaultValue = "0", datatype = "ui4", name = "NumberOfTracks")
    private var numberOfTracks: UnsignedIntegerFourBytes = UnsignedIntegerFourBytes(0)

    /** 下一个待播放URI（连续播放场景，当前未使用） */
    @UpnpStateVariable(defaultValue = "", name = "NextAVTransportURI")
    private var nextAVTransportURI: String = ""

    /** 下一个待播放URI的元数据（当前未使用） */
    @UpnpStateVariable(defaultValue = "", name = "NextAVTransportURIMetaData")
    private var nextAVTransportURIMetaData: String = ""

    /** 播放存储介质（网络流媒体） */
    @UpnpStateVariable(defaultValue = "NETWORK", name = "PlaybackStorageMedium")
    private var playbackStorageMedium: String = "NETWORK"

    /** 录制存储介质（渲染器不支持录制） */
    @UpnpStateVariable(defaultValue = "NOT_IMPLEMENTED", name = "RecordStorageMedium")
    private var recordStorageMedium: String = "NOT_IMPLEMENTED"

    /** 录制介质写入状态（渲染器不支持录制） */
    @UpnpStateVariable(defaultValue = "NOT_IMPLEMENTED", name = "RecordMediumWriteStatus")
    private var recordMediumWriteStatus: String = "NOT_IMPLEMENTED"

    /** 当前轨道编号（通常为0） */
    @UpnpStateVariable(defaultValue = "0", datatype = "ui4", name = "CurrentTrack")
    private var currentTrack: UnsignedIntegerFourBytes = UnsignedIntegerFourBytes(0)

    /** 当前轨道时长（HH:MM:SS格式） */
    @UpnpStateVariable(defaultValue = "00:00:00", name = "CurrentTrackDuration")
    private var currentTrackDuration: String = "00:00:00"

    /** 当前媒体总时长（HH:MM:SS格式） */
    @UpnpStateVariable(defaultValue = "00:00:00", name = "CurrentMediaDuration")
    private var currentMediaDuration: String = "00:00:00"

    /** 当前播放URI */
    @UpnpStateVariable(defaultValue = "", name = "AVTransportURI")
    private var avTransportURI: String = ""

    /** 当前URI的元数据（DIDL-Lite XML格式） */
    @UpnpStateVariable(defaultValue = "", name = "AVTransportURIMetaData")
    private var avTransportURIMetaData: String = ""

    /** 当前轨道URI */
    @UpnpStateVariable(defaultValue = "", name = "CurrentTrackURI")
    private var currentTrackURI: String = ""

    /** 当前轨道元数据（DIDL-Lite XML格式） */
    @UpnpStateVariable(defaultValue = "NOT_IMPLEMENTED", name = "CurrentTrackMetaData")
    private var currentTrackMetaData: String = "NOT_IMPLEMENTED"

    /** 相对时间位置/播放进度（HH:MM:SS格式）⭐ 核心变量 */
    @UpnpStateVariable(defaultValue = "00:00:00", name = "RelativeTimePosition")
    private var relativeTimePosition: String = "00:00:00"

    /** 绝对时间位置（HH:MM:SS格式，通常与RelativeTimePosition相同） */
    @UpnpStateVariable(defaultValue = "00:00:00", name = "AbsoluteTimePosition")
    private var absoluteTimePosition: String = "00:00:00"

    /** 相对计数位置（不常用，设为最大值表示不支持） */
    @UpnpStateVariable(defaultValue = "2147483647", datatype = "i4", name = "RelativeCounterPosition")
    private var relativeCounterPosition: Int = Integer.MAX_VALUE

    /** 绝对计数位置（不常用，设为最大值表示不支持） */
    @UpnpStateVariable(defaultValue = "2147483647", datatype = "i4", name = "AbsoluteCounterPosition")
    private var absoluteCounterPosition: Int = Integer.MAX_VALUE

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // A_ARG_TYPE 参数类型变量（UPnP规范要求，用于动作参数类型声明）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // ━━━ A_ARG_TYPE 参数类型（用于 Seek 等动作的输入参数声明）
    @UpnpStateVariable(sendEvents = false, name = "A_ARG_TYPE_SeekMode")
    private var argTypeSeekMode: String = "REL_TIME"

    @UpnpStateVariable(sendEvents = false, name = "A_ARG_TYPE_SeekTarget")
    private var argTypeSeekTarget: String = "00:00:00"

    @UpnpStateVariable(sendEvents = false, name = "A_ARG_TYPE_InstanceID", datatype = "ui4")
    private var argTypeInstanceID: UnsignedIntegerFourBytes = UnsignedIntegerFourBytes(0)

    /**
     * 获取 PropertyChangeSupport 实例。Cling 的 DefaultServiceManager 通过反射调用，
     * 用于管理 UPnP 事件订阅与通知（GENA 协议）。
     *
     * @return 本服务的 PropertyChangeSupport 实例
     */
    fun getPropertyChangeSupport() = propertyChangeSupport

    /**
     * 获取 LastChange 对象（LastChangeDelegator 接口要求）。
     * LastChangeAwareServiceManager 调用此方法获取 LastChange，用于触发 GENA 事件推送。
     *
     * @return 本服务的 LastChange 实例
     */
    override fun getLastChange() = lastChange

    /**
     * 为新订阅者提供初始状态（LastChangeDelegator 接口要求）。
     * 当控制点（如爱奇艺）订阅 AVTransport 服务时，需要返回当前完整状态；
     * LastChangeAwareServiceManager 会调用此方法生成初始事件通知。
     *
     * @param lc LastChange 实例，用于写入当前状态
     * @param instanceId 实例 ID（本实现固定为 0）
     * 无返回值。
     */
    override fun appendCurrentState(lc: LastChange, instanceId: UnsignedIntegerFourBytes) {
        try {
            val player = getMediaPlayerManager() ?: return

            // 获取当前播放状态
            val state = when (player.getCurrentState()) {
                MediaPlayerManager.PlaybackState.PLAYING -> TransportState.PLAYING
                MediaPlayerManager.PlaybackState.PAUSED -> TransportState.PAUSED_PLAYBACK
                MediaPlayerManager.PlaybackState.STOPPED -> TransportState.STOPPED
                else -> TransportState.STOPPED
            }

            // 获取当前播放进度
            val position = player.getCurrentPosition()
            val duration = player.getDuration()
            val formattedPosition = formatTime(position)
            val formattedDuration = if (duration > 0) formatTime(duration) else "00:00:00"

            // 添加所有重要状态到LastChange
            lc.setEventedValue(
                instanceId,
                AVTransportVariable.TransportState(state),
                AVTransportVariable.RelativeTimePosition(formattedPosition),
                AVTransportVariable.AbsoluteTimePosition(formattedPosition),
                AVTransportVariable.CurrentTrackDuration(formattedDuration),
                AVTransportVariable.CurrentMediaDuration(formattedDuration)
            )
        } catch (e: Exception) {
            Log.e(TAG, getString(R.string.log_append_current_state_failed), e)
        }
    }

    /**
     * 返回当前活动的实例 ID 列表（LastChangeDelegator 接口要求）。
     * 本实现只支持单实例（ID=0），即一次仅播放一个视频。
     *
     * @return 包含 INSTANCE_ID(0) 的数组
     */
    override fun getCurrentInstanceIds() = arrayOf(INSTANCE_ID)

    /**
     * 设置要播放的媒体 URI（UPnP SetAVTransportURI）。保存 URI/元数据并重置播放状态。
     *
     * @param instanceId 实例 ID（通常为 0）
     * @param uri        媒体资源 URI（如 M3U8/MP4 地址）
     * @param metadata   媒体元数据（DIDL-Lite 等，可空）
     * 无返回值。
     */
    @UpnpAction
    fun setAVTransportURI(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "CurrentURI", stateVariable = "AVTransportURI") uri: String,
        @UpnpInputArgument(name = "CurrentURIMetaData", stateVariable = "AVTransportURIMetaData") metadata: String
    ) {
        Log.d(TAG, getString(R.string.log_set_uri_request, uri))

        // 保存实例ID（通常为0）
        this.instanceId = instanceId
        this.avTransportURI = uri
        this.avTransportURIMetaData = metadata
        this.currentTrackURI = uri
        this.currentTrackMetaData = metadata

        // 重置播放状态
        this.currentTransportState = "STOPPED"
    }

    /**
     * 播放动作（UPnP Play）。若有 URI 则启动 VideoPlayerActivity，否则尝试使用已有播放器继续播放。
     *
     * @param _instanceId 实例 ID（未使用）
     * @param speed       播放速度（如 "1"）
     * 无返回值。
     */
    @UpnpAction
    fun play(
        @UpnpInputArgument(name = "InstanceID") _instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Speed", stateVariable = "TransportPlaySpeed") speed: String
    ) {
        Log.d(TAG, getString(R.string.log_play_request, speed, avTransportURI))
        this.currentTransportPlaySpeed = speed

        if (currentTransportState == "PAUSED_PLAYBACK") {
            mainHandler.post {
                getMediaPlayerManager()?.play()
            }
        }

        // 更新状态
        this.currentTransportState = "PLAYING"

        // 使用 mainHandler 确保在主线程上调用播放
        mainHandler.post {
            try {
                // 如果尚未打开播放页面，则启动视频播放页面
                if (avTransportURI.isNotEmpty()) {
                    appContext?.let { context ->
                        getContext()?.let { VideoPlayerActivity.start(it, avTransportURI) }
                        Log.d(TAG, getString(R.string.log_start_video_player_on_play))
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
     * 暂停动作（UPnP Pause）。在主线程调用播放器 pause。
     *
     * @param _instanceId 实例 ID（未使用）
     * 无返回值。
     */
    @UpnpAction
    fun pause(@UpnpInputArgument(name = "InstanceID") _instanceId: UnsignedIntegerFourBytes) {
        Log.d(TAG, getString(R.string.log_pause_request))

        // 更新状态
        currentTransportState = "PAUSED_PLAYBACK"

        // 在主线程上暂停播放
        mainHandler.post {
            mediaPlayerManagerRef?.get()?.pause()
        }
    }

    /**
     * 停止动作（UPnP Stop）。在主线程调用播放器 stop。
     *
     * @param _instanceId 实例 ID（未使用）
     * 无返回值。
     */
    @UpnpAction
    fun stop(@UpnpInputArgument(name = "InstanceID") _instanceId: UnsignedIntegerFourBytes) {
        Log.d(TAG, getString(R.string.log_stop_request))
        // 更新状态
        currentTransportState = "STOPPED"

        // 在主线程上停止播放
        mainHandler.post {
            mediaPlayerManagerRef?.get()?.stop()
        }
    }


    /**
     * 跳转动作（UPnP Seek）。支持 REL_TIME/ABS_TIME，将目标时间字符串解析为毫秒后下发播放器。
     *
     * @param _instanceId 实例 ID（未使用）
     * @param unit        时间单位（如 "REL_TIME"、"ABS_TIME"）
     * @param target      目标位置，HH:MM:SS 格式
     * 无返回值。
     */
    @UpnpAction
    fun seek(
        @UpnpInputArgument(name = "InstanceID") _instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Unit", stateVariable = "A_ARG_TYPE_SeekMode") unit: String,
        @UpnpInputArgument(name = "Target", stateVariable = "A_ARG_TYPE_SeekTarget") target: String
    ) {
        Log.d(TAG, getString(R.string.log_seek_request, unit, target))

        if (unit == "REL_TIME" || unit == "ABS_TIME") {
            val timeMs = parseTimeString(target)

            // 在主线程上执行跳转
            mainHandler.post {
                mediaPlayerManagerRef?.get()?.seekTo(timeMs)
            }

            Log.d(TAG, getString(R.string.log_seek_completed, timeMs / 1000))
        }
    }

    /**
     * 获取传输信息（UPnP AVTransport:1 GetTransportInfo）。
     * 返回当前传输状态、状态码与播放速度。
     *
     * @param _instanceID 实例 ID（未使用，本实现单实例）
     * @return [org.fourthline.cling.support.model.TransportInfo] 包含状态、状态码、速度
     */
    @UpnpAction(out = [
        UpnpOutputArgument(name = "CurrentTransportState", stateVariable = "TransportState", getterName = "getCurrentTransportState"),
        UpnpOutputArgument(name = "CurrentTransportStatus", stateVariable = "TransportStatus", getterName = "getCurrentTransportStatus"),
        UpnpOutputArgument(name = "CurrentSpeed", stateVariable = "TransportPlaySpeed", getterName = "getCurrentSpeed")
    ])
    fun getTransportInfo(
        @UpnpInputArgument(name = "InstanceID") _instanceID: UnsignedIntegerFourBytes
    ): org.fourthline.cling.support.model.TransportInfo {
        return org.fourthline.cling.support.model.TransportInfo(
            org.fourthline.cling.support.model.TransportState.valueOf(currentTransportState),
            org.fourthline.cling.support.model.TransportStatus.OK,
            currentTransportPlaySpeed
        )
    }

    /**
     * 获取媒体信息（UPnP AVTransport:1 GetMediaInfo）。
     * 返回当前 URI 与元数据（轨道数、时长等由状态变量提供）。
     *
     * @param _instanceID 实例 ID（未使用）
     * @return [org.fourthline.cling.support.model.MediaInfo] 当前 URI 与元数据
     */
    @UpnpAction(out = [
        UpnpOutputArgument(name = "NrTracks", stateVariable = "NumberOfTracks", getterName = "getNumberOfTracks"),
        UpnpOutputArgument(name = "MediaDuration", stateVariable = "CurrentMediaDuration", getterName = "getMediaDuration"),
        UpnpOutputArgument(name = "CurrentURI", stateVariable = "AVTransportURI", getterName = "getCurrentURI"),
        UpnpOutputArgument(name = "CurrentURIMetaData", stateVariable = "AVTransportURIMetaData", getterName = "getCurrentURIMetaData"),
        UpnpOutputArgument(name = "NextURI", stateVariable = "NextAVTransportURI", getterName = "getNextURI"),
        UpnpOutputArgument(name = "NextURIMetaData", stateVariable = "NextAVTransportURIMetaData", getterName = "getNextURIMetaData"),
        UpnpOutputArgument(name = "PlayMedium", stateVariable = "PlaybackStorageMedium", getterName = "getPlayMedium"),
        UpnpOutputArgument(name = "RecordMedium", stateVariable = "RecordStorageMedium", getterName = "getRecordMedium"),
        UpnpOutputArgument(name = "WriteStatus", stateVariable = "RecordMediumWriteStatus", getterName = "getWriteStatus")
    ])
    fun getMediaInfo(
        @UpnpInputArgument(name = "InstanceID") _instanceID: UnsignedIntegerFourBytes
    ): org.fourthline.cling.support.model.MediaInfo {
        return org.fourthline.cling.support.model.MediaInfo(
            avTransportURI,
            avTransportURIMetaData
        )
    }

    /**
     * 获取位置信息（UPnP AVTransport:1 GetPositionInfo）。
     * 控制点（爱奇艺/B站等）轮询此方法获取播放进度（RelTime/AbsTime）。
     *
     * @param _instanceID 实例 ID（未使用）
     * @return [org.fourthline.cling.support.model.PositionInfo] 轨道、时长、当前播放位置等
     */
    @UpnpAction(out = [
        UpnpOutputArgument(name = "Track", stateVariable = "CurrentTrack", getterName = "getTrack"),
        UpnpOutputArgument(name = "TrackDuration", stateVariable = "CurrentTrackDuration", getterName = "getTrackDuration"),
        UpnpOutputArgument(name = "TrackMetaData", stateVariable = "CurrentTrackMetaData", getterName = "getTrackMetaData"),
        UpnpOutputArgument(name = "TrackURI", stateVariable = "CurrentTrackURI", getterName = "getTrackURI"),
        UpnpOutputArgument(name = "RelTime", stateVariable = "RelativeTimePosition", getterName = "getRelTime"),
        UpnpOutputArgument(name = "AbsTime", stateVariable = "AbsoluteTimePosition", getterName = "getAbsTime"),
        UpnpOutputArgument(name = "RelCount", stateVariable = "RelativeCounterPosition", getterName = "getRelCount"),
        UpnpOutputArgument(name = "AbsCount", stateVariable = "AbsoluteCounterPosition", getterName = "getAbsCount")
    ])
    fun getPositionInfo(
        @UpnpInputArgument(name = "InstanceID") _instanceID: UnsignedIntegerFourBytes
    ): org.fourthline.cling.support.model.PositionInfo {
        // 返回符合UPnP规范的PositionInfo对象
        // 控制点（爱奇艺/B站/腾讯视频）通过轮询此方法获取播放进度
        val info = org.fourthline.cling.support.model.PositionInfo(
            currentTrack.value.toLong(),
            currentTrackDuration,
            currentTrackMetaData,
            currentTrackURI,
            relativeTimePosition,  // ⭐ 核心：播放进度
            absoluteTimePosition,
            relativeCounterPosition,
            absoluteCounterPosition
        )
        Log.d(TAG, getString(R.string.log_get_position_info_call, currentTrack.value.toInt(), relativeTimePosition, currentTrackDuration))
        return info
    }
} 