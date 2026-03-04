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

package com.hmdm.wifimanager.model;

import android.text.TextUtils;

/**
 * Representation for hidden networks
 */
public class HiddenWiFiItem extends WiFiItem {
    /**
     * Network SSID
     */
    private String ssid;
    /**
     * Security type (should be explicitly provided for hidden networks)
     */
    private String security;

    @Override
    public WiFiItem clone() {
        HiddenWiFiItem item = new HiddenWiFiItem();
        item.allowed = allowed;
        item.userAction = userAction;
        item.wrong = wrong;
        item.ssid = ssid;
        item.security = security;
        return item;
    }

    private HiddenWiFiItem() {}

    public HiddenWiFiItem(String ssid, String security) {
        this.ssid = ssid;
        this.security = security;
    }

    @Override
    public String getSSID() {
        return ssid;
    }

    @Override
    public boolean hasEncryption() {
        return !TextUtils.isEmpty(security);
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    @Override
    public String getCapabilities() {
        if (scanResult == null) {
            //ScanResult.capabilities are conventionally wrapped by [ ], so a raw string may not work as expected
            if (security != null && !security.isEmpty() && !security.startsWith("[")) {
                return "[" + security + "]";
            }
            return security;
        }
        return scanResult.capabilities;
    }
}
