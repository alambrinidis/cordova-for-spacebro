/*
 * Cordova ZeroConf Plugin
 *
 * ZeroConf plugin for Cordova/Phonegap 
 * by Sylvain Brejeon
 */

package net.becvert.cordova;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

public class ZeroConf extends CordovaPlugin {

    private static final String TAG = "ZeroConf";

    WifiManager.MulticastLock lock;

    private RegistrationManager registrationManager;
    private BrowserManager browserManager;
    private List<InetAddress> addresses;
    private String hostname;

    public static final String ACTION_GET_HOSTNAME = "getHostname";
    // publisher
    public static final String ACTION_REGISTER = "register";
    public static final String ACTION_UNREGISTER = "unregister";
    public static final String ACTION_STOP = "stop";
    // browser
    public static final String ACTION_WATCH = "watch";
    public static final String ACTION_UNWATCH = "unwatch";
    public static final String ACTION_CLOSE = "close";

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        WifiManager wifi = (WifiManager) this.cordova.getActivity()
                .getSystemService(android.content.Context.WIFI_SERVICE);
        lock = wifi.createMulticastLock("ZeroConfPluginLock");
        lock.setReferenceCounted(true);
        lock.acquire();

        try {
            List<InetAddress> selectedAddresses = new ArrayList<InetAddress>();
            List<NetworkInterface> intfs = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : intfs) {
                if (intf.supportsMulticast()) {
                    // break on first found ipv4 & ipv6
                    boolean ipv4 = false;
                    boolean ipv6 = false;
                    List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                    for (InetAddress addr : addrs) {
                        if (ipv4 && ipv6) {
                            break;
                        }
                        if (!addr.isLoopbackAddress()) {
                            if (addr instanceof Inet6Address && !ipv6) {
                                selectedAddresses.add(addr);
                                ipv6 = true;
                            } else if (addr instanceof Inet4Address && !ipv4) {
                                selectedAddresses.add(addr);
                                ipv4 = true;
                            }
                        }
                    }
                }
            }
            addresses = selectedAddresses;
        } catch (Exception e) {
            e.printStackTrace();
        }

        Log.d(TAG, "Addresses " + addresses);

        try {
            hostname = getHostName();
        } catch (Exception e) {
            e.printStackTrace();
        }

        Log.d(TAG, "Hostname " + hostname);

        Log.v(TAG, "Initialized");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (registrationManager != null) {
            try {
                registrationManager.stop();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                registrationManager = null;
            }
        }
        if (browserManager != null) {
            try {
                browserManager.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                browserManager = null;
            }
        }
        if (lock != null) {
            lock.release();
            lock = null;
        }
    }

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) {

        if (ACTION_GET_HOSTNAME.equals(action)) {

            if (hostname != null) {

                Log.d(TAG, "Hostname: " + hostname);

                callbackContext.success(hostname);

            } else {
                callbackContext.error("Error: undefined hostname");
                return false;
            }

        } else if (ACTION_REGISTER.equals(action)) {

            final String type = args.optString(0);
            final String domain = args.optString(1);
            final String name = args.optString(2);
            final int port = args.optInt(3);
            final JSONObject props = args.optJSONObject(4);

            Log.d(TAG, "Register " + type + domain);

            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (registrationManager == null) {
                            registrationManager = new RegistrationManager(addresses, hostname);
                        }

                        ServiceInfo service = registrationManager.register(type, domain, name, port, props);

                        JSONObject status = new JSONObject();
                        status.put("action", "registered");
                        status.put("service", jsonifyService(service));

                        Log.d(TAG, "Sending result: " + status.toString());

                        PluginResult result = new PluginResult(PluginResult.Status.OK, status);
                        callbackContext.sendPluginResult(result);

                    } catch (JSONException e) {
                        e.printStackTrace();
                        callbackContext.error("Error: " + e.getMessage());
                    } catch (IOException e) {
                        e.printStackTrace();
                        callbackContext.error("Error: " + e.getMessage());
                    }
                }
            });

        } else if (ACTION_UNREGISTER.equals(action)) {

            final String type = args.optString(0);
            final String domain = args.optString(1);
            final String name = args.optString(2);

            Log.d(TAG, "Unregister " + type + domain);

            if (registrationManager != null) {
                cordova.getThreadPool().execute(new Runnable() {

                    @Override
                    public void run() {
                        registrationManager.unregister(type, domain, name);
                        callbackContext.success();
                    }
                });
            } else {
                callbackContext.success();
            }

        } else if (ACTION_STOP.equals(action)) {

            Log.d(TAG, "Stop");

            if (registrationManager != null) {
                final RegistrationManager rm = registrationManager;
                registrationManager = null;
                cordova.getThreadPool().execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            rm.stop();
                            callbackContext.success();

                        } catch (IOException e) {
                            e.printStackTrace();
                            callbackContext.error("Error: " + e.getMessage());
                        }
                    }
                });
            } else {
                callbackContext.success();
            }

        } else if (ACTION_WATCH.equals(action)) {

            final String type = args.optString(0);
            final String domain = args.optString(1);

            Log.d(TAG, "Watch " + type + domain);

            cordova.getThreadPool().execute(new Runnable() {

                @Override
                public void run() {
                    try {
                        if (browserManager == null) {
                            browserManager = new BrowserManager(addresses, hostname);
                        }

                        browserManager.watch(type, domain, callbackContext);

                    } catch (IOException e) {
                        e.printStackTrace();
                        callbackContext.error("Error: " + e.getMessage());
                    }
                }
            });

            PluginResult result = new PluginResult(Status.NO_RESULT);
            result.setKeepCallback(true);

        } else if (ACTION_UNWATCH.equals(action)) {

            final String type = args.optString(0);
            final String domain = args.optString(1);

            Log.d(TAG, "Unwatch " + type + domain);

            if (browserManager != null) {
                cordova.getThreadPool().execute(new Runnable() {
                    @Override
                    public void run() {
                        browserManager.unwatch(type, domain);
                        callbackContext.success();
                    }
                });
            } else {
                callbackContext.success();
            }

            PluginResult result = new PluginResult(Status.NO_RESULT);
            result.setKeepCallback(false);

        } else if (ACTION_CLOSE.equals(action)) {

            Log.d(TAG, "Close");

            if (browserManager != null) {
                final BrowserManager bm = browserManager;
                browserManager = null;
                cordova.getThreadPool().execute(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            bm.close();
                            callbackContext.success();

                        } catch (IOException e) {
                            e.printStackTrace();
                            callbackContext.error("Error: " + e.getMessage());
                        }
                    }
                });
            } else {
                callbackContext.success();
            }

        } else {
            Log.e(TAG, "Invalid action: " + action);
            callbackContext.error("Invalid action: " + action);
            return false;
        }

        return true;
    }

    private class RegistrationManager {

        private List<JmDNS> publishers = new ArrayList<JmDNS>();

        public RegistrationManager(List<InetAddress> addresses, String hostname) throws IOException {

            if (addresses == null || addresses.size() == 0) {
                publishers.add(JmDNS.create(null, hostname));
            } else {
                for (InetAddress addr : addresses) {
                    publishers.add(JmDNS.create(addr, hostname));
                }
            }

        }

        public ServiceInfo register(String type, String domain, String name, int port, JSONObject props)
                throws JSONException, IOException {

            HashMap<String, String> txtRecord = new HashMap<String, String>();
            if (props != null) {
                Iterator<String> iter = props.keys();
                while (iter.hasNext()) {
                    String key = iter.next();
                    txtRecord.put(key, props.getString(key));
                }
            }

            ServiceInfo service = null;
            for (JmDNS publisher : publishers) {
                service = ServiceInfo.create(type + domain, name, port, 0, 0, txtRecord);
                publisher.registerService(service);
            }
            // returns only one of the ServiceInfo instances!
            return service;
        }

        public void unregister(String type, String domain, String name) {

            for (JmDNS publisher : publishers) {
                ServiceInfo serviceInfo = publisher.getServiceInfo(type + domain, name);
                if (serviceInfo != null) {
                    publisher.unregisterService(serviceInfo);
                }
            }

        }

        public void stop() throws IOException {

            for (JmDNS publisher : publishers) {
                publisher.close();
            }

        }

    }

    private class BrowserManager implements ServiceListener {

        private List<JmDNS> browsers = new ArrayList<JmDNS>();

        private Map<String, CallbackContext> callbacks = new HashMap<String, CallbackContext>();

        public BrowserManager(List<InetAddress> addresses, String hostname) throws IOException {

            if (addresses == null || addresses.size() == 0) {
                browsers.add(JmDNS.create(null, hostname));
            } else {
                for (InetAddress addr : addresses) {
                    browsers.add(JmDNS.create(addr, hostname));
                }
            }
        }

        private void watch(String type, String domain, CallbackContext callbackContext) {

            callbacks.put(type + domain, callbackContext);

            for (JmDNS browser : browsers) {

                browser.addServiceListener(type + domain, this);

                ServiceInfo[] services = browser.list(type + domain);
                for (ServiceInfo service : services) {
                    sendCallback("added", service);
                }

            }

        }

        private void unwatch(String type, String domain) {

            callbacks.remove(type + domain);

            for (JmDNS browser : browsers) {
                browser.removeServiceListener(type + domain, this);
            }

        }

        private void close() throws IOException {

            callbacks.clear();

            for (JmDNS browser : browsers) {
                browser.close();
            }

        }

        @Override
        public void serviceResolved(ServiceEvent ev) {
            Log.d(TAG, "Resolved");

            sendCallback("added", ev.getInfo());
        }

        @Override
        public void serviceRemoved(ServiceEvent ev) {
            Log.d(TAG, "Removed");

            sendCallback("removed", ev.getInfo());
        }

        @Override
        public void serviceAdded(ServiceEvent event) {
            Log.d(TAG, "Added");

            // Force serviceResolved to be called again
            for (JmDNS browser : browsers) {
                browser.requestServiceInfo(event.getType(), event.getName(), 1);
            }
        }

        public void sendCallback(String action, ServiceInfo service) {
            CallbackContext callbackContext = callbacks.get(service.getType());
            if (callbackContext == null) {
                return;
            }

            JSONObject status = new JSONObject();
            try {
                status.put("action", action);
                status.put("service", jsonifyService(service));

                Log.d(TAG, "Sending result: " + status.toString());

                PluginResult result = new PluginResult(PluginResult.Status.OK, status);
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);

            } catch (JSONException e) {
                e.printStackTrace();
                callbackContext.error("Error: " + e.getMessage());
            }
        }

    }

    private static JSONObject jsonifyService(ServiceInfo service) throws JSONException {
        JSONObject obj = new JSONObject();

        String domain = service.getDomain() + ".";
        obj.put("domain", domain);
        obj.put("type", service.getType().replace(domain, ""));
        obj.put("name", service.getName());
        obj.put("port", service.getPort());
        obj.put("hostname", service.getServer());

        JSONArray ipv4Addresses = new JSONArray();
        InetAddress[] inet4Addresses = service.getInet4Addresses();
        for (int i = 0; i < inet4Addresses.length; i++) {
            if (inet4Addresses[i] != null) {
                ipv4Addresses.put(inet4Addresses[i].getHostAddress());
            }
        }
        obj.put("ipv4Addresses", ipv4Addresses);

        JSONArray ipv6Addresses = new JSONArray();
        InetAddress[] inet6Addresses = service.getInet6Addresses();
        for (int i = 0; i < inet6Addresses.length; i++) {
            if (inet6Addresses[i] != null) {
                ipv6Addresses.put(inet6Addresses[i].getHostAddress());
            }
        }
        obj.put("ipv6Addresses", ipv6Addresses);

        JSONObject props = new JSONObject();
        Enumeration<String> names = service.getPropertyNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            props.put(name, service.getPropertyString(name));
        }
        obj.put("txtRecord", props);

        return obj;

    }

    // http://stackoverflow.com/questions/21898456/get-android-wifi-net-hostname-from-code
    public static String getHostName() throws NoSuchMethodException, SecurityException, IllegalAccessException,
            IllegalArgumentException, InvocationTargetException {
        Method getString = Build.class.getDeclaredMethod("getString", String.class);
        getString.setAccessible(true);
        return getString.invoke(null, "net.hostname").toString();
    }

}
