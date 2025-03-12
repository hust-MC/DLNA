package com.example.dlna;

import org.fourthline.cling.UpnpService;
import org.fourthline.cling.UpnpServiceImpl;
import org.fourthline.cling.binding.*;
import org.fourthline.cling.binding.annotations.*;
import org.fourthline.cling.model.*;
import org.fourthline.cling.model.meta.*;
import org.fourthline.cling.model.types.*;

import java.net.HttpURLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.io.IOException;
import java.net.URL;

public class BinaryLightServer implements Runnable {


    public static class CustomStreamHandlerFactory implements URLStreamHandlerFactory {
        @Override
        public URLStreamHandler createURLStreamHandler(String protocol) {
            // 只处理 HTTP 协议
            if ("http".equals(protocol)) {
                return new java.net.URLStreamHandler() {
                    @Override
                    protected java.net.URLConnection openConnection(URL u) {
                        return new HttpURLConnection(u) {
                            @Override
                            public void disconnect() {
                                System.err.println("BinaryLightServer main start: disconnect");

                            }

                            @Override
                            public boolean usingProxy() {
                                System.err.println("BinaryLightServer main start: usingProxy");

                                return false;
                            }

                            @Override
                            public void connect() {
                                // 这里可以添加自定义连接逻辑
                                System.err.println("BinaryLightServer main start: connect");

                            }
                        };
                    }
                };
            }
            return null; // 返回 null，表示其他协议使用默认处理器
        }
    }

    public static void main(String[] args) throws Exception {
        URL.setURLStreamHandlerFactory(new CustomStreamHandlerFactory());
        System.err.println("BinaryLightServer main start: ");

        // Start a user thread that runs the UPnP stack
        Thread serverThread = new Thread(new BinaryLightServer());
        serverThread.setDaemon(false);
        serverThread.start();
    }

    public void run() {
        try {

            final UpnpService upnpService = new UpnpServiceImpl();

            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    upnpService.shutdown();
                }
            });

            // Add the bound local device to the registry
            upnpService.getRegistry().addDevice(createDevice());

        } catch (Exception ex) {
            System.err.println("Exception occured: " + ex);
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }

    LocalDevice createDevice() throws ValidationException, LocalServiceBindingException, IOException {

        DeviceIdentity identity = new DeviceIdentity(UDN.uniqueSystemIdentifier("Demo Binary Light"));

        DeviceType type = new UDADeviceType("BinaryLight", 1);

        DeviceDetails details = new DeviceDetails("Friendly Binary Light", new ManufacturerDetails("ACME"), new ModelDetails("BinLight2000", "A demo light with on/off switch.", "v1"));

//        Icon icon = new Icon("image/png", 48, 48, 8, getClass().getResource("icon.png"));

        LocalService<SwitchPower> switchPowerService = new AnnotationLocalServiceBinder().read(SwitchPower.class);

        switchPowerService.setManager(new DefaultServiceManager(switchPowerService, SwitchPower.class));

        return new LocalDevice(identity, type, details, switchPowerService);

    /* Several services can be bound to the same device:
    return new LocalDevice(
            identity, type, details, icon,
            new LocalService[] {switchPowerService, myOtherService}
    );
    */

    }

}
