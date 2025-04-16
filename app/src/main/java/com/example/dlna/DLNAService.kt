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
import com.example.dlna.MediaRendererService.Companion.initialize
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
        initialize(applicationContext)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // 创建唯一的设备标识符
        udn = UDN(UUID.randomUUID())

        // 绑定UPnP服务
        serviceConnection = object : ServiceConnection {
            /** 服务连接成功回调 */
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                Log.d(TAG, "Upnp Service connected")

                upnpService = service as AndroidUpnpService
                // 注册设备
                createDevice()
            }

            /** 服务断开连接回调 */
            override fun onServiceDisconnected(name: ComponentName) {
                Log.d(TAG, "Upnp Service Disconnected")

                upnpService = null
                release()
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
                getString(R.string.dlna_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.dlna_service_channel_description)
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
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.dlna_service_notification_title))
            .setContentText(getString(R.string.dlna_service_notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }

    /** 创建UPnP设备 */
    private fun createDevice() {
        try {
            val type = UDADeviceType("MediaRenderer", 1)
            val details = DeviceDetails(
                getString(R.string.device_friendly_name),  // 设备名称将显示在爱奇艺的设备列表中
                ManufacturerDetails(getString(R.string.device_manufacturer)),
                ModelDetails(
                    getString(R.string.device_model_name),
                    getString(R.string.device_model_description),
                    getString(R.string.device_model_version)
                )
            )

            val identity = DeviceIdentity(udn)

            // 创建媒体渲染服务
            val binder = AnnotationLocalServiceBinder()

            @Suppress("UNCHECKED_CAST")
            val mediaRendererService =
                binder.read(MediaRendererService::class.java) as LocalService<MediaRendererService>
            mediaRendererService.apply {
                initialize(this@DLNAService)
                setManager(
                    DefaultServiceManager(mediaRendererService, MediaRendererService::class.java)
                )
            }

            // 创建渲染控制服务
            @Suppress("UNCHECKED_CAST")
            val renderingControlService =
                binder.read(RenderingControlService::class.java) as LocalService<RenderingControlService>
            
            // 创建一个自定义的ServiceManager，可以提供带有Context的RenderingControlService实例
            val renderingControlManager = object : DefaultServiceManager<RenderingControlService>(
                renderingControlService,
                RenderingControlService::class.java
            ) {
                override fun createServiceInstance(): RenderingControlService {
                    return RenderingControlService(applicationContext)
                }
            }
            
            renderingControlService.setManager(renderingControlManager)

            // 创建本地设备实例
            localDevice = LocalDevice(
                identity, type, details, arrayOf(mediaRendererService, renderingControlService)
            )

            // 注册设备到UPnP网络
            upnpService?.registry?.addDevice(localDevice)
            Log.d(TAG, getString(R.string.log_dlna_device_register_success, details.friendlyName))
        } catch (e: Exception) {
            Log.e(TAG, getString(R.string.log_dlna_device_register_failed), e)
        }
    }

    /** 服务绑定回调 */
    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    /** 服务销毁回调 */
    override fun onDestroy() {
        Log.d(TAG, "Destroying DLNA service")

        super.onDestroy()
        release()
    }

    private fun release() {
        // 释放媒体播放器
        mediaPlayerManager.release()

        // 移除所有本地和远程设备
        upnpService?.registry?.removeAllRemoteDevices()
        upnpService?.registry?.removeAllLocalDevices()

        // 关闭UPnP服务
        Thread {
            try {
                upnpService?.registry?.shutdown()
                Log.d(TAG, "UPnP服务已成功关闭")
            } catch (e: Exception) {
                Log.e(TAG, "关闭UPnP服务时出错", e)
            }
        }.start()

        // 解绑服务连接
        serviceConnection?.let {
            try {
                applicationContext.unbindService(it)
                Log.d(TAG, "服务连接已解绑")
            } catch (e: Exception) {
                Log.e(TAG, "解绑服务连接时出错", e)
            }
            serviceConnection = null
        }

        stopForeground(true)
    }
} 