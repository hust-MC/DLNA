//package com.example.dlna
//
//import android.bluetooth.BluetoothClass
//import org.fourthline.cling.controlpoint.ControlPoint
//import org.fourthline.cling.registry.RegistryListener
//
//
//class DeviceScanner(controlPoint: ControlPoint) : RegistryListener {
//    private val controlPoint: ControlPoint
//
//    init {
//        this.controlPoint = controlPoint
//        controlPoint.getRegistry().addListener(this)
//    }
//
//    fun remoteDeviceAdded(registry: Registry?, device: BluetoothClass.Device) {
//        // 当发现新设备时调用
//        System.out.println("New device discovered: " + device.getDetails().getFriendlyName())
//    }
//
//    fun remoteDeviceRemoved(registry: Registry?, device: BluetoothClass.Device) {
//        // 当设备移除时调用
//        System.out.println("Device removed: " + device.getDetails().getFriendlyName())
//    } // 其他必要的回调方法...
//}