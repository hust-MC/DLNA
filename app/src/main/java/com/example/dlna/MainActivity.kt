package com.example.dlna

import android.app.Activity
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import org.fourthline.cling.UpnpService
import org.fourthline.cling.UpnpServiceImpl
import org.fourthline.cling.android.AndroidUpnpService
import org.fourthline.cling.android.AndroidUpnpServiceConfiguration
import org.fourthline.cling.binding.LocalServiceBindingException
import org.fourthline.cling.binding.annotations.AnnotationLocalServiceBinder
import org.fourthline.cling.model.DefaultServiceManager
import org.fourthline.cling.model.ValidationException
import org.fourthline.cling.model.message.header.STAllHeader
import org.fourthline.cling.model.meta.Device
import org.fourthline.cling.model.meta.DeviceDetails
import org.fourthline.cling.model.meta.DeviceIdentity
import org.fourthline.cling.model.meta.Icon
import org.fourthline.cling.model.meta.LocalDevice
import org.fourthline.cling.model.meta.LocalService
import org.fourthline.cling.model.meta.ManufacturerDetails
import org.fourthline.cling.model.meta.ModelDetails
import org.fourthline.cling.model.meta.RemoteDevice
import org.fourthline.cling.model.types.UDADeviceType
import org.fourthline.cling.model.types.UDN
import org.fourthline.cling.registry.DefaultRegistryListener
import org.fourthline.cling.registry.Registry
import org.fourthline.cling.registry.RegistryListener
import java.io.IOException


class MainActivity : Activity() {

    private var upnpService: AndroidUpnpService? = null

    // UPnP discovery is asynchronous, we need a callback
    var listener: RegistryListener = object : RegistryListener {
        override fun remoteDeviceDiscoveryStarted(
            registry: Registry, device: RemoteDevice
        ) {
            println(
                "Discovery started: " + device.displayString
            )
        }

        override fun remoteDeviceDiscoveryFailed(
            registry: Registry, device: RemoteDevice, ex: Exception
        ) {
            println("Discovery failed: " + device.displayString + " => " + ex)
        }

        override fun remoteDeviceAdded(registry: Registry, device: RemoteDevice) {
            println("Remote device available: " + device.displayString)
        }

        override fun remoteDeviceUpdated(registry: Registry, device: RemoteDevice) {
            println(
                "Remote device updated: " + device.displayString
            )
        }

        override fun remoteDeviceRemoved(registry: Registry, device: RemoteDevice) {
            println(
                "Remote device removed: " + device.displayString
            )
        }

        override fun localDeviceAdded(registry: Registry, device: LocalDevice) {
            println(
                "Local device added: " + device.displayString
            )
        }

        override fun localDeviceRemoved(registry: Registry, device: LocalDevice) {
            println(
                "Local device removed: " + device.displayString
            )
        }

        override fun beforeShutdown(registry: Registry) {
            println(
                "Before shutdown, the registry has devices: " + registry.devices.size
            )
        }

        override fun afterShutdown() {
            println("Shutdown of registry complete!")
        }
    }


    // Define ServiceConnection for binding
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            // Safely access the UpnpService through the AndroidUpnpService interface
            if (service is AndroidUpnpService) {
                upnpService = service
                // Initialize Cling and register listeners once service is connected
                initializeCling()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // When service is disconnected, set the upnpService to null
            upnpService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind to the UPnP service
//        val intent = Intent(this, AndroidUpnpService::class.java)
//        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        // Search Devices
//        searchDevices()

        // Start a user thread that runs the UPnP stack
        val serverThread = Thread(BinaryLightServer())
        serverThread.isDaemon = false
        serverThread.start()
    }

    private fun searchDevices() {
        val upnpService: UpnpService = UpnpServiceImpl(AndroidUpnpServiceConfiguration(), listener)

        // Send a search message to all devices and services, they should respond soon
        upnpService.controlPoint.search(STAllHeader())

        // Let's wait 10 seconds for them to respond
        println("Waiting 10 seconds before shutting down...")
        Thread.sleep(10000)

        // Release all resources and advertise BYEBYE to other UPnP devices
        println("Stopping Cling...")
        upnpService.shutdown()
    }

    private fun initializeCling() {
        upnpService?.let { service ->
            // Access Registry and ControlPoint from the UpnpService interface
            val registry = service.registry

            // Add a RegistryListener to track device changes
            registry.addListener(object : DefaultRegistryListener() {
                override fun deviceAdded(registry: Registry, device: Device<DeviceIdentity, *, *>) {
                    Log.d("Cling", "Device Added: ${device.details.friendlyName}")
                }

                override fun deviceRemoved(
                    registry: Registry, device: Device<DeviceIdentity, *, *>
                ) {
                    Log.d("Cling", "Device Removed: ${device.details.friendlyName}")
                }
            })

            // Start searching for devices
            service.controlPoint.search()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unbind from the service when the activity is destroyed
        unbindService(serviceConnection)
    }

}

