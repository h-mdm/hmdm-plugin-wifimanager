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

import android.net.wifi.ScanResult;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Describes the network parameters used to display the info in the app interface.
 */
public class WiFiItem implements Parcelable {
    /**
     * Access point parameters.
     */
    protected ScanResult scanResult;
    /**
     * Is it allowed or forbidden to connect to this network.
     */
    protected boolean allowed;
    /**
     * Is it allowed or forbidden to forget this network.
     */
    protected boolean userAction;
    /**
     * Config contains a wrong password.
     */
    protected boolean wrong;

    public void setScanResult(ScanResult scanResult) {
        this.scanResult = scanResult;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    public boolean hasUserAction() {
        return userAction;
    }

    public void setUserAction(boolean userAction) {
        this.userAction = userAction;
    }

    public boolean isWrong() {
        return wrong;
    }

    public void setWrong(boolean wrong) {
        this.wrong = wrong;
    }

    public boolean isHidden() {
        return false;
    }

    public String getSSID() {
        if (scanResult == null) {
            return "";
        }
        return scanResult.SSID;
    }

    public String getBSSID() {
        if (scanResult == null) {
            return "";
        }
        return scanResult.BSSID;
    }

    public String getCapabilities() {
        if (scanResult == null) {
            return "";
        }
        return scanResult.capabilities;
    }

    public boolean hasEncryption() {
        if (scanResult == null) {
            return false;
        }
        return !Capabilities.parse(scanResult.capabilities).isOpen();
    }

    public int getLevel() {
        if (scanResult == null) {
            return 0;
        }
        return scanResult.level;
    }

    public WiFiItem() {}

    public WiFiItem clone() {
        WiFiItem item = new WiFiItem();
        item.scanResult = scanResult;
        item.allowed = allowed;
        item.userAction = userAction;
        item.wrong = wrong;
        return item;
    }

    public WiFiItem(ScanResult scanResult, boolean allowed, boolean forgettable, boolean wrong) {
        this.scanResult = scanResult;
        this.allowed = allowed;
        this.userAction = forgettable;
        this.wrong = wrong;
    }

    public WiFiItem(ScanResult scanResult) {
        this.scanResult = scanResult;
    }

    protected WiFiItem(Parcel in) {
        scanResult = in.readParcelable(ScanResult.class.getClassLoader());
        allowed = in.readByte() != 0;
        userAction = in.readByte() != 0;
        wrong = in.readByte() != 0;
    }

    public static final Creator<WiFiItem> CREATOR = new Creator<WiFiItem>() {
        @Override
        public WiFiItem createFromParcel(Parcel in) {
            return new WiFiItem(in);
        }

        @Override
        public WiFiItem[] newArray(int size) {
            return new WiFiItem[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(scanResult, flags);
        dest.writeByte((byte) (allowed ? 1 : 0));
        dest.writeByte((byte) (userAction ? 1 : 0));
        dest.writeByte((byte) (wrong ? 1 : 0));
    }
}
