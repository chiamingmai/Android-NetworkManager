package com.github.chiamingmai.networkmanagerdemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.github.chiamingmai.networkmanager.NetworkManager;

public class WiFiNetworkFragment extends MyFragment {
    ViewHolder viewHolder;
    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            getStatus();
            switch (intent.getAction()) {
                case MainActivity.WIFI_ENABLED:
                    Toast.makeText(context, "WIFI_ENABLED", Toast.LENGTH_LONG).show();
                    MainActivity.networkManager.scanWifiAP();
                    break;
                case MainActivity.WIFI_DISABLED:
                    Toast.makeText(context, "WIFI_DISABLED", Toast.LENGTH_LONG).show();
                    break;
                case MainActivity.SCAN_WIFI:
                    String[] apInfo = intent.getStringArrayExtra(MainActivity.AP_LIST);
                    viewHolder.tvStatus.append("Scan Results: " + MainActivity.seperator);
                    int len = apInfo.length;
                    for (int i = 0; i < len; i++) {
                        viewHolder.tvStatus.append(apInfo[i] + MainActivity.seperator);
                    }
                    break;
                case MainActivity.WIFI_CONNECTED:
                    String ssid = intent.getStringExtra(MainActivity.SSID_KEY);
                    Toast.makeText(context, " WIFI_CONNECTED: " + ssid, Toast.LENGTH_LONG).show();
                    break;
                case MainActivity.WIFI_DISCONNECTED:
                    Toast.makeText(context, "WIFI_DISCONNECTED", Toast.LENGTH_LONG).show();
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setRetainInstance(true);
        View view = inflater.inflate(R.layout.wifi_frag_layout, container, false);
        Object tag = view.getTag();
        if (tag != null && tag instanceof ViewHolder) {
            viewHolder = (ViewHolder) tag;
        } else {
            viewHolder = new ViewHolder();
            ToggleButton networkBtn = (ToggleButton) view.findViewById(R.id.networkswitch);
            networkBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        MainActivity.networkManager.enableNetworkInterface(NetworkManager.NetworkType.WIFI);
                    } else {
                        MainActivity.networkManager.disableNetworkInterface(NetworkManager.NetworkType.WIFI);
                    }
                }
            });
            viewHolder.networkBtn = networkBtn;
            TextView tvStatus = (TextView) view.findViewById(R.id.tvStatus);
            tvStatus.setMovementMethod(new ScrollingMovementMethod());
            viewHolder.tvStatus = tvStatus;
            Button btnrefresh = (Button) view.findViewById(R.id.btnrefresh);
            btnrefresh.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    MainActivity.networkManager.scanWifiAP();
                    getStatus();
                }
            });
            viewHolder.btnrefresh = btnrefresh;
        }
        view.setTag(viewHolder);
        getStatus();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MainActivity.WIFI_ENABLED);
        intentFilter.addAction(MainActivity.WIFI_DISABLED);
        intentFilter.addAction(MainActivity.SCAN_WIFI);
        intentFilter.addAction(MainActivity.WIFI_CONNECTED);
        intentFilter.addAction(MainActivity.WIFI_DISCONNECTED);
        MainActivity.ctx.registerReceiver(receiver, intentFilter);
        return view;
    }

    void getStatus() {
        if (viewHolder != null) {
            StringBuilder str = new StringBuilder();
            boolean enabled = MainActivity.networkManager.isNetworkEnabled(NetworkManager.NetworkType.WIFI);
            viewHolder.networkBtn.setChecked(enabled);
            str.append("isWiFiEnabled: " + enabled + MainActivity.seperator);
            str.append("isWiFiConnected: " + MainActivity.networkManager.isNetworkConnected(NetworkManager.NetworkType.WIFI) + MainActivity.seperator);
            str.append(MainActivity.seperator + MainActivity.seperator);
            viewHolder.tvStatus.setText(str.toString());
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        viewHolder = null;
        MainActivity.ctx.unregisterReceiver(receiver);
    }

    public static class ViewHolder {
        TextView tvStatus = null;
        ToggleButton networkBtn = null;
        Button btnrefresh = null;
    }
}
