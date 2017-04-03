package com.github.chiamingmai.networkmanager;

import java.net.InetAddress;

public class WifiConnectionInfo {
    public String SSID;
    public int Rssi;
    public int networkID;
    public int linkSpeed;
    public InetAddress wifiHotspotAddress;
    public InetAddress ipAddress;

    void reset() {
        SSID = null;
        Rssi = 0;
        networkID = -1;
        linkSpeed = 0;
        wifiHotspotAddress = null;
        ipAddress = null;
    }

    @Override
    public String toString() {
        return "WifiConnectionInfo:[ SSID:" + SSID + " Network Id:" + networkID + " Rssi:" + Rssi + " IP Address:"
                + ipAddress.getHostAddress() + " Wifi AP Address:" + wifiHotspotAddress.getHostAddress()
                + " Link speed:" + linkSpeed + " ]";
    }

    WifiConnectionInfo() {
        reset();
    }
}