//package com.example.dlna;
//
//import org.fourthline.cling.UpnpService;
//import org.fourthline.cling.UpnpServiceConfiguration;
//import org.fourthline.cling.android.AndroidUpnpService;
//import org.fourthline.cling.android.AndroidUpnpServiceConfiguration;
//import org.fourthline.cling.controlpoint.ControlPoint;
//import org.fourthline.cling.model.meta.Device;
//import org.fourthline.cling.model.meta.Service;
//import org.fourthline.cling.model.message.UpnpResponse;
//import org.fourthline.cling.model.types.UDADeviceType;
//import org.fourthline.cling.registry.Registry;
//import org.fourthline.cling.support.model.DIDLContent;
//import org.fourthline.cling.support.model.res.Res;
//import org.fourthline.cling.support.model.DIDLResource;
//import org.fourthline.cling.model.meta.Device;
//import org.fourthline.cling.model.meta.Service;
//import org.fourthline.cling.model.message.UpnpResponse;
//import org.fourthline.cling.model.types.UDADeviceType;
//
//import android.os.Bundle;
//import android.widget.Toast;
//import androidx.appcompat.app.AppCompatActivity;
//
//public class DLNAActivity extends AppCompatActivity {
//
//    private AndroidUpnpService upnpService;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
////        setContentView(R.layout.activity_dlna);
//
//        // 初始化 Cling 服务
//        upnpService = new AndroidUpnpService() {
//            @Override
//            public UpnpService get() {
//                return null;
//            }
//
//            @Override
//            public UpnpServiceConfiguration getConfiguration() {
//                return null;
//            }
//
//            @Override
//            public Registry getRegistry() {
//                return null;
//            }
//
//            @Override
//            public ControlPoint getControlPoint() {
//                return null;
//            }
//        };
//        upnpService.start();
//
//        // 查找 DLNA 设备
//        searchForDevices();
//    }
//
//    // 查找 DLNA 设备
//    private void searchForDevices() {
//        upnpService.getRegistry().addDeviceDiscoveryListener(new DeviceDiscoveryListener() {
//            @Override
//            public void deviceAdded(Device device) {
//                // 在此处理找到的设备
//                Toast.makeText(DLNAActivity.this, "发现设备：" + device.getDetails().getFriendlyName(), Toast.LENGTH_LONG).show();
//
//                // 获取设备服务
//                Service<?> service = device.findService(new UDADeviceType("MediaRenderer"));
//                if (service != null) {
//                    // 控制设备或播放媒体
//                    playMedia(service);
//                }
//            }
//
//            @Override
//            public void deviceRemoved(Device device) {
//                // 设备移除时的处理
//            }
//        });
//
//        upnpService.getControlPoint().search();
//    }
//
//    // 播放媒体
//    private void playMedia(Service<?> service) {
//        // 创建 DIDLContent（媒体内容）并调用设备服务播放媒体
//        DIDLContent content = new DIDLContent();
//        Res res = new Res("http://example.com/video.mp4");
//        content.addItem(new DIDLResource(res));
//
//        // 控制设备播放
//        service.getAction("Play").invoke();
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        if (upnpService != null) {
//            upnpService.stop();
//        }
//    }
//}
