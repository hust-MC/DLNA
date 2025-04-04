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
 */
class DLNAService : Service() {
    companion object {
        private const val TAG = "DLNAService"
        private const val CHANNEL_ID = "DLNA_SERVICE_CHANNEL"
        private const val NOTIFICATION_ID = 1
    }

    /**
     * UPnP服务引用
     *
     * 保存对Cling UPnP服务的引用，用于设备注册和管理
     */
    private var upnpService: AndroidUpnpService? = null

    /**
     * 本地Binder实例
     *
     * 提供给活动绑定到此服务的接口
     */
    private val binder = LocalBinder()

    /**
     * 设备唯一标识符
     *
     * UPnP设备的唯一标识，用于在网络上识别此设备
     */
    private lateinit var udn: UDN

    /**
     * 服务连接对象
     *
     * 用于连接到AndroidUpnpService的连接对象
     */
    private var serviceConnection: ServiceConnection? = null

    /**
     * 本地设备实例
     *
     * 表示本应用作为DLNA渲染器的UPnP设备实例
     */
    private var localDevice: LocalDevice? = null

    /**
     * 本地Binder类
     *
     * 允许活动组件绑定到此服务并获取服务引用
     */
    inner class LocalBinder : Binder() {
        /**
         * 获取服务实例
         *
         * @return DLNAService实例
         */
        fun getService(): DLNAService = this@DLNAService
    }

    /**
     * 服务创建回调
     *
     * 初始化服务，创建通知通道，并启动前台服务。
     * 生成唯一设备标识并绑定UPnP服务。
     */
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "创建DLNA服务")
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        // 创建唯一的设备标识符
        udn = UDN(UUID.randomUUID())
        
        // 绑定UPnP服务
        serviceConnection = object : ServiceConnection {
            /**
             * 服务连接成功回调
             *
             * 当成功绑定到AndroidUpnpService时调用。
             * 保存服务引用并创建UPnP设备。
             *
             * @param name 服务的组件名称
             * @param service 服务的IBinder接口
             */
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                upnpService = service as AndroidUpnpService
                // 注册设备
                createDevice()
            }

            /**
             * 服务断开连接回调
             *
             * 当与AndroidUpnpService的连接意外断开时调用。
             * 清除服务引用。
             *
             * @param name 服务的组件名称
             */
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

    /**
     * 创建通知通道
     *
     * 为Android 8.0及以上版本创建通知通道，
     * 以便于前台服务通知的显示。
     */
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

    /**
     * 创建通知
     *
     * 创建前台服务所需的通知，显示服务正在运行。
     *
     * @return 包含服务信息的Notification对象
     */
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

    /**
     * 创建UPnP设备
     *
     * 创建并注册本设备作为DLNA渲染器，
     * 包含媒体渲染服务和渲染控制服务。
     */
    private fun createDevice() {
        try {
            val type = UDADeviceType("MediaRenderer", 1)
            val details = DeviceDetails(
                "Android DLNA渲染器",
                ManufacturerDetails("Android"),
                ModelDetails("DLNA", "Android DLNA渲染器", "1.0")
            )

            val identity = DeviceIdentity(udn)

            // 创建媒体渲染服务
            val binder = AnnotationLocalServiceBinder()
            
            @Suppress("UNCHECKED_CAST")
            val mediaRendererService = binder.read(MediaRendererService::class.java) as LocalService<MediaRendererService>
            mediaRendererService.setManager(
                DefaultServiceManager(mediaRendererService, MediaRendererService::class.java)
            )

            // 创建渲染控制服务
            @Suppress("UNCHECKED_CAST")
            val renderingControlService = binder.read(RenderingControlService::class.java) as LocalService<RenderingControlService>
            renderingControlService.setManager(
                DefaultServiceManager(renderingControlService, RenderingControlService::class.java)
            )

            // 创建本地设备实例
            localDevice = LocalDevice(
                identity,
                type,
                details,
                arrayOf(mediaRendererService, renderingControlService)
            )

            // 注册设备到UPnP网络
            upnpService?.registry?.addDevice(localDevice)
            Log.d(TAG, "DLNA设备注册成功")
        } catch (e: Exception) {
            Log.e(TAG, "注册DLNA设备失败", e)
        }
    }

    /**
     * 服务绑定回调
     *
     * 当活动组件绑定到此服务时调用。
     * 
     * @param intent 绑定意图
     * @return IBinder接口，允许活动与服务通信
     */
    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    /**
     * 服务销毁回调
     *
     * 当服务被销毁时清理资源，
     * 解绑UPnP服务并关闭UPnP框架。
     */
    override fun onDestroy() {
        serviceConnection?.let {
            applicationContext.unbindService(it)
        }
        upnpService?.get()?.shutdown()
        super.onDestroy()
    }
} 