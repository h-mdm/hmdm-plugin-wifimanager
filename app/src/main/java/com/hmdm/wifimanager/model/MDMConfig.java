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

import java.util.ArrayList;

/**
 * Describes the configuration of WiFi networks from Headwind MDM service
 */
public class MDMConfig {
    /**
     * true - allowed connection to all networks, false - only networks in 'allowed' list are allowed.
     */
    public boolean allAllowed;
    /**
     * Used if allAllowed = false. true - allowed connection to free networks,
     * false - only protected networks or in 'allowed' list are allowed.
     */
    public boolean freeAllowed;
    /**
     * List of allowed networks.
     */
    public ArrayList<AllowedItem> allowed;

    public MDMConfig() {
        allAllowed = true;
        freeAllowed = true;
        allowed = new ArrayList<>();
    }
}
