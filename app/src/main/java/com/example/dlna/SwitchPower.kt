package com.example.dlna

import org.fourthline.cling.binding.annotations.UpnpAction
import org.fourthline.cling.binding.annotations.UpnpOutputArgument
import org.fourthline.cling.binding.annotations.UpnpService
import org.fourthline.cling.binding.annotations.UpnpServiceId
import org.fourthline.cling.binding.annotations.UpnpServiceType
import org.fourthline.cling.binding.annotations.UpnpStateVariable

@UpnpService(
    serviceId = UpnpServiceId("SwitchPower"),
    serviceType = UpnpServiceType(value = "SwitchPower", version = 1)
)
class SwitchPower {
    @get:UpnpAction(out = [UpnpOutputArgument(name = "RetTargetValue")])
    @set:UpnpAction
    @UpnpStateVariable(defaultValue = "0", sendEvents = false)
    var target: Boolean = false
        set(newTargetValue) {
            field = newTargetValue
            status = newTargetValue
            println("Switch is: $status")
        }

    // If you want to pass extra UPnP information on error:
    // throw new ActionException(ErrorCode.ACTION_NOT_AUTHORIZED);
    @get:UpnpAction(out = [UpnpOutputArgument(name = "ResultStatus")])
    @UpnpStateVariable(defaultValue = "0")
    var status: Boolean = false
        private set
}