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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.hmdm.MDMService;
import com.hmdm.wifimanager.model.AllowedItem;
import com.hmdm.wifimanager.model.Capabilities;
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

import static android.net.wifi.WifiManager.ERROR_AUTHENTICATING;
import static android.net.wifi.WifiManager.EXTRA_RESULTS_UPDATED;
import static android.net.wifi.WifiManager.NETWORK_STATE_CHANGED_ACTION;
import static android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION;
import static android.net.wifi.WifiManager.SUPPLICANT_STATE_CHANGED_ACTION;
import static android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION;
import static android.net.wifi.WifiManager.WIFI_STATE_DISABLED;
import static android.net.wifi.WifiManager.WIFI_STATE_ENABLED;

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
                if (resultsUpdated || lastScan == null) {
                    lastScan = arrayToMap(wifiManager.getScanResults());
                    MDMService.Log.d(TAG, "ScanReceiver; onReceive(); lastScan.size(): " + lastScan.size());

                    updateConnectedWiFiNetwork();
                    if (iMainView != null) iMainView.onScanComplete(createList());
                    if (iParamsView != null) iParamsView.onParamsResults(lastScan, connectionInfo, connectedState);
                }
            }
            else {
                lastScan = arrayToMap(wifiManager.getScanResults());
                MDMService.Log.d(TAG, "ScanReceiver; onReceive(); lastScan.size(): " + lastScan.size());

                updateConnectedWiFiNetwork();
                if (iMainView != null) iMainView.onScanComplete(createList());
                if (iParamsView != null) iParamsView.onParamsResults(lastScan, connectionInfo, connectedState);
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

                        updateConnectedWiFiNetwork();
                        if (iMainView != null)
                            iMainView.onScanComplete(createList());
                        if (iParamsView != null)
                            iParamsView.onParamsResults(lastScan, connectionInfo, connectedState);
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

                            if (lastConfig != null && lastConfig.allowed != null && !TextUtils.isEmpty(tryConnectToSSID)) {
                                for (AllowedItem item : lastConfig.allowed) {
                                    if (item.ssid.equals(tryConnectToSSID))
                                        item.wrong = true;
                                }

                                tryConnectToSSID = "";
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

                            if (lastConfig != null && lastConfig.allowed != null && !TextUtils.isEmpty(tryConnectToSSID)) {
                                for (AllowedItem item : lastConfig.allowed) {
                                    if (item.ssid.equals(tryConnectToSSID))
                                        item.wrong = true;
                                }

                                tryConnectToSSID = "";
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
    private Map<String, ScanResult> lastScan;
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
        if (iParamsView != null) iParamsView.onParamsResults(lastScan, connectionInfo, connectedState);
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
                lastScan.clear();
                if (iMainView != null) iMainView.onScanComplete(createList());
            }
        }
    }

    private Map<String, ScanResult> arrayToMap(List<ScanResult> list) {
        Map<String, ScanResult> map = new HashMap<>();

        if (list != null && list.size() > 0) {
            for (ScanResult item : list) {
                map.put(item.SSID, item);
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
            if (connectionInfo != null && !TextUtils.isEmpty(Utils.getSSIDWithoutQuotes(connectionInfo.getSSID()))) {
                if (!lastConfig.allAllowed) {
                    boolean allowed = false;

                    if (lastConfig.allowed != null) {
                        for (AllowedItem item : lastConfig.allowed) {
                            ScanResult scanResult = null;

                            // Get the parameters of the access point for the current WiFi connection
                            if (!TextUtils.isEmpty(item.ssid)
                                    && Utils.getSSIDWithoutQuotes(connectionInfo.getSSID()).equalsIgnoreCase(item.ssid)) {
                                scanResult = getScanResultBySSID(Utils.getSSIDWithoutQuotes(connectionInfo.getSSID()));
                            }
                            else if (!TextUtils.isEmpty(item.bssid)
                                    && Utils.getSSIDWithoutQuotes(connectionInfo.getSSID()).equalsIgnoreCase(item.bssid)) {
                                scanResult = getScanResultByBSID(Utils.getSSIDWithoutQuotes(connectionInfo.getSSID()));
                            }

                            if (scanResult == null)
                                continue;

                            allowed = true;
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
                    if (hasEncryption(getScanResultBySSID(Utils.getSSIDWithoutQuotes(connectionInfo.getSSID()))))
                        allowed = true;
                    else {
                        if (lastConfig.allowed != null) {
                            for (AllowedItem item : lastConfig.allowed) {
                                // Add password check
                                if (!TextUtils.isEmpty(item.ssid)
                                        && Utils.getSSIDWithoutQuotes(connectionInfo.getSSID()).equalsIgnoreCase(item.ssid)) {
                                    allowed = true;
                                    break;
                                }
                                else if (!TextUtils.isEmpty(item.bssid)
                                        && Utils.getSSIDWithoutQuotes(connectionInfo.getSSID()).equalsIgnoreCase(item.bssid)) {
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

    private ScanResult getScanResultBySSID(String ssid) {
        ScanResult result = null;

        if (wifiManager != null && ssid != null) {
            List<ScanResult> scanResults = wifiManager.getScanResults();

            if (scanResults != null && scanResults.size() > 0) {
                for (ScanResult item: scanResults) {
                    if (!TextUtils.isEmpty(item.SSID) && item.SSID.equalsIgnoreCase(ssid)) {
                        result = item;
                        break;
                    }
                }
            }
            else if (lastScan != null && lastScan.size() > 0) {
                result = lastScan.get(ssid);
            }
        }

        return result;
    }

    private ScanResult getScanResultByBSID(String bsid) {
        ScanResult result = null;

        if (wifiManager != null) {
            List<ScanResult> scanResults = wifiManager.getScanResults();

            if (scanResults != null && scanResults.size() > 0) {
                for (ScanResult item: scanResults) {
                    if (!TextUtils.isEmpty(item.BSSID) && item.BSSID.equalsIgnoreCase(bsid)) {
                        result = item;
                        break;
                    }
                }
            }
            else if (lastScan != null && lastScan.size() > 0) {
                for (ScanResult item: lastScan.values()) {
                    if (!TextUtils.isEmpty(item.BSSID) && item.BSSID.equalsIgnoreCase(bsid)) {
                        result = item;
                        break;
                    }
                }
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
        if (wifiManager != null && lastScan != null) {
            List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();
            if (list != null) {
                ArrayList<ScanResult> filtered = new ArrayList<>();
                for (WifiConfiguration config : list) {
                    if (lastScan.containsKey(config.SSID)
                            && (hasEncryption(lastScan.get(config.SSID)) || isAllowed(config.SSID, ""))) {
                        filtered.add(lastScan.get(config.SSID));
                    }
                }

                if (filtered.size() > 0) {
                    Collections.sort(filtered, new Comparator<ScanResult>() {
                        @Override
                        public int compare(ScanResult o1, ScanResult o2) {
                            return o1.level - o2.level;
                        }
                    });

                    for (WifiConfiguration config : list) {
                        if (config.SSID.equals(filtered.get(0).SSID) && !isWrong(config.SSID))
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

    private void tryConnectToAllowed() {
        if (wifiManager != null && lastScan != null && lastConfig != null && lastConfig.allowed != null) {
            for (AllowedItem item : lastConfig.allowed) {
                if (lastScan.containsKey(item.ssid) && !item.wrong) {
                    ScanResult network = lastScan.get(item.ssid);
                    if (network != null) {
                        // Search in saved
                        int id = -1;
                        WifiConfiguration config = searchConfigured(network.SSID);

                        // Save if not found
                        if (config == null) {
                            config = new WifiConfiguration();
                            config.SSID = "\"" + network.SSID + "\"";

                            setupSecurity(network.capabilities, config, item.password);

                            id = wifiManager.addNetwork(config);
                        }

                        if (id != -1) {
                            tryConnectToId = id;
                            if (connectedState != NetworkInfo.State.DISCONNECTED) wifiManager.disconnect();
                            wifiManager.enableNetwork(id, true);
                            wifiManager.reconnect();
                        }
                    }
                }
            }
        }
    }

    private void saveAllowedFromConfig() {
        if (wifiManager != null && lastScan != null && lastConfig != null && lastConfig.allowed != null) {
            for (AllowedItem item : lastConfig.allowed) {
                if (lastScan.containsKey(item.ssid)) {
                    ScanResult network = lastScan.get(item.ssid);
                    if (network != null) {
                        int id = -1;
                        WifiConfiguration config = searchConfigured(network.SSID);
                        // Save if not found
                        if (config == null) {
                            config = new WifiConfiguration();
                            config.SSID = "\"" + network.SSID + "\"";

                            setupSecurity(network.capabilities, config, item.password);

                            id = wifiManager.addNetwork(config);
                        }
                    }
                }
            }
        }
    }

    private boolean hasEncryption(ScanResult result) {
        return !Capabilities.parse(result.capabilities).isOpen();
        /*return result != null
                && (result.capabilities.contains("WEP") || result.capabilities.contains("WPA")
                || result.capabilities.contains("WPA2") || result.capabilities.contains("WPA3"));*/
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

        if (lastScan == null || lastScan.size() == 0)
            result.add(null);
        else {
            if (lastConfig == null) {
                for (ScanResult item : lastScan.values())
                    result.add(new WiFiItem(item, true, true, false));
            }
            else {
                // If only networks from the list are allowed
                if (!lastConfig.allAllowed) {
                    if (lastConfig.allowed != null) {
                        for (ScanResult item : lastScan.values()) {
                            boolean isAllowed = isAllowed(item.SSID, item.BSSID);
                            result.add(new WiFiItem(item, isAllowed, !(isAllowed && hasEncryption(item)), isWrong(item.SSID)));
                        }
                    }
                }
                // Networks from the list and password-encrypted networks are allowed
                else if (!lastConfig.freeAllowed) {
                    if (lastConfig.allowed != null) {
                        for (ScanResult item : lastScan.values()) {
                            boolean isAllowed = isAllowed(item.SSID, item.BSSID);
                            result.add(new WiFiItem(item,
                                    isAllowed || hasEncryption(item), !(isAllowed && hasEncryption(item)), isWrong(item.SSID)));
                        }
                    }
                }
                // All networks allowed
                else {
                    for (ScanResult item : lastScan.values())
                        result.add(new WiFiItem(item, true, true, false));
                }
            }
        }

        return result;
    }

    private boolean isAllowed(String ssid, String bsid) {
        boolean allowed = false;

        if (!TextUtils.isEmpty(ssid)) {
            for (AllowedItem item : lastConfig.allowed) {
                if (item.ssid != null && item.ssid.equalsIgnoreCase(ssid)) {
                    allowed = true;
                    break;
                }
            }
        }

        if (!allowed && !TextUtils.isEmpty(bsid)) {
            for (AllowedItem item : lastConfig.allowed) {
                if (item.bssid != null && item.bssid.equalsIgnoreCase(bsid)) {
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
        }
        else
            connectionInfo = null;
    }

    public void userAction(ScanResult scanResult, String password) {
        if (connectionInfo != null) {
            boolean connectToOther = !Utils.getSSIDWithoutQuotes(connectionInfo.getSSID()).equals(scanResult.SSID);

            wifiManager.disableNetwork(connectionInfo.getNetworkId());
            wifiManager.removeNetwork(connectionInfo.getNetworkId());
            wifiManager.disconnect();

            if (connectToOther) {
                connectWifi(scanResult, password);
            }
        }
        else {
            connectWifi(scanResult, password);
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
                if (config.SSID.equals("\"" + ssid + "\"")) {
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

    public boolean isWrong(String ssid) {
        if (lastConfig != null && lastConfig.allowed != null) {
            for (AllowedItem item : lastConfig.allowed) {
                if (item.ssid.equals(ssid))
                    return item.wrong;
            }
        }
        return false;
    }

    private boolean connectWifi(ScanResult network, String password) {
        if (!hasEncryption(network)) {
            // Search in saved networks
            WifiConfiguration config = searchConfigured(network.SSID);

            // Save if not found
            int id = -1;
            if (config == null) {
                config = new WifiConfiguration();
                config.SSID = "\"" + network.SSID + "\"";

                setupSecurity(network.capabilities, config, password);

                id = wifiManager.addNetwork(config);
            }

            // Connect
            if (id != -1) {
                tryConnectToId = id;
                tryConnectToSSID = network.SSID;
                if (connectedState != NetworkInfo.State.DISCONNECTED) wifiManager.disconnect();
                wifiManager.enableNetwork(id, true);
                wifiManager.reconnect();
            }
        }
        else {
            // Search in saved networks
            int id = -1;
            WifiConfiguration config = null;

            if (!isWrong(network.SSID))
                config = searchConfigured(network.SSID);

            // Save if not found
            if (config == null) {
                config = new WifiConfiguration();
                config.SSID = "\"" + network.SSID + "\"";

                setupSecurity(network.capabilities, config, password);

                id = wifiManager.addNetwork(config);
            }
            // Connect
            if (id != -1) {
                tryConnectToId = id;
                tryConnectToSSID = network.SSID;

                if (connectedState != NetworkInfo.State.DISCONNECTED) {
                    wifiManager.disconnect();
                }

                wifiManager.enableNetwork(id, true);
                wifiManager.reconnect();
                //tryConnect = true;
            }
        }

        return true;
    }

    public Map<String, ScanResult> getLastScan() {
        return lastScan;
    }

    public WifiInfo getConnectionInfo() {
        return connectionInfo;
    }

    public String getPasswordFromAllowed(String ssid) {
        if (lastConfig != null && lastConfig.allowed != null && ! TextUtils.isEmpty(ssid)) {
            for (AllowedItem item : lastConfig.allowed) {
                if (item.ssid.equals(ssid) && !TextUtils.isEmpty(item.password))
                    return item.password;
            }
        }

        return "";
    }

    public NetworkInfo.State getConnectedState() {
        return connectedState;
    }
}