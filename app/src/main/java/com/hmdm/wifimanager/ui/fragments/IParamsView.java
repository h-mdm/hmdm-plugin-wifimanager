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

package com.hmdm.wifimanager.ui.fragments;

import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import java.util.Map;

/**
 * Interface for paramsFragment events.
 */
public interface IParamsView {
    /**
     * Refresh of scan results and connection parameters.
     * @param lastScan
     * @param connectionInfo
     * @param connectedState
     */
    void onParamsResults(Map<String, ScanResult> lastScan, WifiInfo connectionInfo, NetworkInfo.State connectedState);

    /**
     * Connection error.
     * @param supplicantError
     * @param ssid
     */
    void onConnectionError(int supplicantError, String ssid);
}
