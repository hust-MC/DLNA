package com.example.dlna

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import org.fourthline.cling.android.AndroidUpnpService
import org.fourthline.cling.android.AndroidUpnpServiceImpl
import org.fourthline.cling.binding.annotations.AnnotationLocalServiceBinder
import org.fourthline.cling.model.DefaultServiceManager
import org.fourthline.cling.model.meta.DeviceDetails
import org.fourthline.cling.model.meta.DeviceIdentity
import org.fourthline.cling.model.meta.LocalDevice
import org.fourthline.cling.model.meta.LocalService
import org.fourthline.cling.model.meta.ManufacturerDetails
import org.fourthline.cling.model.meta.ModelDetails
import org.fourthline.cling.model.types.DeviceType
import org.fourthline.cling.model.types.UDADeviceType
import org.fourthline.cling.model.types.UDN
import java.util.UUID

/**
 * DLNA服务
 *
 * 该服务负责创建和注册UPnP设备，使Android设备成为DLNA渲染器。
 * 它管理设备的生命周期，提供前台服务通知，并处理与UPnP框架的交互。
 * @author Max
 */
class DLNAService : Service() {
    companion object {
        private const val TAG = "DLNAService"
        private const val CHANNEL_ID = "DLNA_SERVICE_CHANNEL"
        private const val NOTIFICATION_ID = 1
    }

    /** UPnP服务引用 */
    private var upnpService: AndroidUpnpService? = null

    /** 本地Binder实例 */
    private val binder = LocalBinder()

    /** 设备唯一标识符 */
    private lateinit var udn: UDN

    /** 服务连接对象 */
    private var serviceConnection: ServiceConnection? = null

    /** 本地设备实例 */
    private var localDevice: LocalDevice? = null
    
    /** 媒体播放器管理器 */
    private lateinit var mediaPlayerManager: MediaPlayerManager

    /** 本地Binder类 */
    inner class LocalBinder : Binder() {
        /** 获取服务实例 */
        fun getService(): DLNAService = this@DLNAService
    }

    /** 服务创建回调 */
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "DLNA服务创建")
        
        // 初始化MediaPlayerManager
        mediaPlayerManager = MediaPlayerManager(applicationContext)
        
        // 初始化服务
        MediaRendererService.initialize(applicationContext)
        
        // 将媒体播放器管理器设置到MediaRendererService中
        MediaRendererService.setMediaPlayerManager(mediaPlayerManager)
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        // 创建唯一的设备标识符
        udn = UDN(UUID.randomUUID())
        
        // 绑定UPnP服务
        serviceConnection = object : ServiceConnection {
            /** 服务连接成功回调 */
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                upnpService = service as AndroidUpnpService
                // 注册设备
                createDevice()
            }

            /** 服务断开连接回调 */
            override fun onServiceDisconnected(name: ComponentName) {
                upnpService = null
            }
        }

        // 绑定AndroidUpnpService
        applicationContext.bindService(
            Intent(this, AndroidUpnpServiceImpl::class.java),
            serviceConnection!!,
            Context.BIND_AUTO_CREATE
        )
    }

    /** 创建通知通道 */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DLNA服务通道",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "DLNA服务通知通道"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /** 创建通知 */
    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DLNA 投屏服务")
            .setContentText("正在运行中...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()
    }

    /** 创建UPnP设备 */
    private fun createDevice() {
        try {
            val type = UDADeviceType("MediaRenderer", 1)
            val details = DeviceDetails(
                "Max投屏器",  // 设备名称将显示在爱奇艺的设备列表中
                ManufacturerDetails("Max投屏器"),
                ModelDetails("DLNA播放器", "Android DLNA媒体渲染器", "1.0")
            )

            val identity = DeviceIdentity(udn)

            // 创建媒体渲染服务
            val binder = AnnotationLocalServiceBinder()
            
            @Suppress("UNCHECKED_CAST")
            val mediaRendererService = binder.read(MediaRendererService::class.java) as LocalService<MediaRendererService>
            mediaRendererService.setManager(
                DefaultServiceManager(mediaRendererService, MediaRendererService::class.java)
            )

            // 创建本地设备实例
            localDevice = LocalDevice(
                identity,
                type,
                details,
                arrayOf(mediaRendererService)
            )

            // 注册设备到UPnP网络
            upnpService?.registry?.addDevice(localDevice)
            Log.d(TAG, "DLNA设备注册成功: ${details.friendlyName}")
        } catch (e: Exception) {
            Log.e(TAG, "注册DLNA设备失败", e)
        }
    }

    /** 服务绑定回调 */
    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    /** 服务销毁回调 */
    override fun onDestroy() {
        Log.d(TAG, "Destroying DLNA service")
        
        // 释放媒体播放器
        mediaPlayerManager.release()
        
        // 移除所有本地和远程设备
        upnpService?.registry?.removeAllRemoteDevices()
        upnpService?.registry?.removeAllLocalDevices()
        
        // 关闭UPnP服务
        upnpService?.registry?.shutdown()
        
        // 解绑服务连接
        serviceConnection?.let { 
            unbindService(it)
        }
        
        stopForeground(true)
        super.onDestroy()
    }
} 