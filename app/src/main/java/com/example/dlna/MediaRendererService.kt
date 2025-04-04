package com.example.dlna

import org.fourthline.cling.binding.annotations.UpnpAction
import org.fourthline.cling.binding.annotations.UpnpInputArgument
import org.fourthline.cling.binding.annotations.UpnpOutputArgument
import org.fourthline.cling.binding.annotations.UpnpService
import org.fourthline.cling.binding.annotations.UpnpServiceId
import org.fourthline.cling.binding.annotations.UpnpServiceType
import org.fourthline.cling.binding.annotations.UpnpStateVariable
import org.fourthline.cling.model.types.UnsignedIntegerFourBytes

/**
 * UPnP媒体渲染服务
 * 
 * 该服务实现了UPnP AVTransport服务规范，用于控制媒体播放和传输。
 * 它处理媒体URI的设置、播放控制（播放、暂停、停止）以及进度控制（跳转）等功能。
 */
@UpnpService(
    serviceId = UpnpServiceId("AVTransport"),
    serviceType = UpnpServiceType(value = "AVTransport", version = 1)
)
class MediaRendererService {

    /**
     * 实例ID状态变量
     * 
     * 在UPnP中用于标识特定的服务实例。默认值为0，不发送事件通知。
     */
    @UpnpStateVariable(defaultValue = "0", sendEvents = false, name = "InstanceID")
    private var instanceId: UnsignedIntegerFourBytes? = null

    /**
     * 传输状态
     * 
     * 表示当前媒体的传输状态。可能的值包括：
     * - STOPPED：已停止
     * - PLAYING：正在播放
     * - PAUSED_PLAYBACK：暂停播放
     */
    @UpnpStateVariable(defaultValue = "STOPPED")
    private var transportState: String = "STOPPED"

    /**
     * 当前播放模式
     * 
     * 表示当前的播放模式，默认为"1"（常规播放）
     */
    @UpnpStateVariable(defaultValue = "1")
    private var currentPlayMode: String = "1"

    /**
     * 当前轨道
     * 
     * 表示当前正在播放的轨道号
     */
    @UpnpStateVariable(defaultValue = "0")
    private var currentTrack: String = "0"

    /**
     * 当前轨道时长
     * 
     * 表示当前轨道的总时长，格式为"HH:MM:SS"
     */
    @UpnpStateVariable(defaultValue = "00:00:00")
    private var currentTrackDuration: String = "00:00:00"

    /**
     * 当前轨道元数据
     * 
     * 存储当前轨道的描述性信息
     */
    @UpnpStateVariable(defaultValue = "00:00:00")
    private var currentTrackMetaData: String = "00:00:00"

    /**
     * 当前媒体时长
     * 
     * 表示当前媒体的总时长，格式为"HH:MM:SS"
     */
    @UpnpStateVariable(defaultValue = "00:00:00")
    private var currentMediaDuration: String = "00:00:00"

    /**
     * 当前URI
     * 
     * 存储当前正在播放的媒体资源URI
     */
    @UpnpStateVariable(defaultValue = "")
    private var currentURI: String = ""

    /**
     * 当前URI元数据
     * 
     * 存储当前媒体资源的元数据
     */
    @UpnpStateVariable(defaultValue = "")
    private var currentURIMetaData: String = ""

    /**
     * 下一轨道URI
     * 
     * 存储下一个将要播放的资源URI
     */
    @UpnpStateVariable(defaultValue = "0")
    private var nextTrackURI: String = "0"

    /**
     * 下一轨道元数据
     * 
     * 存储下一个轨道的描述性信息
     */
    @UpnpStateVariable(defaultValue = "")
    private var nextTrackMetaData: String = ""

    /**
     * 轨道总数
     * 
     * 当前播放列表中的轨道总数
     */
    @UpnpStateVariable(defaultValue = "0")
    private var numberOfTracks: String = "0"

    /**
     * 播放速度
     * 
     * 当前媒体的播放速度，"1"表示正常速度
     */
    @UpnpStateVariable(defaultValue = "1")
    private var speed: String = "1"

    /**
     * 跳转模式
     * 
     * 用于指定跳转操作的模式，"REL_TIME"表示相对时间
     */
    @UpnpStateVariable(defaultValue = "REL_TIME", name = "Unit")
    private var seekMode: String = "REL_TIME"

    /**
     * 跳转目标
     * 
     * 跳转操作的目标位置，格式为"HH:MM:SS"
     */
    @UpnpStateVariable(defaultValue = "00:00:00", name = "Target")
    private var seekTarget: String = "00:00:00"

    /**
     * 设置AV传输URI
     * 
     * 设置要播放的媒体资源URI及其元数据
     * 
     * @param instanceId 目标服务实例的ID
     * @param uri 媒体资源的URI
     * @param metadata 媒体资源的元数据
     */
    @UpnpAction
    fun setAVTransportURI(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "CurrentURI") uri: String,
        @UpnpInputArgument(name = "CurrentURIMetaData") metadata: String
    ) {
        this.currentURI = uri
        this.currentURIMetaData = metadata
    }

    /**
     * 播放媒体
     * 
     * 开始播放当前设置的媒体资源
     * 
     * @param instanceId 目标服务实例的ID
     * @param speed 播放速度，"1"表示正常速度
     */
    @UpnpAction
    fun play(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Speed") speed: String
    ) {
        this.speed = speed
        this.transportState = "PLAYING"
    }

    /**
     * 暂停播放
     * 
     * 暂停当前正在播放的媒体
     * 
     * @param instanceId 目标服务实例的ID
     */
    @UpnpAction
    fun pause(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes
    ) {
        this.transportState = "PAUSED_PLAYBACK"
    }

    /**
     * 停止播放
     * 
     * 停止当前媒体的播放
     * 
     * @param instanceId 目标服务实例的ID
     */
    @UpnpAction
    fun stop(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes
    ) {
        this.transportState = "STOPPED"
    }

    /**
     * 媒体跳转
     * 
     * 将媒体播放位置跳转到指定时间点
     * 
     * @param instanceId 目标服务实例的ID
     * @param unit 跳转单位，通常为"REL_TIME"（相对时间）
     * @param target 目标时间点，格式为"HH:MM:SS"
     */
    @UpnpAction
    fun seek(
        @UpnpInputArgument(name = "InstanceID") instanceId: UnsignedIntegerFourBytes,
        @UpnpInputArgument(name = "Unit") unit: String,
        @UpnpInputArgument(name = "Target") target: String
    ) {
        this.seekMode = unit
        this.seekTarget = target
    }

    /**
     * 获取传输状态
     * 
     * @return 当前的传输状态（PLAYING、STOPPED、PAUSED_PLAYBACK等）
     */
    @UpnpAction(out = [UpnpOutputArgument(name = "CurrentTransportState")])
    fun getTransportState(): String {
        return transportState
    }

    /**
     * 获取当前轨道
     * 
     * @return 当前正在播放的轨道号
     */
    @UpnpAction(out = [UpnpOutputArgument(name = "CurrentTrack")])
    fun getCurrentTrack(): String {
        return currentTrack
    }

    /**
     * 获取当前轨道时长
     * 
     * @return 当前轨道的总时长
     */
    @UpnpAction(out = [UpnpOutputArgument(name = "CurrentTrackDuration")])
    fun getCurrentTrackDuration(): String {
        return currentTrackDuration
    }

    /**
     * 获取当前媒体时长
     * 
     * @return 当前媒体的总时长
     */
    @UpnpAction(out = [UpnpOutputArgument(name = "CurrentMediaDuration")])
    fun getCurrentMediaDuration(): String {
        return currentMediaDuration
    }

    /**
     * 获取当前URI
     * 
     * @return 当前正在播放的媒体资源URI
     */
    @UpnpAction(out = [UpnpOutputArgument(name = "CurrentURI")])
    fun getCurrentURI(): String {
        return currentURI
    }

    /**
     * 获取当前URI元数据
     * 
     * @return 当前媒体资源的元数据
     */
    @UpnpAction(out = [UpnpOutputArgument(name = "CurrentURIMetaData")])
    fun getCurrentURIMetaData(): String {
        return currentURIMetaData
    }

    /**
     * 获取播放速度
     * 
     * @return 当前媒体的播放速度
     */
    @UpnpAction(out = [UpnpOutputArgument(name = "Speed")])
    fun getSpeed(): String {
        return speed
    }

    /**
     * 获取跳转模式
     * 
     * @return 当前设置的跳转模式
     */
    @UpnpAction(out = [UpnpOutputArgument(name = "Unit")])
    fun getSeekMode(): String {
        return seekMode
    }

    /**
     * 获取跳转目标
     * 
     * @return 最近一次跳转操作的目标时间点
     */
    @UpnpAction(out = [UpnpOutputArgument(name = "Target")])
    fun getSeekTarget(): String {
        return seekTarget
    }
} 