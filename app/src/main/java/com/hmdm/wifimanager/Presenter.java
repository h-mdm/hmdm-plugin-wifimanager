/*
 * Headwind MDM: Open Source Android MDM Software
 * https://h-mdm.com
 *
 * WiFi Manager Plugin
 *
 * Copyright (C) 2020 Headwind Solutions LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hmdm.wifimanager;

import static android.net.wifi.WifiManager.ERROR_AUTHENTICATING;
import static android.net.wifi.WifiManager.EXTRA_RESULTS_UPDATED;
import static android.net.wifi.WifiManager.NETWORK_STATE_CHANGED_ACTION;
import static android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION;
import static android.net.wifi.WifiManager.SUPPLICANT_STATE_CHANGED_ACTION;
import static android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION;
import static android.net.wifi.WifiManager.WIFI_STATE_DISABLED;
import static android.net.wifi.WifiManager.WIFI_STATE_ENABLED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.MacAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.hmdm.MDMService;
import com.hmdm.wifimanager.model.AllowedItem;
import com.hmdm.wifimanager.model.HiddenWiFiItem;
import com.hmdm.wifimanager.model.MDMConfig;
import com.hmdm.wifimanager.model.WiFiItem;
import com.hmdm.wifimanager.ui.fragments.IMainView;
import com.hmdm.wifimanager.ui.fragments.IParamsView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Presenter {
    private final static String TAG = "HeadwindWiFi";
    /**
     * Interval between scans.
     */
    private static int SCAN_DELAY = 5 * 1000;
    /**
     * Receiver to get the network scanning results.
     */
    class ScanReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                boolean resultsUpdated = intent.getBooleanExtra(EXTRA_RESULTS_UPDATED, false);
                MDMService.Log.d(TAG, "ScanReceiver; onReceive(); resultsUpdated: " + resultsUpdated);

                // Refresh data only if there are new results or this is the first scan
                if (resultsUpdated || lastScanSSIDMap == null || lastScanBSSIDMap == null) {
                    lastScanSSIDMap = createSSIDMap(wifiManager.getScanResults());
                    lastScanBSSIDMap = createBSSIDMap(wifiManager.getScanResults());
                    MDMService.Log.d(TAG, "ScanReceiver; onReceive(); lastScan.size(): " + lastScanSSIDMap.size());

                    updateConnectedWiFiNetwork();
                    if (iMainView != null) iMainView.onScanComplete(createList());
                    if (iParamsView != null) iParamsView.onParamsResults(lastScanSSIDMap, connectionInfo, connectedState);
                }
            }
            else {
                lastScanSSIDMap = createSSIDMap(wifiManager.getScanResults());
                lastScanBSSIDMap = createBSSIDMap(wifiManager.getScanResults());
                MDMService.Log.d(TAG, "ScanReceiver; onReceive(); lastScan.size(): " + lastScanSSIDMap.size());

                updateConnectedWiFiNetwork();
                if (iMainView != null) iMainView.onScanComplete(createList());
                if (iParamsView != null) iParamsView.onParamsResults(lastScanSSIDMap, connectionInfo, connectedState);
            }

            // Schedule next scan
            handler.removeCallbacksAndMessages(null);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startScan();
                }
            }, SCAN_DELAY);
        }
    }

    /**
     * Receiver to get the network states.
     */
    class ConnectionStateReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(NETWORK_STATE_CHANGED_ACTION)) {
                MDMService.Log.d(TAG, "ConnectionStateReceiver; onReceive(); NETWORK_STATE_CHANGED_ACTION;");

                NetworkInfo netInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (netInfo != null && ConnectivityManager.TYPE_WIFI == netInfo.getType()) {
                    MDMService.Log.d(TAG, "ConnectionStateReceiver; onReceive(); NETWORK_STATE_CHANGED_ACTION; netInfo: " + netInfo.toString());

                    connectedState = netInfo.getState();

                    if (netInfo.isConnected()) {
                        tryConnectToId = -1;
                        tryConnectToSSID = "";
                        tryConnectToBSSID = "";

                        updateConnectedWiFiNetwork();
                        if (iMainView != null)
                            iMainView.onScanComplete(createList());
                        if (iParamsView != null)
                            iParamsView.onParamsResults(lastScanSSIDMap, connectionInfo, connectedState);
                    }
                }
            }
            else if (intent.getAction().equals(SUPPLICANT_STATE_CHANGED_ACTION)) {
                SupplicantState newState = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
                int error = intent.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, -1);

                if (tryConnectToId != -1) {
                    WifiConfiguration config = searchConfigured(tryConnectToId);

                    if (config != null) {
                        MDMService.Log.d(TAG, "ConnectionStateReceiver; onReceive(); SUPPLICANT_STATE_CHANGED_ACTION; newState: "
                                + newState.toString() + "; error: " + error + "; tryConnectToId: " + tryConnectToId + "; (" + config.SSID + "); tryConnectToSSID: " + tryConnectToSSID);
                    }
                    else {
                        MDMService.Log.d(TAG, "ConnectionStateReceiver; onReceive(); SUPPLICANT_STATE_CHANGED_ACTION; newState: "
                                + newState.toString() + "; error: " + error + "; tryConnectToId: " + tryConnectToId + "; tryConnectToSSID: " + tryConnectToSSID);
                    }
                }
                else {
                    MDMService.Log.d(TAG, "ConnectionStateReceiver; onReceive(); SUPPLICANT_STATE_CHANGED_ACTION; newState: "
                            + newState.toString() + "; error: " + error + "; tryConnectToId: " + tryConnectToId + "; tryConnectToSSID: " + tryConnectToSSID);
                }

                if (newState == SupplicantState.DISCONNECTED) {
                    if (error == ERROR_AUTHENTICATING) {
                        if (tryConnectToId != -1) {
                            WifiConfiguration config = searchConfigured(tryConnectToId);

                            if (iParamsView != null)
                                iParamsView.onConnectionError(error, config == null ? "" : config.SSID);

                            // Forget the network
                            wifiManager.disableNetwork(tryConnectToId);
                            wifiManager.removeNetwork(tryConnectToId);

                            if (lastConfig != null && lastConfig.allowed != null && !TextUtils.isEmpty(tryConnectToSSID) && !TextUtils.isEmpty(tryConnectToBSSID)) {
                                for (AllowedItem item : lastConfig.allowed) {
                                    if (tryConnectToSSID.equalsIgnoreCase(item.ssid) || tryConnectToBSSID.equalsIgnoreCase(item.bssid))
                                        item.wrongPassword = true;
                                }

                                tryConnectToSSID = "";
                                tryConnectToBSSID = "";
                            }

                            tryConnectToId = -1;
                        }
                    }
                    else if (tryConnectToId != -1) {
                        // Workaround!
                        // On some devices, the state SupplicantState.DISCONNECTED may occasionally sent during the connection
                        // To exclude wrong handling of the series of states during the connection attempt,
                        // I introduced the parameter tryAttempts.
                        // Here we ignore SupplicantState.DISCONNECTED 3 times during the connection
                        if (tryAttempts < 3)
                            tryAttempts++;
                        else {
                            tryAttempts = 0;

                            WifiConfiguration config = searchConfigured(tryConnectToId);

                            if (iParamsView != null)
                                iParamsView.onConnectionError(error, config == null ? "" : config.SSID);

                            // Forget the network
                            wifiManager.disableNetwork(tryConnectToId);
                            wifiManager.removeNetwork(tryConnectToId);

                            if (lastConfig != null && lastConfig.allowed != null && !TextUtils.isEmpty(tryConnectToSSID) && !TextUtils.isEmpty(tryConnectToBSSID)) {
                                for (AllowedItem item : lastConfig.allowed) {
                                    if (tryConnectToSSID.equalsIgnoreCase(item.ssid) || tryConnectToBSSID.equalsIgnoreCase(item.bssid))
                                        item.wrongPassword = true;
                                }

                                tryConnectToSSID = "";
                                tryConnectToBSSID = "";
                            }

                            tryConnectToId = -1;
                        }
                    }
                }
            }
            else if (intent.getAction().equals(WIFI_STATE_CHANGED_ACTION)) {
                int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                if (state != lastWiFiState) {
                    lastWiFiState = state;

                    MDMService.Log.d(TAG, "ConnectionStateReceiver; onReceive(); WIFI_STATE_CHANGED_ACTION; state: " + Utils.formatWiFiState(state)
                            + "; lastWiFiState: " + Utils.formatWiFiState(lastWiFiState));

                    switch (state) {
                        case WIFI_STATE_ENABLED:
                            handler.removeCallbacksAndMessages(null);
                            startScan();
                            if (iMainView != null) iMainView.onSetWiFiState(true);
                            break;
                        case WIFI_STATE_DISABLED:
                            startScanTime = 0;
                            if (iMainView != null) iMainView.onSetWiFiState(false);
                            break;
                    }
                }
            }
        }
    }

    private static final Presenter instance = new Presenter();

    private Handler handler = new Handler();
    private WifiManager wifiManager;
    /**
     * Interface for MainFragment events.
     */
    private IMainView iMainView;
    /**
     * Interface for ParamsFragment events.
     */
    private IParamsView iParamsView;
    private ScanReceiver scanReceiver;
    private ConnectionStateReceiver connectionStateReceiver;
    /**
     * List for latest scanning results.
     */
    private Map<String, WiFiItem> lastScanSSIDMap;
    private Map<String, WiFiItem> lastScanBSSIDMap;
    private long startScanTime = 0;
    private int lastWiFiState;
    /**
     * Latest configuration of Headwind MDM.
     */
    private MDMConfig lastConfig;
    private WifiInfo connectionInfo;
    private NetworkInfo.State connectedState;
    /**
     * id of the network we're currently connecting.
     */
    private int tryConnectToId = -1;
    /**
     * SSID of the network we're currently connecting.
     */
    private String tryConnectToSSID = "";
    private String tryConnectToBSSID = "";
    private int tryAttempts = 0;

    public static Presenter getInstance() {
        return instance;
    }

    private Presenter() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            SCAN_DELAY = 30 * 1000;

        MDMService.Log.d(TAG, "ctr; SCAN_DELAY: " + SCAN_DELAY + " ms");

        wifiManager = (WifiManager) WFMApp.getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    public void setiMainView(IMainView iMainView) {
        this.iMainView = iMainView;

        if (iMainView != null && wifiManager != null)
            iMainView.onSetWiFiState(wifiManager.isWifiEnabled());
    }

    public void setiParamsView(IParamsView iParamsView) {
        this.iParamsView = iParamsView;
    }

    public void setLastConfig(MDMConfig lastConfig) {
        this.lastConfig = lastConfig;
        if (lastConfig != null) {
            ArrayList<AllowedItem> list = new ArrayList<>();
            for (AllowedItem item : lastConfig.allowed) {
                if (!TextUtils.isEmpty(item.ssid) || !TextUtils.isEmpty(item.bssid))
                    list.add(item);
            }

            this.lastConfig.allowed = list;
        }

        updateConnectedWiFiNetwork();
        if (iMainView != null) iMainView.onScanComplete(createList());
        if (iParamsView != null) iParamsView.onParamsResults(lastScanSSIDMap, connectionInfo, connectedState);
    }

    public void startScan() {
        MDMService.Log.d(TAG, "startScan;");

        if (wifiManager != null) {
            if (scanReceiver == null) {
                MDMService.Log.d(TAG, "startScan; init ScanReceiver");
                scanReceiver = new ScanReceiver();
                WFMApp.getContext().getApplicationContext().registerReceiver(scanReceiver, new IntentFilter(SCAN_RESULTS_AVAILABLE_ACTION));
            }

            if (connectionStateReceiver == null) {
                MDMService.Log.d(TAG, "startScan; init ConnectionStateReceiver");
                connectionStateReceiver = new ConnectionStateReceiver();
                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction(NETWORK_STATE_CHANGED_ACTION);
                intentFilter.addAction(SUPPLICANT_STATE_CHANGED_ACTION);
                intentFilter.addAction(WIFI_STATE_CHANGED_ACTION);
                WFMApp.getContext().getApplicationContext().registerReceiver(connectionStateReceiver, intentFilter);
            }

            if (wifiManager.isWifiEnabled()) {
                MDMService.Log.d(TAG, "startScan; wifiManager.isWifiEnabled(): true");

                if (System.currentTimeMillis() - startScanTime > SCAN_DELAY) {
                    boolean start = wifiManager.startScan();
                    startScanTime = System.currentTimeMillis();

                    MDMService.Log.d(TAG, "startScan; wifiManager.startScan(): " + start + "; startScanTime: " + startScanTime);

                    if (!start) {
                        handler.removeCallbacksAndMessages(null);
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                startScan();
                            }
                        }, SCAN_DELAY);
                    }
                }
                else
                    MDMService.Log.d(TAG, "startScan; delta time < SCAN_DELAY");
            }
            else
                MDMService.Log.d(TAG, "startScan; wifiManager.isWifiEnabled(): false");
        }
    }

    public void stopScan() {
        MDMService.Log.d(TAG, "stopScan;");

        if (scanReceiver != null) {
            WFMApp.getContext().getApplicationContext().unregisterReceiver(scanReceiver);
            scanReceiver = null;
        }

        handler.removeCallbacksAndMessages(null);
        startScanTime = 0;

        if (connectionStateReceiver != null) {
            WFMApp.getContext().getApplicationContext().unregisterReceiver(connectionStateReceiver);
            connectionStateReceiver = null;
        }
    }

    public void setWiFiState(boolean enable) {
        if (wifiManager != null) {
            if (enable && !wifiManager.isWifiEnabled()) {
                wifiManager.setWifiEnabled(true);
            }
            else if (!enable && wifiManager.isWifiEnabled()) {
                wifiManager.setWifiEnabled(false);
                lastScanSSIDMap.clear();
                lastScanBSSIDMap.clear();
                if (iMainView != null) iMainView.onScanComplete(createList());
            }
        }
    }

    private Map<String, WiFiItem> createSSIDMap(List<ScanResult> list) {
        Map<String, WiFiItem> map = new HashMap<>();

        if (list != null && list.size() > 0) {
            for (ScanResult item : list) {
                if (!TextUtils.isEmpty(item.SSID)) {
                    map.put(item.SSID, new WiFiItem(item));
                }
            }
        }

        if (lastConfig != null) {
            for (AllowedItem item : lastConfig.allowed) {
                // the "hidden" flag is used only if network is not visible in the scan results
                if (item.hidden && !map.containsKey(item.ssid) && (!TextUtils.isEmpty(item.ssid) || !TextUtils.isEmpty(item.bssid))) {
                    map.put(item.ssid, new HiddenWiFiItem(item.ssid, item.security));
                }
            }
        }

        return map;
    }

    private Map<String, WiFiItem> createBSSIDMap(List<ScanResult> list) {
        Map<String, WiFiItem> map = new HashMap<>();

        if (list != null && list.size() > 0) {
            for (ScanResult item : list) {
                if (!TextUtils.isEmpty(item.BSSID)) {
                    map.put(item.BSSID, new WiFiItem(item));
                }
            }
        }

        return map;
    }

    /**
     * Refresh the WiFi connection according to the configuration.
     */
    private void updateConnectedWiFiNetwork() {
        getWiFiConnectionInfo();
        updateConnectedWithConfig();
    }

    private void updateConnectedWithConfig() {
        if (lastConfig != null) {
            // Check if the active connection matches the configuration
            if (connectionInfo != null
                    && !TextUtils.isEmpty(Utils.unquote(connectionInfo.getSSID()))
                    && !TextUtils.isEmpty(Utils.unquote(connectionInfo.getBSSID()))) {
                if (!lastConfig.allAllowed) {
                    boolean allowed = false;

                    if (lastConfig.allowed != null) {
                        for (AllowedItem item : lastConfig.allowed) {
                            if (!TextUtils.isEmpty(item.ssid)
                                    && Utils.unquote(connectionInfo.getSSID()).equalsIgnoreCase(item.ssid)) {
                                allowed = true;
                            }
                            else if (!TextUtils.isEmpty(item.bssid)
                                    && Utils.unquote(connectionInfo.getBSSID()).equalsIgnoreCase(item.bssid)) {
                                allowed = true;
                            }
                            break;
                        }
                    }

                    // If we're connected to the network which isn't in the list, forget it and disconnect
                    if (!allowed) {
                        if (wifiManager != null) {
                            wifiManager.disableNetwork(connectionInfo.getNetworkId());
                            wifiManager.removeNetwork(connectionInfo.getNetworkId());
                            wifiManager.disconnect();
                        }

                        // Refresh the parameters of the current connection
                        getWiFiConnectionInfo();
                        if (iMainView != null) iMainView.onSetConnectionParams(connectionInfo, connectedState);

                        return;
                    }
                }
                // Connection only to the password protected networks or networks configured on the server
                else if (!lastConfig.freeAllowed) {
                    boolean allowed = false;

                    // Check if the connected network is password protected
                    WiFiItem current = getScanResultBySSID(Utils.unquote(connectionInfo.getSSID()));
                    if (current.hasEncryption())
                        allowed = true;
                    else {
                        if (lastConfig.allowed != null) {
                            for (AllowedItem item : lastConfig.allowed) {
                                // Add password check
                                if (!TextUtils.isEmpty(item.ssid)
                                        && Utils.unquote(connectionInfo.getSSID()).equalsIgnoreCase(item.ssid)) {
                                    allowed = true;
                                    break;
                                }
                                else if (!TextUtils.isEmpty(item.bssid)
                                        && Utils.unquote(connectionInfo.getSSID()).equalsIgnoreCase(item.bssid)) {
                                    allowed = true;
                                    break;
                                }
                            }
                        }
                    }

                    // If we're connected to the network which isn't in the list or has no password, forget it and disconnect
                    if (!allowed) {
                        if (wifiManager != null) {
                            wifiManager.disableNetwork(connectionInfo.getNetworkId());
                            wifiManager.removeNetwork(connectionInfo.getNetworkId());
                            wifiManager.disconnect();
                        }

                        // Refresh the parameters of the current connection
                        getWiFiConnectionInfo();
                        if (iMainView != null) iMainView.onSetConnectionParams(connectionInfo, connectedState);

                        return;
                    }
                }
            }
            // If there's no active connection
            else {
                if (lastConfig.allAllowed) {
                    // Connection only to the password protected networks or networks configured on the server
                    if (!lastConfig.freeAllowed) {
                        // Search for allowed network with the best signal strength
                        int bestId = getBestWiFiNetwork();

                        // If not found, save the configured networks
                        if (bestId == -1)
                            saveAllowedFromConfig();

                        // Search again for allowed network with the best signal strength
                        bestId = getBestWiFiNetwork();

                        // Trying to connect to the found network
                        if (bestId != -1) {
                            tryConnectToId = bestId;
                            wifiManager.enableNetwork(bestId, true);
                            wifiManager.reconnect();
                        }
                    }
                }
                // Connection only to the networks configured on the server
                else
                    tryConnectToAllowed();
            }
        }

        if (iMainView != null) iMainView.onSetConnectionParams(connectionInfo, connectedState);
    }

    private WiFiItem getScanResultBySSID(String ssid) {
        WiFiItem result = null;

        if (wifiManager != null && ssid != null) {
            List<ScanResult> scanResults = wifiManager.getScanResults();

            if (scanResults != null && scanResults.size() > 0) {
                for (ScanResult item: scanResults) {
                    if (!TextUtils.isEmpty(item.SSID) && item.SSID.equalsIgnoreCase(ssid)) {
                        result = new WiFiItem(item);
                        break;
                    }
                }
            }
            else if (lastScanSSIDMap != null && lastScanSSIDMap.size() > 0) {
                result = lastScanSSIDMap.get(ssid);
            }
        }

        return result;
    }

    private WiFiItem getScanResultByBSSID(String bssid) {
        WiFiItem result = null;

        if (wifiManager != null) {
            List<ScanResult> scanResults = wifiManager.getScanResults();

            if (scanResults != null && scanResults.size() > 0) {
                for (ScanResult item: scanResults) {
                    if (!TextUtils.isEmpty(item.BSSID) && item.BSSID.equalsIgnoreCase(bssid)) {
                        result = new WiFiItem(item);
                        break;
                    }
                }
            }
            else if (lastScanBSSIDMap != null && lastScanBSSIDMap.size() > 0) {
                result = lastScanBSSIDMap.get(bssid);
            }
        }

        return result;
    }

    /**
     * Get the networkId of the network having the best signal strength.
     * @return
     */
    @SuppressWarnings("MissingPermission")
    private int getBestWiFiNetwork() {
        if (wifiManager != null && lastScanSSIDMap != null) {
            List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();
            if (list != null) {
                ArrayList<WiFiItem> filtered = new ArrayList<>();
                for (WifiConfiguration config : list) {
                    WiFiItem item = lastScanSSIDMap.get(config.SSID);
                    if (item != null && (item.hasEncryption() || isAllowed(config.SSID, config.BSSID))) {
                        filtered.add(lastScanSSIDMap.get(config.SSID));
                    }
                }

                if (filtered.size() > 0) {
                    Collections.sort(filtered, new Comparator<WiFiItem>() {
                        @Override
                        public int compare(WiFiItem o1, WiFiItem o2) {
                            return o1.getLevel() - o2.getLevel();
                        }
                    });

                    for (WifiConfiguration config : list) {
                        if (config.SSID.equalsIgnoreCase(filtered.get(0).getSSID()) && !isWrong(config.SSID, config.BSSID))
                            return config.networkId;
                    }
                }
            }
        }

        return -1;
    }

    private void setupSecurity(String capabilities, WifiConfiguration config, String preSharedKey) {
        if (!TextUtils.isEmpty(capabilities) && config != null) {
            boolean isWPA = capabilities.contains("WPA") || capabilities.contains("WPA2") || capabilities.contains("WPA3");
            if (isWPA) {
                config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
                config.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            }

            if (capabilities.contains("EAP"))
                config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.LEAP);
            else if (isWPA)
                config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            else if (capabilities.contains("WEP"))
                config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);

            if (capabilities.contains("IEEE802.1X"))
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.IEEE8021X);
            else if (capabilities.contains("WPA") && capabilities.contains("EAP"))
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP);
            else if (capabilities.contains("WPA") && capabilities.contains("PSK"))
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            else if (capabilities.contains("WPA2") && capabilities.contains("PSK"))
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            else if (capabilities.contains("WPA3") && capabilities.contains("PSK"))
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            else
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);

            if (capabilities.contains("CCMP") || capabilities.contains("TKIP")) {
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            }

            if (!TextUtils.isEmpty(preSharedKey)) {
                if (capabilities.contains("WEP")) {
                    if (preSharedKey.matches("\\p{XDigit}+")) {
                        config.wepKeys[0] = preSharedKey;
                    } else {
                        config.wepKeys[0] = "\"" + preSharedKey + "\"";
                    }

                    config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
                    config.wepTxKeyIndex = 0;
                } else {
                    config.preSharedKey = "\"" + preSharedKey + "\"";
                }
            }
        }
    }

    private boolean isWiFiConnected() {
        Context context = WFMApp.getContext().getApplicationContext();
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            NetworkInfo networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            return networkInfo != null && networkInfo.isConnected();
        } else {
            Network network = connectivityManager.getActiveNetwork();
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);

            return capabilities != null &&
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        }
    }

    private void tryConnectToAllowed() {
        if (wifiManager != null && lastScanSSIDMap != null && lastConfig != null && lastConfig.allowed != null) {
            for (AllowedItem item : lastConfig.allowed) {
                if ((lastScanSSIDMap.containsKey(item.ssid) || lastScanBSSIDMap.containsKey(item.bssid)) && !item.wrongPassword) {
                    WiFiItem network = item.ssid != null ? lastScanSSIDMap.get(item.ssid) : lastScanBSSIDMap.get(item.bssid);
                    if (network != null) {
                        // Search in saved
                        int id = -1;
                        WifiConfiguration config = searchConfigured(network.getSSID());

                        // Save if not found
                        if (config == null) {
                            config = new WifiConfiguration();
                            config.SSID = "\"" + network.getSSID() + "\"";
                            if (!TextUtils.isEmpty(network.getBSSID())) {
                                config.BSSID = network.getBSSID();
                            }
                            config.hiddenSSID = network.isHidden();
                            setupSecurity(network.getCapabilities(), config, item.password);
                            id = wifiManager.addNetwork(config);
                        } else {
                            id = config.networkId;
                        }

                        if (id != -1) {
                            tryConnectToId = id;
                            if (connectedState != NetworkInfo.State.DISCONNECTED) {
                                wifiManager.disconnect();
                            }
                            wifiManager.enableNetwork(id, true);
                            if (!network.isHidden()) {
                                wifiManager.reconnect();
                            } else {
                                wifiManager.reassociate();
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    private void saveAllowedFromConfig() {
        if (wifiManager != null && lastScanSSIDMap != null && lastConfig != null && lastConfig.allowed != null) {
            for (AllowedItem item : lastConfig.allowed) {
                if (lastScanSSIDMap.containsKey(item.ssid)) {
                    WiFiItem network = lastScanSSIDMap.get(item.ssid);
                    if (network != null) {
                        int id = -1;
                        WifiConfiguration config = searchConfigured(network.getSSID());
                        // Save if not found
                        if (config == null) {
                            config = new WifiConfiguration();
                            config.SSID = "\"" + network.getSSID() + "\"";

                            setupSecurity(network.getCapabilities(), config, item.password);

                            id = wifiManager.addNetwork(config);
                        }
                    }
                }
            }
        }
    }

    @Nullable
    @SuppressWarnings("MissingPermission")
    private WifiConfiguration getWiFiConfigById(int id) {
        if (wifiManager != null) {
            List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();
            if (list != null) {
                for (WifiConfiguration config : list) {
                    if (config.networkId == id)
                        return config;
                }
            }
        }

        return null;
    }

    private ArrayList<WiFiItem> createList() {
        ArrayList<WiFiItem> result = new ArrayList<>();

        if (lastScanSSIDMap == null || lastScanSSIDMap.size() == 0)
            result.add(null);
        else {
            if (lastConfig == null) {
                for (WiFiItem item : lastScanSSIDMap.values()) {
                    WiFiItem newItem = item.clone();
                    newItem.setAllowed(true);
                    newItem.setUserAction(true);
                    newItem.setWrong(false);
                    result.add(newItem);
                }
            }
            else {
                // If only networks from the list are allowed
                if (!lastConfig.allAllowed) {
                    if (lastConfig.allowed != null) {
                        for (WiFiItem item : lastScanSSIDMap.values()) {
                            WiFiItem newItem = item.clone();
                            boolean isAllowed = isAllowed(item.getSSID(), item.getBSSID());
                            newItem.setAllowed(isAllowed);
                            newItem.setUserAction(!(isAllowed && item.hasEncryption()));
                            newItem.setWrong(isWrong(item.getSSID(), item.getBSSID()));
                            result.add(newItem);
                        }
                    }
                }
                // Networks from the list and password-encrypted networks are allowed
                else if (!lastConfig.freeAllowed) {
                    if (lastConfig.allowed != null) {
                        for (WiFiItem item : lastScanSSIDMap.values()) {
                            WiFiItem newItem = item.clone();
                            boolean isAllowed = isAllowed(item.getSSID(), item.getBSSID());
                            newItem.setAllowed(isAllowed || item.hasEncryption());
                            newItem.setUserAction(!(isAllowed && item.hasEncryption()));
                            newItem.setWrong(isWrong(item.getSSID(), item.getBSSID()));
                            result.add(newItem);
                        }
                    }
                }
                // All networks allowed
                else {
                    for (WiFiItem item : lastScanSSIDMap.values()) {
                        WiFiItem newItem = item.clone();
                        newItem.setAllowed(true);
                        newItem.setUserAction(true);
                        newItem.setWrong(false);
                        result.add(newItem);
                    }
                }
            }
        }

        return result;
    }

    private boolean isAllowed(String ssid, String bssid) {
        boolean allowed = false;

        if (!TextUtils.isEmpty(ssid)) {
            for (AllowedItem item : lastConfig.allowed) {
                if (item.ssid != null && item.ssid.equalsIgnoreCase(ssid)) {
                    allowed = true;
                    break;
                }
            }
        }

        if (!allowed && !TextUtils.isEmpty(bssid)) {
            for (AllowedItem item : lastConfig.allowed) {
                if (item.bssid != null && item.bssid.equalsIgnoreCase(bssid)) {
                    allowed = true;
                    break;
                }
            }
        }

        return allowed;
    }

    /**
     * Retrieves the parameters of the current WiFi connection.
     */
    private void getWiFiConnectionInfo() {
        if (wifiManager != null) {
            connectionInfo = wifiManager.getConnectionInfo();

            if (connectionInfo != null && (connectedState == null || connectedState == NetworkInfo.State.DISCONNECTED))
                connectionInfo = null;

            // FIX: during scans or roaming, Android returns placeholder values
            //   Prevent connecting to the network when the info is incomplete
            //   SSID  = "<unknown ssid>"
            //   BSSID = "02:00:00:00:00:00"
            //   https://developer.android.com/reference/android/net/wifi/WifiInfo
            if (connectionInfo != null) {
                String ssid = Utils.unquote(connectionInfo.getSSID());
                String bssid = connectionInfo.getBSSID();
                if ("<unknown ssid>".equals(ssid)
                        || "02:00:00:00:00:00".equals(bssid)
                        || TextUtils.isEmpty(ssid)
                        || TextUtils.isEmpty(bssid)) {
                    connectionInfo = null;
                }
            }
        }
        else
            connectionInfo = null;
    }

    public void userAction(WiFiItem network, String password) {
        if (connectionInfo != null) {
            boolean connectToOther = !Utils.unquote(connectionInfo.getSSID()).equalsIgnoreCase(network.getSSID());

            wifiManager.disableNetwork(connectionInfo.getNetworkId());
            wifiManager.removeNetwork(connectionInfo.getNetworkId());
            wifiManager.disconnect();

            if (connectToOther) {
                connectWifi(network, password);
            }
        }
        else {
            connectWifi(network, password);
        }
    }

    /**
     * Search in the list of saved networks.
     * @param ssid
     * @return
     */
    @SuppressWarnings("MissingPermission")
    private WifiConfiguration searchConfigured(String ssid) {
        List<WifiConfiguration> wifiConfigurationList = wifiManager.getConfiguredNetworks();
        if (wifiConfigurationList != null) {
            for (WifiConfiguration config : wifiConfigurationList) {
                if (config.SSID.equalsIgnoreCase("\"" + ssid + "\"")) {
                    return config;
                }
            }
        }

        return null;
    }

    @SuppressWarnings("MissingPermission")
    private WifiConfiguration searchConfigured(int id) {
        List<WifiConfiguration> wifiConfigurationList = wifiManager.getConfiguredNetworks();
        if (wifiConfigurationList != null) {
            for (WifiConfiguration config : wifiConfigurationList) {
                if (config.networkId == id) {
                    return config;
                }
            }
        }

        return null;
    }

    public boolean isWrong(String ssid, String bssid) {
        if (lastConfig != null && lastConfig.allowed != null) {
            for (AllowedItem item : lastConfig.allowed) {
                if ((ssid != null && ssid.equalsIgnoreCase(item.ssid)) || (bssid != null && bssid.equalsIgnoreCase(item.bssid)))
                    return item.wrongPassword;
            }
        }
        return false;
    }

    private boolean connectWifi(WiFiItem network, String password) {
        // Search in saved networks
        WifiConfiguration config = searchConfigured(network.getSSID());

        // Save if not found
        int id = -1;
        if (config == null) {
            config = new WifiConfiguration();
            config.SSID = "\"" + network.getSSID() + "\"";
            config.hiddenSSID = network.isHidden();
            setupSecurity(network.getCapabilities(), config, password);
            id = wifiManager.addNetwork(config);
        } else {
            id = config.networkId;
        }

        // Connect
        if (id != -1) {
            tryConnectToId = id;
            tryConnectToSSID = network.getSSID();
            tryConnectToBSSID = network.getBSSID();
            if (connectedState != NetworkInfo.State.DISCONNECTED) {
                wifiManager.disconnect();
            }
            wifiManager.enableNetwork(id, true);
            if (!network.isHidden()) {
                // for hidden networks, reconnect should be delayed to let the system save the network first
                wifiManager.reconnect();
            } else {
                wifiManager.reassociate();
            }
        }

        return true;
    }

    public Map<String, WiFiItem> getLastScanSSIDMap() {
        return lastScanSSIDMap;
    }

    public WifiInfo getConnectionInfo() {
        return connectionInfo;
    }

    public String getPasswordFromAllowed(String ssid, String bssid) {
        if (lastConfig != null && lastConfig.allowed != null && (!TextUtils.isEmpty(ssid) || !TextUtils.isEmpty(bssid))) {
            for (AllowedItem item : lastConfig.allowed) {
                if ((ssid.equalsIgnoreCase(item.ssid) || bssid.equalsIgnoreCase(item.bssid)) && !TextUtils.isEmpty(item.password))
                    return item.password;
            }
        }

        return "";
    }

    public NetworkInfo.State getConnectedState() {
        return connectedState;
    }

    public int suggestNetworks(Context context, MDMConfig config) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            return 0;
        }

        final List<WifiNetworkSuggestion> suggestionsList =
                new ArrayList<WifiNetworkSuggestion>();

        for (AllowedItem item : config.allowed) {
            final WifiNetworkSuggestion.Builder builder =
                    new WifiNetworkSuggestion.Builder();
            if (item.ssid != null) {
                builder.setSsid(item.ssid);
            }
            if (item.bssid != null) {
                builder.setBssid(MacAddress.fromString(item.bssid));
            }
            if (item.hidden) {
                builder.setIsHiddenSsid(true);
            }
            if (item.password != null) {
                builder.setWpa2Passphrase(item.password);
            }
            WifiNetworkSuggestion suggestion = builder.build();
            suggestionsList.add(suggestion);
        }
        final WifiManager wifiManager =
                (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        final int status = wifiManager.addNetworkSuggestions(suggestionsList);
        return status;
    }
}