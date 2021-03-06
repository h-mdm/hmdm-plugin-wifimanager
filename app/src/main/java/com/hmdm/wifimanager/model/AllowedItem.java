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

/**
 * Description of the allowed network
 */
public class AllowedItem {
    /**
     * Network SSID.
     */
    public String ssid;
    /**
     * Network BSSID.
     */
    public String bssid;
    /**
     * Password.
     */
    public String password;
    /**
     * This flag shows the coincidence of the password from 'password' field and the password of network with this SSID/BSSID.
     */
    public boolean wrong = false;

    public AllowedItem() {}

    public AllowedItem(String ssid, String bssid, String password) {
        this.ssid = ssid;
        this.bssid = bssid;
        this.password = password;
    }
}
