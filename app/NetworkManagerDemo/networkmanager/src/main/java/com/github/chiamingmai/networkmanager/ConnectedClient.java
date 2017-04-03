package com.github.chiamingmai.networkmanager;

public class ConnectedClient {
    public String iPAddress;
    public String macAddress;
    public String device;
    public boolean isReachable;

    ConnectedClient(String ipAddr, String macAddr, String device, boolean isReachable) {
        iPAddress = ipAddr;
        macAddress = macAddr;
        this.device = device;
        this.isReachable = isReachable;
    }
}