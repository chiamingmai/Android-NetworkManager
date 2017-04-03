package com.github.chiamingmai.networkmanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.NetworkInfo.State;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public final class NetworkManager {
    static final String TAG = "NetworkManager";
    static final int timeout = 10 * 1000;

    public enum NetworkType {
        NONE, MOBILE, WIFI, WIFI_HOTSPOT
    }

    public enum WIFI_HOTSPOT_STATE {
        DISABLING, DISABLED, ENABLING, ENABLED, FAILED
    }

    public static interface NetworkManagerListener {
        void onGetWifiHotspotClientListFinished(List<ConnectedClient> connectedClients);

        void onRemoveAllWifiNetworksFinished();

        void onScanWifiAccessPointsFinished(List<ScanResult> scanResults);

        void onWifiAccessPointConnected(String SSID);

        void onWifiAccessPointDisconnected();

        void onWifiHotspotDisabled();

        void onWifiHotspotEnabled();

        void onWifiNetworkDisabled();

        void onWifiNetworkEnabled();

        void onMobileNetworkEnabled();

        void onMobileNetworkConnected();
    }

    ConnectivityManager cm;
    WifiManager wm;
    BroadcastReceiver mBroadcastReceiver;
    Context mCtx;
    Class<?> cmClass, wmClass;
    static final IntentFilter filter;

    static {
        filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
    }

    HandlerThread process = new HandlerThread(TAG);
    Handler processHandler;
    NetworkManagerListener mListener;
    boolean requestScan = false, enableMobile = false;
    int scan_cnt = 0, existedNetID = -1;
    boolean isWifiAPConnecting = false, isWifiAPDisconnecting = false, isWifiHotspotCreating = false;
    WifiConfiguration AP_CONFIG = new WifiConfiguration();
    WifiConfiguration HOTSPOT_CONFIG = new WifiConfiguration();
    WifiConnectionInfo wCInfo = new WifiConnectionInfo();
    ArrayList<ConnectedClient> hotspotClients = new ArrayList<ConnectedClient>();
    ArrayList<ScanResult> scanResults = new ArrayList<ScanResult>();

    public NetworkManager(Context context) {
        this(context, null);
    }

    public NetworkManager(Context context, NetworkManagerListener listener) {
        if (context == null)
            throw new NullPointerException("context = null");
        mListener = listener;
        mCtx = context;
        cm = (ConnectivityManager) mCtx.getSystemService(Context.CONNECTIVITY_SERVICE);
        wm = (WifiManager) mCtx.getSystemService(Context.WIFI_SERVICE);
        cmClass = cm.getClass();
        wmClass = wm.getClass();
        prepareBroadcastReceiver();
        process.start();
        processHandler = new Handler(process.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                int what = msg.what/*, existedNetID*/;
                Object o = msg.obj;
                NetworkManagerAction action = null;
                if (o instanceof NetworkManagerAction)
                    action = (NetworkManagerAction) o;
                switch (what) {
                    case NetworkManagerAction.DISABLE_HOTSPOT:
                        while (getWifiHotspotState() != WIFI_HOTSPOT_STATE.DISABLED) {
                        }
                        if (mListener != null)
                            sendHandlerMessage(mainHandler, what, new NetworkManagerAction(mListener));
                        break;
                    case NetworkManagerAction.ENABLE_HOTSPOT:
                        while (!isWifiHotspotEnabled()) {
                        }
                        isWifiHotspotCreating = false;
                        sendHandlerMessage(mainHandler, what, action);
                        break;
                    case NetworkManagerAction.CONNECT_WIFI_AP:
                        enableNetworkInterface(NetworkType.WIFI);
                        while (!isNetworkEnabled(NetworkType.WIFI)) {
                        }
                        existedNetID = getExistedNetworkID(action.newSSID);
                        if (existedNetID >= 0)
                            wm.removeNetwork(existedNetID);
                        AP_CONFIG.SSID = fixString(action.newSSID);
                        AP_CONFIG.preSharedKey = fixString(action.SSIDPwd);
                        AP_CONFIG.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                        int netId = wm.addNetwork(AP_CONFIG);
                        if (netId >= 0) {
                            wm.enableNetwork(netId, true);
                            wm.reassociate();
                        }
                        break;
                    case NetworkManagerAction.DISCONNECT_WIFI_AP:
                        existedNetID = getExistedNetworkID(action.currSSID);
                        if (existedNetID > -1)
                            wm.removeNetwork(existedNetID);
                        if (!wm.disconnect()) {
                            if (mListener != null)
                                sendHandlerMessage(mainHandler, what, new NetworkManagerAction(mListener));
                        }
                        isWifiAPDisconnecting = false;
                        break;
                    case NetworkManagerAction.GET_HOTSPOT_CLIENTS:
                        if (mListener != null) {
                            getWifiHotspotClientList(hotspotClients, action.onlyReachable);
                            action.connectedClients = hotspotClients;
                            sendHandlerMessage(mainHandler, what, action);
                        }
                        break;
                    case NetworkManagerAction.SCAN_WIFI_AP:
                        if (!isNetworkEnabled(NetworkType.WIFI))
                            enableNetworkInterface(NetworkType.WIFI);
                        while (!isNetworkEnabled(NetworkType.WIFI)) {
                        }
                        wm.startScan();
                        break;
                    case NetworkManagerAction.REMOVE_WIFI_NETWORKS:
                        removeExistingNetworks();
                        if (mListener != null) {
                            sendHandlerMessage(mainHandler, what, new NetworkManagerAction(mListener));
                        }
                        break;
                    default:
                        break;
                }
            }
        };
        if (isWifiHotspotEnabled()) {
            if (mListener != null)
                sendHandlerMessage(mainHandler, NetworkManagerAction.ENABLE_HOTSPOT, new NetworkManagerAction(mListener));
        }
    }

    private void prepareBroadcastReceiver() {
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                    if (requestScan) {
                        if (mListener != null) {
                            scanResults.clear();
                            scanResults.addAll(wm.getScanResults());
                            NetworkManagerAction action = new NetworkManagerAction(mListener);
                            action.scanResults = scanResults;
                            sendHandlerMessage(mainHandler, NetworkManagerAction.SCAN_WIFI_AP, action);
                        }
                        requestScan = false;
                        scan_cnt = 0;
                    }
                }
                if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                    NetworkInfo wifiNetworkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                    if (wifiNetworkInfo != null) {
                        if (wifiNetworkInfo.isConnected()) {
                            if (isWifiAPConnecting) {
                                if (mListener != null) {
                                    NetworkManagerAction action = new NetworkManagerAction(mListener);
                                    action.newSSID = getWifiConnectionInfo().SSID;
                                    sendHandlerMessage(mainHandler, NetworkManagerAction.CONNECT_WIFI_AP, action);
                                }
                                isWifiAPConnecting = false;
                            }
                        } else if (wifiNetworkInfo.getState() == State.DISCONNECTED) {
                            if (mListener != null)
                                sendHandlerMessage(mainHandler, NetworkManagerAction.DISCONNECT_WIFI_AP, new NetworkManagerAction(mListener));
                            isWifiAPDisconnecting = false;
                        }
                    }
                }
                if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                    if (WifiManager.WIFI_STATE_ENABLED == wm.getWifiState()) {
                        if (mListener != null)
                            sendHandlerMessage(mainHandler, NetworkManagerAction.ENABLE_WIFI, new NetworkManagerAction(mListener));
                    } else if (WifiManager.WIFI_STATE_DISABLED == wm.getWifiState()) {
                        if (mListener != null)
                            sendHandlerMessage(mainHandler, NetworkManagerAction.DISABLE_WIFI, new NetworkManagerAction(mListener));
                    }
                }
                if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                    NetworkInfo info = cm.getActiveNetworkInfo();
                    if (info != null) {
                        DetailedState dstate = info.getDetailedState();
                        switch (info.getType()) {
                            case ConnectivityManager.TYPE_MOBILE:
                                if (isNetworkEnabled(NetworkType.MOBILE)) {
                                    if (mListener != null)
                                        sendHandlerMessage(mainHandler, NetworkManagerAction.MOBILE_ENABLED, new NetworkManagerAction(mListener));

                                }
                                switch (dstate) {
                                    case CONNECTED:
                                        if (mListener != null)
                                            sendHandlerMessage(mainHandler, NetworkManagerAction.MOBILE_CONNECTED, new NetworkManagerAction(mListener));
                                        enableMobile = false;
                                        break;
                                    case SUSPENDED:
                                    case FAILED:
                                        if (enableMobile)
                                            enableNetworkInterface(NetworkType.MOBILE);
                                        break;
                                    default:
                                        break;
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                                    if (dstate == DetailedState.BLOCKED) {
                                        if (enableMobile)
                                            enableNetworkInterface(NetworkType.MOBILE);
                                    }
                                }

                                break;
                            case ConnectivityManager.TYPE_WIFI:

                                break;
                            default:
                                break;
                        }
                    }
                }
            }
        };
        try {
            mCtx.unregisterReceiver(mBroadcastReceiver);
        } catch (Exception e) {
        }
        try {
            mCtx.registerReceiver(mBroadcastReceiver, filter);
        } catch (Exception e) {
        }
    }

    /**
     * Terminate this manager.
     */
    public void terminate() {
        try {
            mCtx.unregisterReceiver(mBroadcastReceiver);
        } catch (Exception e) {
        }
        try {
            process.quit();
        } catch (Exception e) {
        }
        processHandler.removeCallbacksAndMessages(null);
        mainHandler.removeCallbacksAndMessages(null);
    }

    /**
     * Indicate whether this manager is terminated or not.
     *
     * @return
     */
    public boolean isTerminated() {
        return !process.isAlive();
    }

    public WifiManager getWifiManager() {
        return wm;
    }

    /**
     * Scan the nearby Wifi APs.
     */
    public void scanWifiAP() {
        if (isTerminated())
            return;
        if (requestScan) {
            if (scan_cnt++ <= 2)
                return;
        }
        scan_cnt = 0;
        requestScan = true;
        sendHandlerMessage(processHandler, NetworkManagerAction.SCAN_WIFI_AP, null);
    }

    /**
     * Enable the network interface with given network type.
     *
     * @param type network type
     * @return {@code true} if the operation succeeds
     */
    public boolean enableNetworkInterface(NetworkType type) {
        if (type == null)
            throw new NullPointerException("type == null");
        //if (isNetworkEnabled(type))
        //return true;
        switch (type) {
            case MOBILE:
                // fix for android 5.0 upper
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                } else {
                    // works for < android 4.x
                    try {
                        Method method = cmClass.getDeclaredMethod("setMobileDataEnabled", boolean.class);
                        method.setAccessible(true);
                        return (Boolean) method.invoke(cm, true);
                    } catch (Exception e) {
                        return false;
                    }
                }
                enableMobile = true;
                break;
            case WIFI:
                return setWifiHotspotEnabled(null, false) && wm.setWifiEnabled(true);
            case WIFI_HOTSPOT:
                boolean result = setWifiHotspotEnabled(HOTSPOT_CONFIG, true);
                sendHandlerMessage(processHandler, NetworkManagerAction.ENABLE_HOTSPOT, new NetworkManagerAction(mListener));
                return result;
            default:
                break;
        }
        return false;
    }

    /**
     * Disable the network interface with the given network type.
     *
     * @param type network type
     * @return {@code true} if the operation succeeds; {@code false} otherwise.
     */
    public boolean disableNetworkInterface(NetworkType type) {
        if (type == null)
            throw new NullPointerException("type == null");
        if (NetworkType.WIFI_HOTSPOT == type && (getWifiHotspotState() == WIFI_HOTSPOT_STATE.DISABLED)) {
            if (mListener != null)
                sendHandlerMessage(mainHandler, NetworkManagerAction.DISABLE_HOTSPOT, new NetworkManagerAction(mListener));
            return true;
        }
        boolean result = false;
        switch (type) {
            case MOBILE:
                try {
                    Method method = cmClass.getDeclaredMethod("setMobileDataEnabled", boolean.class);
                    method.setAccessible(true); // Make the method callable
                    // get the setting for "mobile data"
                    result = (Boolean) method.invoke(cm, false);
                } catch (Exception e) {
                    result = false;
                }
                enableMobile = false;
                break;
            case WIFI:
                result = wm.setWifiEnabled(false);
                break;
            case WIFI_HOTSPOT:
                result = setWifiHotspotEnabled(null, false);
                sendHandlerMessage(processHandler, NetworkManagerAction.DISABLE_HOTSPOT, null);
                break;
            default:
                break;
        }
        return result;
    }

    /**
     * Return MAC address of current device.
     *
     * @return MAC address in String format
     */
    public static String getMacAddress() {
        // WifiInfo info = wm.getConnectionInfo();
        // return (info == null) ? null : info.getMacAddress();
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            if (interfaces.size() > 0) {
                for (NetworkInterface intf : interfaces) {
                    if (intf.getName().contains("wlan")) {
                        byte[] macAddr = intf.getHardwareAddress();
                        if (macAddr != null) {
                            String mac = "";
                            for (int i = 0; i < macAddr.length; i++) {
                                mac += String.format("%02x%s", macAddr[i], (i < macAddr.length - 1) ? ":" : "");
                            }
                            return mac;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Get MacAddress Error:" + e.getMessage(), e);
        }
        return null;
    }

    public static String getIPAddress(NetworkType type, boolean getIPv4) {
        if (type == null)
            return null;
        String interface_name = null;
        switch (type) {
            case MOBILE:
                interface_name = "rmnet";
                break;
            case WIFI_HOTSPOT:
            case WIFI:
                interface_name = "wlan";
                break;
            default:
                return null;
        }
        try {
            Enumeration<NetworkInterface> intfs = NetworkInterface.getNetworkInterfaces();
            while (intfs.hasMoreElements()) {
                NetworkInterface intf = intfs.nextElement();
                if (intf.getName().contains(interface_name)) {
                    Enumeration<InetAddress> eIP = intf.getInetAddresses();
                    while (eIP.hasMoreElements()) {
                        InetAddress addr = eIP.nextElement();
                        if (!addr.isLoopbackAddress()) {
                            String sAddr = addr.getHostAddress().toUpperCase(Locale.getDefault());
                            //boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
                            boolean isIPv4 = sAddr.indexOf(':') < 0;
                            if (getIPv4) {
                                if (isIPv4)
                                    return new String(sAddr);
                            } else {
                                if (!isIPv4) {
                                    int delim = sAddr.indexOf('%'); // drop
                                    // ip6
                                    // port
                                    // suffix
                                    return new String(delim < 0 ? sAddr : sAddr.substring(0, delim));
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error when getting IPAddress:" + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Convert a IPv4 address from an integer to an InetAddress.
     *
     * @param hostAddress hostAddress an int corresponding to the IPv4 address in
     *                    network byte order
     */
    public static InetAddress intToInetAddress(int hostAddress) {
        byte[] addressBytes = {(byte) (0xff & hostAddress), (byte) (0xff & (hostAddress >> 8)),
                (byte) (0xff & (hostAddress >> 16)), (byte) (0xff & (hostAddress >> 24))};
        try {
            return InetAddress.getByAddress(addressBytes);
        } catch (UnknownHostException e) {
            throw new AssertionError();
        }
    }

    /**
     * Convert a IPv4 address from an InetAddress to an integer
     *
     * @param ipv4Addr an InetAddress corresponding to the IPv4 address
     * @return the IP address as an integer in network byte order
     */
    public static int inetAddressToInt(Inet4Address ipv4Addr) {
        if (ipv4Addr == null)
            throw new NullPointerException("ipv4Addr == null");
        byte[] addr = ipv4Addr.getAddress();
        return ((addr[3] & 0xff) << 24) | ((addr[2] & 0xff) << 16) | ((addr[1] & 0xff) << 8) | (addr[0] & 0xff);
    }

    /**
     * Get Wi-Fi connection information.
     */
    public WifiConnectionInfo getWifiConnectionInfo() {
        wCInfo.reset();
        DhcpInfo dhcpInfo = wm.getDhcpInfo();
        WifiInfo wifiInfo = wm.getConnectionInfo();
        if (dhcpInfo != null) {
            wCInfo.wifiHotspotAddress = intToInetAddress(dhcpInfo.serverAddress);
            wCInfo.ipAddress = intToInetAddress(dhcpInfo.ipAddress);
        }
        if (wifiInfo != null) {
            wCInfo.linkSpeed = wifiInfo.getLinkSpeed();
            wCInfo.Rssi = wifiInfo.getRssi();
            String ssid = wifiInfo.getSSID();
            wCInfo.SSID = ssid == null ? "" : ssid.replace("\"", "");
            wCInfo.networkID = wifiInfo.getNetworkId();
        }
        return wCInfo;
    }

    private static String fixString(String str) {
        if (str == null || str.isEmpty())
            str = "\"\"";
        else {
            if ('\"' != str.charAt(0))
                str = '\"' + str;
            if ('\"' != str.charAt(str.length() - 1))
                str = str + '\"';
        }
        return new String(str);
    }

    /**
     * Get existed network ID with the given SSID.
     * <p>
     * Be sure that WI-FI interface has been enabled before calling this method;
     * otherwise, it will return -1 even if the netowrk ID is not -1.
     *
     * @param SSID the network's SSID
     * @return the network ID of ssid
     */
    int getExistedNetworkID(String SSID) {
        if (SSID == null || SSID.isEmpty())
            return -1;
        enableNetworkInterface(NetworkType.WIFI);
        SSID = fixString(SSID);
        List<WifiConfiguration> configuredWifiList;
        try {
            configuredWifiList = wm.getConfiguredNetworks();
            if (null != configuredWifiList && configuredWifiList.size() > 0) {
                WifiConfiguration config = null;
                Iterator<WifiConfiguration> iterator = configuredWifiList.iterator();
                while (iterator.hasNext()) {
                    config = iterator.next();
                    if (config.SSID.equals(SSID))
                        break;
                }
                return (config == null) ? -1 : config.networkId;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            configuredWifiList = null;
        }
        return -1;
    }

    private void removeExistingNetworks() {
        try {
            List<WifiConfiguration> configuredWifiList = wm.getConfiguredNetworks();
            if (null != configuredWifiList) {
                int size = configuredWifiList.size();
                for (int i = 0; i < size; i++) {
                    WifiConfiguration config = configuredWifiList.get(i);
                    wm.removeNetwork(config.networkId);
                }
            }
        } catch (Exception e) {
        }
    }

    public void removeAllWifiNetworks() {
        sendHandlerMessage(processHandler, NetworkManagerAction.REMOVE_WIFI_NETWORKS, null);
    }

    /**
     * Create a WiFi hotspot with SSID and password.
     */
    public void createWifiHotspot(String SSID, String pwd) {
        if (isWifiHotspotCreating)
            return;
        if (SSID == null)
            throw new NullPointerException("ssid == null");
        if (pwd == null)
            throw new NullPointerException("pwd == null");
        if (SSID.isEmpty())
            throw new NullPointerException("ssid is empty");
        if (pwd.isEmpty())
            throw new NullPointerException("pwd is empty");
        isWifiHotspotCreating = true;
        HOTSPOT_CONFIG.SSID = SSID;
        HOTSPOT_CONFIG.preSharedKey = pwd;
        HOTSPOT_CONFIG.status = WifiConfiguration.Status.ENABLED;
        HOTSPOT_CONFIG.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
        HOTSPOT_CONFIG.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        setWifiHotspotEnabled(null, false);
        setWifiHotspotConfiguration(HOTSPOT_CONFIG);
        enableNetworkInterface(NetworkType.WIFI_HOTSPOT);
    }

    /**
     * Connect with the designated Wifi AP.
     *
     * @param SSID         A SSID to connect with.
     * @param SSIDPassword A SSID's password.
     * @return {@code true} if the operation succeeds; {@code false} otherwise.
     */
    public boolean connectToWifiAP(String SSID, String SSIDPassword) {
        if (isWifiAPConnecting || isTerminated())
            return false;
        if (SSID == null)
            throw new NullPointerException("SSID == null");
        if (SSID.isEmpty())
            throw new NullPointerException("SSID is empty");
        if (isNetworkConnected(NetworkType.WIFI) && getWifiConnectionInfo().SSID.equals(SSID)) {
            isWifiAPConnecting = false;
            return false;
        }
        NetworkManagerAction action = new NetworkManagerAction();
        action.newSSID = SSID;
        action.SSIDPwd = SSIDPassword;
        action.callback = mListener;
        isWifiAPConnecting = true;
        sendHandlerMessage(processHandler, NetworkManagerAction.CONNECT_WIFI_AP, action);
        return true;
    }

    /**
     * Disconnect with the currently connected Wifi AP.
     *
     * @return {@code true} if the operation succeeds; {@code false} otherwise.
     */
    public boolean disconnectWifiAP() {
        if (isTerminated())
            return false;
        if (!isNetworkEnabled(NetworkType.WIFI)/* || !isNetworkConnected(NetworkType.WIFI)*/) {
            // the Wifi interface is not enabled.
            isWifiAPDisconnecting = false;
            return true;
        }
        if (isWifiAPDisconnecting)
            return true;
        isWifiAPDisconnecting = true;

        NetworkManagerAction action = new NetworkManagerAction();
        action.currSSID = getWifiConnectionInfo().SSID;
        sendHandlerMessage(processHandler, NetworkManagerAction.DISCONNECT_WIFI_AP, action);
        return true;
    }

    /**
     * Start AccessPoint mode with the specified configuration. If the radio is
     * already running in AP mode, update the new configuration Note that
     * starting in access point mode disables station mode operation
     *
     * @param wifiConfig SSID, security and channel details as part of
     *                   WifiConfiguration
     * @return {@code true} if the operation succeeds, {@code false} otherwise
     */
    boolean setWifiHotspotEnabled(WifiConfiguration wifiConfig, boolean enabled) {
        try {
            if (enabled)
                wm.setWifiEnabled(false);
            Method method = wmClass.getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            return (Boolean) method.invoke(wm, wifiConfig, enabled);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get the Wi-Fi enabled state.
     *
     * @return {@link WIFI_HOTSPOT_STATE}
     * @see #isWifiHotspotEnabled()
     */
    public WIFI_HOTSPOT_STATE getWifiHotspotState() {
        try {
            Method method = wmClass.getMethod("getWifiApState");
            int tmp = (Integer) method.invoke(wm);
            // Fix for Android 4
            if (tmp >= 10)
                tmp = tmp - 10;
            return WIFI_HOTSPOT_STATE.class.getEnumConstants()[tmp];
        } catch (Exception e) {
            // e.printStackTrace();
            return WIFI_HOTSPOT_STATE.FAILED;
        }
    }

    /**
     * Check whether the network is enabled or not.
     *
     * @param type network type of {@link NetworkType}
     * @return {@code true} if the network is enabled, {@code false} otherwise
     */
    public boolean isNetworkEnabled(NetworkType type) {
        if (type == null)
            throw new NullPointerException("type == null");
        switch (type) {
            case MOBILE:
                try {
                    Method method = cmClass.getDeclaredMethod("getMobileDataEnabled");
                    method.setAccessible(true); // Make the method callable
                    // get the setting for "mobile data"
                    return (Boolean) method.invoke(cm);
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            case WIFI:
                return wm.isWifiEnabled();
            case WIFI_HOTSPOT:
                return isWifiHotspotEnabled();
            default:
                break;
        }
        return false;
    }

    /**
     * Check whether the network is connected or not.
     *
     * @param type network type of {@link NetworkType}
     * @return {@code true} if the network is connected, {@code false} otherwise
     */
    public boolean isNetworkConnected(NetworkType type) {
        if (type == null)
            throw new NullPointerException("type == null");
        switch (type) {
            case MOBILE:
                return cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).isConnected();
            case WIFI:
                return cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected();
            case WIFI_HOTSPOT:
                return isWifiHotspotEnabled();
            default:
                break;
        }
        return false;
    }

    /**
     * Return whether Wi-Fi Hotspot(AP) is enabled or disabled.
     *
     * @return {@code true} if Wi-Fi AP is enabled
     * @see #getWifiHotspotState()
     */
    boolean isWifiHotspotEnabled() {
        return getWifiHotspotState() == WIFI_HOTSPOT_STATE.ENABLED;
    }

    /**
     * Get the WiFi Hotspot(AP) Configuration.
     *
     * @return AP details in {@link WifiConfiguration}
     */
    public WifiConfiguration getWifiHotspotConfiguration() {
        try {
            Method method = wmClass.getMethod("getWifiApConfiguration");
            return (WifiConfiguration) method.invoke(wm);
        } catch (Exception e) {
            // e.printStackTrace();
            return null;
        }
    }

    public static double calculateDistance(double signalLevelInDb) {
        return calculateDistance(signalLevelInDb, 2412);
    }

    public static double calculateDistance(double signalLevelInDb, double freqInMHz) {
        // distance(meters) frequency(mhz)
        // http://rvmiller.com/2013/05/part-1-wifi-based-trilateration-on-android/
        double exp = (27.55 - (20 * Math.log10(freqInMHz)) + Math.abs(signalLevelInDb)) / 20.0;
        return Math.pow(10.0, exp);
    }

    /**
     * Set the WiFi Hotspot(AP) Configuration.
     *
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     */
    boolean setWifiHotspotConfiguration(WifiConfiguration wifiConfig) {
        try {
            Method method = wmClass.getMethod("setWifiApConfiguration", WifiConfiguration.class);
            return (Boolean) method.invoke(wm, wifiConfig);
        } catch (Exception e) {
            // e.printStackTrace();
            return false;
        }
    }

    /**
     * Get the WiFi hotspot's connected clients which are reachable.
     */
    public void getWifiHotspotClientList() {
        getWifiHotspotClientList(true);
    }

    /**
     * Get the WiFi hotspot's connected clients.
     *
     * @param onlyReachable {@code false} if the list should contain unreachable (probably
     *                      disconnected) clients, {@code true} otherwise
     */
    public void getWifiHotspotClientList(boolean onlyReachable) {
        if (mListener != null) {
            NetworkManagerAction action = new NetworkManagerAction(mListener);
            action.onlyReachable = onlyReachable;
            sendHandlerMessage(processHandler, NetworkManagerAction.GET_HOTSPOT_CLIENTS, action);
        }
    }

    static void sendHandlerMessage(Handler h, int actionType, NetworkManagerAction action) {
        h.obtainMessage(actionType, action).sendToTarget();
    }

    /**
     * Get a list of the clients connected to the Hotspot, reachable timeout is
     * 300
     *
     * @param onlyReachables {@code false} if the list should contain unreachable (probably
     *                       disconnected) clients, {@code true} otherwise
     */
    static void getWifiHotspotClientList(List<ConnectedClient> clientList, boolean onlyReachables) {
        getWifiHotspotClientList(clientList, onlyReachables, 300);
    }

    /**
     * Get a list of the clients connected to the Hotspot
     *
     * @param onlyReachables   {@code false} if the list should contain unreachable (probably
     *                         disconnected) clients, {@code true} otherwise
     * @param reachableTimeout Reachable Timout in miliseconds
     */
    static void getWifiHotspotClientList(List<ConnectedClient> clientList, boolean onlyReachables,
                                         int reachableTimeout) {
        BufferedReader br = null;
        String line;
        clientList.clear();
        try {
            br = new BufferedReader(new FileReader("/proc/net/arp"));
            while ((line = br.readLine()) != null) {
                String[] splitted = line.split(" +");
                if ((splitted != null) && (splitted.length >= 4)) {
                    // Basic sanity check
                    String mac = splitted[3];
                    if (mac.matches("..:..:..:..:..:..")) {
                        boolean isReachable = InetAddress.getByName(splitted[0]).isReachable(reachableTimeout);
                        if (!onlyReachables || isReachable)
                            clientList.add(new ConnectedClient(splitted[0], splitted[3], splitted[5], isReachable));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error when getting WifiHotspot Client List:" + e.getMessage(), e);
        } finally {
            try {
                br.close();
            } catch (IOException e) {
            }
            br = null;
            line = null;
        }
    }

    private Handler mainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            int what = msg.what;
            Object obj = msg.obj;
            if (obj instanceof NetworkManagerAction) {
                NetworkManagerAction action = (NetworkManagerAction) obj;
                if (action.callback != null) {
                    switch (what) {
                        case NetworkManagerAction.ENABLE_HOTSPOT:
                            action.callback.onWifiHotspotEnabled();
                            break;
                        case NetworkManagerAction.DISABLE_HOTSPOT:
                            action.callback.onWifiHotspotDisabled();
                            break;
                        case NetworkManagerAction.ENABLE_WIFI:
                            action.callback.onWifiNetworkEnabled();
                            break;
                        case NetworkManagerAction.DISABLE_WIFI:
                            action.callback.onWifiNetworkDisabled();
                            break;
                        case NetworkManagerAction.CONNECT_WIFI_AP:
                            action.callback.onWifiAccessPointConnected(action.newSSID);
                            break;
                        case NetworkManagerAction.DISCONNECT_WIFI_AP:
                            action.callback.onWifiAccessPointDisconnected();
                            break;
                        case NetworkManagerAction.GET_HOTSPOT_CLIENTS:
                            action.callback.onGetWifiHotspotClientListFinished(action.connectedClients);
                            break;
                        case NetworkManagerAction.SCAN_WIFI_AP:
                            action.callback.onScanWifiAccessPointsFinished(action.scanResults);
                            break;
                        case NetworkManagerAction.REMOVE_WIFI_NETWORKS:
                            action.callback.onRemoveAllWifiNetworksFinished();
                            break;
                        case NetworkManagerAction.MOBILE_ENABLED:
                            action.callback.onMobileNetworkEnabled();
                            break;
                        case NetworkManagerAction.MOBILE_CONNECTED:
                            action.callback.onMobileNetworkConnected();
                            break;
                        default:
                            break;
                    }
                }
            }
        }
    };

    private static final class NetworkManagerAction {
        static final int DISABLE_HOTSPOT = 1;
        static final int ENABLE_HOTSPOT = 2;
        static final int DISABLE_WIFI = 3;
        static final int ENABLE_WIFI = 4;
        static final int CONNECT_WIFI_AP = 5;
        static final int DISCONNECT_WIFI_AP = 6;
        static final int GET_HOTSPOT_CLIENTS = 7;
        static final int SCAN_WIFI_AP = 8;
        static final int REMOVE_WIFI_NETWORKS = 9;
        static final int MOBILE_ENABLED = 10;
        static final int MOBILE_CONNECTED = 11;

        ArrayList<ConnectedClient> connectedClients;
        ArrayList<ScanResult> scanResults;
        String currSSID, newSSID, SSIDPwd;
        boolean onlyReachable;
        NetworkManagerListener callback;

        NetworkManagerAction() {
            this(null);
        }

        NetworkManagerAction(NetworkManagerListener callback) {
            this.callback = callback;
        }
    }
}