package com.github.chiamingmai.networkmanagerdemo;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.wifi.ScanResult;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.github.chiamingmai.networkmanager.ConnectedClient;
import com.github.chiamingmai.networkmanager.NetworkManager;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    static final String seperator = System.getProperty("line.separator");
    static Context ctx;
    static NetworkManager networkManager; //create onty one instance in this app.
    static final String DTAG = "NetworkManager";
    static Resources res;

    public static final String MOBILE_ENABLED = "MOBILE_ENABLED";
    public static final String MOBILE_CONNECTED = "MOBILE_CONNECTED";
    public static final String HOTSPOT_ENABLED = "HOTSPOT_ENABLED";
    public static final String HOTSPOT_DISABLED = "HOTSPOT_DISABLED";
    public static final String HOTSPOT_CLIENT = "HOTSPOT_CLIENT";
    public static final String WIFI_ENABLED = "WIFI_ENABLED";
    public static final String WIFI_DISABLED = "WIFI_DISABLED";
    public static final String WIFI_CONNECTED = "WIFI_CONNECTED";
    public static final String WIFI_DISCONNECTED = "WIFI_DISCONNECTED";
    public static final String SCAN_WIFI = "SCAN_WIFI";
    public static final String SSID_KEY = "SSID_KEY";
    public static final String AP_LIST = "AP_LIST";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (ctx == null)
            ctx = this.getApplicationContext();
        if (res == null)
            res = ctx.getResources();

        if (networkManager == null)
            networkManager = new NetworkManager(ctx, new NetworkManager.NetworkManagerListener() {
                @Override
                public void onGetWifiHotspotClientListFinished(List<ConnectedClient> connectedClients) {
                    Log.d(DTAG, "onGetWifiHotspotClientListFinished: ");
                    Intent intent = new Intent(HOTSPOT_CLIENT);
                    int len = connectedClients.size();
                    String[] clientInfo = new String[len];
                    for (int i = 0; i < len; i++) {
                        ConnectedClient client = connectedClients.get(i);
                        StringBuilder str = new StringBuilder();
                        str.append("Client " + i + MainActivity.seperator);
                        str.append(" device: " + client.device + MainActivity.seperator);
                        str.append(" iPAddress: " + client.iPAddress + MainActivity.seperator);
                        str.append(" macAddress: " + client.macAddress + MainActivity.seperator);
                        str.append(" isReachable: " + client.isReachable + MainActivity.seperator);
                        str.append(MainActivity.seperator);
                        clientInfo[i] = str.toString();
                    }
                    intent.putExtra(HOTSPOT_CLIENT, clientInfo);
                    ctx.sendBroadcast(intent);
                }

                @Override
                public void onRemoveAllWifiNetworksFinished() {
                    Log.d(DTAG, "onRemoveAllWifiNetworksFinished");
                }

                @Override
                public void onScanWifiAccessPointsFinished(List<ScanResult> scanResults) {
                    Log.d(DTAG, "onScanWifiAccessPointsFinished");
                    int len = scanResults.size();
                    String[] apInfo = new String[len];
                    for (int i = 0; i < len; i++) {
                        apInfo[i] = scanResults.get(i).toString();
                    }
                    ctx.sendBroadcast(new Intent(SCAN_WIFI).putExtra(AP_LIST, apInfo));
                }

                @Override
                public void onWifiAccessPointConnected(String SSID) {
                    Log.d(DTAG, "onWifiAccessPointConnected:  SSID: " + SSID);
                    ctx.sendBroadcast(new Intent(WIFI_CONNECTED).putExtra(SSID_KEY, SSID));
                }

                @Override
                public void onWifiAccessPointDisconnected() {
                    Log.d(DTAG, "onWifiAccessPointDisconnected");
                    ctx.sendBroadcast(new Intent(WIFI_DISCONNECTED));
                }

                @Override
                public void onWifiHotspotDisabled() {
                    Log.d(DTAG, "onWifiHotspotDisabled");
                    ctx.sendBroadcast(new Intent(HOTSPOT_DISABLED));
                }

                @Override
                public void onWifiHotspotEnabled() {
                    Log.d(DTAG, "onWifiHotspotEnabled");
                    ctx.sendBroadcast(new Intent(HOTSPOT_ENABLED));
                }

                @Override
                public void onWifiNetworkDisabled() {
                    Log.d(DTAG, "onWifiNetworkDisabled");
                    ctx.sendBroadcast(new Intent(WIFI_DISABLED));
                }

                @Override
                public void onWifiNetworkEnabled() {
                    Log.d(DTAG, "onWifiNetworkEnabled");
                    ctx.sendBroadcast(new Intent(WIFI_ENABLED));
                }

                @Override
                public void onMobileNetworkEnabled() {
                    Log.d(DTAG, "onMobileNetworkEnabled");
                    ctx.sendBroadcast(new Intent(MOBILE_ENABLED));
                }

                @Override
                public void onMobileNetworkConnected() {
                    Log.d(DTAG, "onMobileNetworkConnected");
                    ctx.sendBroadcast(new Intent(MOBILE_CONNECTED));
                }
            });
        bindViews();
    }

    private void bindViews() {
        ViewPager pager = (ViewPager) findViewById(R.id.viewpager);
        TabPagerAdapter adapter = new TabPagerAdapter(getSupportFragmentManager());
        adapter.addItem(new CellularNetworkFragment(), res.getString(R.string.tab_cellularnetwork_config));
        adapter.addItem(new WiFiNetworkFragment(), res.getString(R.string.tab_wifinetwork_config));
        adapter.addItem(new WiFiHotspotFragment(), res.getString(R.string.tab_wifihotspot_config));
        pager.setAdapter(adapter);
        TabLayout tabs = (TabLayout) findViewById(R.id.tabs);
        tabs.setupWithViewPager(pager);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkManager != null)
            networkManager.terminate();
    }
}
