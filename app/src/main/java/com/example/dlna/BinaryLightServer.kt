package com.example.dlna

import android.util.Log
import org.fourthline.cling.UpnpService
import org.fourthline.cling.UpnpServiceImpl
import org.fourthline.cling.binding.LocalServiceBindingException
import org.fourthline.cling.binding.annotations.AnnotationLocalServiceBinder
import org.fourthline.cling.model.DefaultServiceManager
import org.fourthline.cling.model.ValidationException
import org.fourthline.cling.model.meta.DeviceDetails
import org.fourthline.cling.model.meta.DeviceIdentity
import org.fourthline.cling.model.meta.Icon
import org.fourthline.cling.model.meta.LocalDevice
import org.fourthline.cling.model.meta.LocalService
import org.fourthline.cling.model.meta.ManufacturerDetails
import org.fourthline.cling.model.meta.ModelDetails
import org.fourthline.cling.model.types.UDADeviceType
import org.fourthline.cling.model.types.UDN
import java.io.IOException


class BinaryLightServer : Runnable {
    override fun run() {
        try {
            val upnpService: UpnpService = UpnpServiceImpl()

            Runtime.getRuntime().addShutdownHook(object : Thread() {
                override fun run() {
                    Log.i("MCLOG", "ShutdownHook")
                    upnpService.shutdown()
                }
            })

            // Add the bound local device to the registry
            upnpService.registry.addDevice(
                createDevice()
            )
        } catch (ex: Exception) {
            System.err.println("Exception occured: $ex")
            ex.printStackTrace(System.err)
            System.exit(1)
        }
    }

    @Throws(ValidationException::class, LocalServiceBindingException::class, IOException::class)
    fun createDevice(): LocalDevice {
        val identity = DeviceIdentity(
            UDN.uniqueSystemIdentifier("Demo Binary Light")
        )

        val type = UDADeviceType("BinaryLight", 1)

        val details = DeviceDetails(
            "Friendly Binary Light",
            ManufacturerDetails("ACME"),
            ModelDetails(
                "BinLight2000",
                "A demo light with on/off switch.",
                "v1"
            )
        )

        val icon = Icon("image/png", 48, 48, 8, javaClass.getResource("icon.png"))

        @Suppress("UNCHECKED_CAST")
        val switchPowerService =
            AnnotationLocalServiceBinder().read(SwitchPower::class.java) as LocalService<SwitchPower>
        switchPowerService.setManager(
            DefaultServiceManager(
                switchPowerService,
                SwitchPower::class.java
            )
        )

        return LocalDevice(identity, type, details, icon, switchPowerService)

        // 你也可以使用下面的代码将多个服务绑定到同一个设备:
        // return LocalDevice(
        //     identity, type, details, icon,
        //     arrayOf(switchPowerService, myOtherService)
        // )
    }

}