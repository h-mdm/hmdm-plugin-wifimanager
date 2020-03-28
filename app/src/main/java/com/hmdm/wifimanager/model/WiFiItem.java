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
    public ScanResult scanResult;
    /**
     * Is it allowed or forbidden to connect to this network.
     */
    public boolean allowed;
    /**
     * Is it allowed or forbidden to forget this network.
     */
    public boolean userActions;
    /**
     * Config contains a wrong password.
     */
    public boolean isWrong;

    public WiFiItem(ScanResult scanResult, boolean allowed, boolean userActions, boolean isWrong) {
        this.scanResult = scanResult;
        this.allowed = allowed;
        this.userActions = userActions;
        this.isWrong = isWrong;
    }

    protected WiFiItem(Parcel in) {
        scanResult = in.readParcelable(ScanResult.class.getClassLoader());
        allowed = in.readByte() != 0;
        userActions = in.readByte() != 0;
        isWrong = in.readByte() != 0;
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
        dest.writeByte((byte) (userActions ? 1 : 0));
        dest.writeByte((byte) (isWrong ? 1 : 0));
    }
}
