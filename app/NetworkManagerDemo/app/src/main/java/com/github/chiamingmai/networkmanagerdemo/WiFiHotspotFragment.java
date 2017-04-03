package com.github.chiamingmai.networkmanagerdemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.github.chiamingmai.networkmanager.NetworkManager;

public class WiFiHotspotFragment extends MyFragment {
    ViewHolder viewHolder;
    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case MainActivity.HOTSPOT_ENABLED:
                    Toast.makeText(context, "HOTSPOT_ENABLED", Toast.LENGTH_LONG).show();
                    getStatus();
                    break;
                case MainActivity.HOTSPOT_DISABLED:
                    Toast.makeText(context, "HOTSPOT_DISABLED", Toast.LENGTH_LONG).show();
                    getStatus();
                    break;
                case MainActivity.HOTSPOT_CLIENT:
                    String[] clientInfo = intent.getStringArrayExtra(MainActivity.HOTSPOT_CLIENT);
                    getStatus();
                    viewHolder.tvStatus.append("Connected Clients: " + MainActivity.seperator);
                    int len = clientInfo.length;
                    for (int i = 0; i < len; i++) {
                        viewHolder.tvStatus.append(clientInfo[i]);
                    }

                    break;
                default:
                    break;
            }
        }
    };

    public static class ViewHolder {
        TextView tvStatus = null;
        ToggleButton networkSwitch = null;
        Button btnGetClient = null;
        CheckBox cBShowPwd = null;
        EditText eTSSID = null;
        EditText eTPassword = null;
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setRetainInstance(true);
        final View view = inflater.inflate(R.layout.wifihotspot_frag_layout, container, false);
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
                        String SSID = viewHolder.eTSSID.getText().toString();
                        String pwd = viewHolder.eTPassword.getText().toString();
                        if (!"".equals(SSID) && !"".equals(pwd))
                            MainActivity.networkManager.createWifiHotspot(SSID, pwd);
                        else {
                            Toast.makeText(MainActivity.ctx, "Please enter  SSID and Password", Toast.LENGTH_LONG).show();
                            viewHolder.networkSwitch.setChecked(false);
                        }
                    } else {
                        MainActivity.networkManager.disableNetworkInterface(NetworkManager.NetworkType.WIFI_HOTSPOT);
                    }
                }
            });
            viewHolder.networkSwitch = networkBtn;
            TextView tvStatus = (TextView) view.findViewById(R.id.tvStatus);
            tvStatus.setMovementMethod(new ScrollingMovementMethod());
            viewHolder.tvStatus = tvStatus;
            Button btnGetClient = (Button) view.findViewById(R.id.btnGetClient);
            btnGetClient.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    MainActivity.networkManager.getWifiHotspotClientList();
                }
            });
            viewHolder.btnGetClient = btnGetClient;
            CheckBox cBShowPwd = (CheckBox) view.findViewById(R.id.cBShowPwd);
            cBShowPwd.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        viewHolder.eTPassword.setTransformationMethod(new HideReturnsTransformationMethod());
                    } else {
                        viewHolder.eTPassword.setTransformationMethod(new PasswordTransformationMethod());
                    }
                }
            });
            viewHolder.cBShowPwd = cBShowPwd;
            viewHolder.eTSSID = (EditText) view.findViewById(R.id.eTSSID);
            viewHolder.eTPassword = (EditText) view.findViewById(R.id.eTPassword);
            view.setTag(viewHolder);
        }
        viewHolder.networkSwitch.setChecked(MainActivity.networkManager.isNetworkEnabled(NetworkManager.NetworkType.WIFI_HOTSPOT));
        viewHolder.eTSSID.setText(null);
        viewHolder.eTPassword.setText(null);
        viewHolder.cBShowPwd.setChecked(false);
        getStatus();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MainActivity.MOBILE_ENABLED);
        intentFilter.addAction(MainActivity.MOBILE_CONNECTED);
        intentFilter.addAction(MainActivity.HOTSPOT_CLIENT);
        MainActivity.ctx.registerReceiver(receiver, intentFilter);
        return view;
    }

    void getStatus() {
        StringBuilder str = new StringBuilder();
        str.append("isWiFiHotspotEnabled: " + MainActivity.networkManager.isNetworkEnabled(NetworkManager.NetworkType.WIFI_HOTSPOT));
        str.append(MainActivity.seperator + MainActivity.seperator);
        viewHolder.tvStatus.setText(str.toString());

    }

    @Override
    public void onDetach() {
        super.onDetach();
        viewHolder = null;
        MainActivity.ctx.unregisterReceiver(receiver);
    }
}
