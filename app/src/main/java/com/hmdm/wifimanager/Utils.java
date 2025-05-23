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

import static android.net.wifi.WifiManager.WIFI_STATE_DISABLED;
import static android.net.wifi.WifiManager.WIFI_STATE_DISABLING;
import static android.net.wifi.WifiManager.WIFI_STATE_ENABLED;
import static android.net.wifi.WifiManager.WIFI_STATE_ENABLING;
import static android.net.wifi.WifiManager.WIFI_STATE_UNKNOWN;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

public class Utils {
    /**
     * Retrieves the string without quotes.
     * @param str string.
     * @return string without quotes.
     */
    public static String unquote(String str) {
        if (str != null && str.length() > 2 && str.charAt(0) == '"' && str.charAt(str.length() - 1) == '"')
            return str.substring(1, str.length() - 1);

        return str;
    }

    /**
     * Hides the keyboard.
     * @param context Context.
     * @param view Field which activated the keyboard.
     */
    public static void hideKeyboardFrom(Context context, View view) {
        if (context != null) {
            InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (inputMethodManager != null)
                inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public static String formatWiFiState(int state) {
        switch (state) {
            case WIFI_STATE_DISABLING:
                return "WIFI_STATE_DISABLING";
            case WIFI_STATE_DISABLED:
                return "WIFI_STATE_DISABLED";
            case WIFI_STATE_ENABLING:
                return "WIFI_STATE_ENABLING";
            case WIFI_STATE_ENABLED:
                return "WIFI_STATE_ENABLED";
            case WIFI_STATE_UNKNOWN:
                return "WIFI_STATE_UNKNOWN";
            default:
                return String.valueOf(state);
        }
    }
}
