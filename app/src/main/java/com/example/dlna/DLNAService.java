package com.example.dlna;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import org.fourthline.cling.android.AndroidUpnpService;
import org.fourthline.cling.android.AndroidUpnpServiceImpl;
import org.fourthline.cling.binding.LocalServiceBindingException;
import org.fourthline.cling.binding.annotations.AnnotationLocalServiceBinder;
import org.fourthline.cling.model.DefaultServiceManager;
import org.fourthline.cling.model.ValidationException;
import org.fourthline.cling.model.meta.DeviceDetails;
import org.fourthline.cling.model.meta.DeviceIdentity;
import org.fourthline.cling.model.meta.LocalDevice;
import org.fourthline.cling.model.meta.LocalService;
import org.fourthline.cling.model.meta.ManufacturerDetails;
import org.fourthline.cling.model.meta.ModelDetails;
import org.fourthline.cling.model.types.DeviceType;
import org.fourthline.cling.model.types.UDADeviceType;
import org.fourthline.cling.model.types.UDN;
import org.fourthline.cling.registry.DefaultRegistryListener;
import org.fourthline.cling.registry.Registry;
import org.fourthline.cling.registry.RegistryListener;

import java.util.UUID;

public class DLNAService extends Service {
    private static final String TAG = "DLNAService";
    private static final String CHANNEL_ID = "DLNA_SERVICE_CHANNEL";
    private static final int NOTIFICATION_ID = 1;

    private AndroidUpnpService upnpService;
    private final IBinder binder = new LocalBinder();
    private UDN udn;
    private ServiceConnection serviceConnection;
    private LocalDevice localDevice;

    public class LocalBinder extends Binder {
        DLNAService getService() {
            return DLNAService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Creating DLNA Service");
        
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        
        // 创建唯一的设备标识符
        udn = new UDN(UUID.randomUUID());
        
        // 绑定UPnP服务
        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                upnpService = (AndroidUpnpService) service;
                // 注册设备
                createDevice();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                upnpService = null;
            }
        };

        // 绑定AndroidUpnpService
        getApplicationContext().bindService(
            new Intent(this, AndroidUpnpServiceImpl.class),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        );
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "DLNA Service Channel",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("DLNA Service Notification Channel");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DLNA 投屏服务")
            .setContentText("正在运行中...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build();
    }

    private void createDevice() {
        try {
            DeviceType type = new UDADeviceType("MediaRenderer", 1);
            DeviceDetails details = new DeviceDetails(
                "Android DLNA Renderer",
                new ManufacturerDetails("Android"),
                new ModelDetails("DLNA", "Android DLNA Renderer", "1.0")
            );

            DeviceIdentity identity = new DeviceIdentity(udn);

            // 创建服务
            LocalService<MediaRendererService> mediaRendererService = 
                new AnnotationLocalServiceBinder().read(MediaRendererService.class);
            mediaRendererService.setManager(
                new DefaultServiceManager(mediaRendererService, MediaRendererService.class)
            );

            LocalService<RenderingControlService> renderingControlService = 
                new AnnotationLocalServiceBinder().read(RenderingControlService.class);
            renderingControlService.setManager(
                new DefaultServiceManager(renderingControlService, RenderingControlService.class)
            );

            localDevice = new LocalDevice(
                identity,
                type,
                details,
                new LocalService[]{mediaRendererService, renderingControlService}
            );

            upnpService.getRegistry().addDevice(localDevice);
            Log.d(TAG, "DLNA device registered successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register DLNA device", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        if (serviceConnection != null) {
            getApplicationContext().unbindService(serviceConnection);
        }
        if (upnpService != null) {
            upnpService.get().shutdown();
        }
        super.onDestroy();
    }
} 