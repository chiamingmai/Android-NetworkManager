package com.github.chiamingmai.networkmanagerdemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.github.chiamingmai.networkmanager.NetworkManager;

public class CellularNetworkFragment extends MyFragment {
    ViewHolder viewHolder;
    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case MainActivity.MOBILE_ENABLED:
                    Toast.makeText(context, "MOBILE_ENABLED", Toast.LENGTH_LONG).show();
                    getStatus();
                    break;
                case MainActivity.MOBILE_CONNECTED:
                    Toast.makeText(context, "MOBILE_CONNECTED", Toast.LENGTH_LONG).show();
                    getStatus();
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setRetainInstance(true);
        View view = inflater.inflate(R.layout.cellular_frag_layout, container, false);
        Object tag = view.getTag();
        if (tag != null && tag instanceof ViewHolder) {
            viewHolder = (ViewHolder) tag;
        } else {
            viewHolder = new ViewHolder();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                Switch networkswitch = (Switch) view.findViewById(R.id.networkswitch);
                networkswitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            MainActivity.networkManager.enableNetworkInterface(NetworkManager.NetworkType.MOBILE);
                        } else {
                            MainActivity.networkManager.disableNetworkInterface(NetworkManager.NetworkType.MOBILE);
                        }
                    }
                });
                viewHolder.networkswitch = networkswitch;
            } else {
                ToggleButton networkBtn = (ToggleButton) view.findViewById(R.id.networkswitch);
                networkBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            MainActivity.networkManager.enableNetworkInterface(NetworkManager.NetworkType.MOBILE);
                        } else {
                            MainActivity.networkManager.disableNetworkInterface(NetworkManager.NetworkType.MOBILE);
                        }
                    }
                });
                viewHolder.networkBtn = networkBtn;
            }
            TextView tvStatus = (TextView) view.findViewById(R.id.tvStatus);
            tvStatus.setMovementMethod(new ScrollingMovementMethod());
            viewHolder.tvStatus = tvStatus;
            Button btnrefresh = (Button) view.findViewById(R.id.btnrefresh);
            btnrefresh.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    getStatus();
                }
            });
            viewHolder.btnrefresh = btnrefresh;
            view.setTag(viewHolder);
        }
        getStatus();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MainActivity.MOBILE_ENABLED);
        intentFilter.addAction(MainActivity.MOBILE_CONNECTED);
        MainActivity.ctx.registerReceiver(receiver, intentFilter);
        return view;
    }

    void getStatus() {
        if (viewHolder != null) {
            StringBuilder builder = new StringBuilder();
            int currentSDK = Build.VERSION.SDK_INT;
            if (currentSDK >= Build.VERSION_CODES.LOLLIPOP) {
                builder.append("Your current device's version ( " + currentSDK + " ) is higher than KitKat (4.x)." + MainActivity.seperator);
                builder.append("Cannot access the network programmatically.");
                builder.append(MainActivity.seperator + MainActivity.seperator);
            }

            builder.append("isCellularNetworkEnabled: ");
            boolean enabled = MainActivity.networkManager.isNetworkEnabled(NetworkManager.NetworkType.MOBILE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                viewHolder.networkswitch.setChecked(enabled);
            } else {
                viewHolder.networkBtn.setChecked(enabled);
            }
            builder.append(enabled);
            builder.append(MainActivity.seperator);

            builder.append("isCellularNetworkConnected: ");
            builder.append(MainActivity.networkManager.isNetworkConnected(NetworkManager.NetworkType.MOBILE));
            builder.append(MainActivity.seperator);
            viewHolder.tvStatus.setText(builder.toString());
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
        Switch networkswitch = null;
        ToggleButton networkBtn = null;
        Button btnrefresh = null;
    }
}
