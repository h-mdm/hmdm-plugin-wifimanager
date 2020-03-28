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
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.hmdm.MDMService;
import com.hmdm.wifimanager.R;
import com.hmdm.wifimanager.Utils;
import com.hmdm.wifimanager.model.Capabilities;
import com.hmdm.wifimanager.model.WiFiItem;
import com.hmdm.wifimanager.Presenter;

import java.util.Locale;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;

import static android.net.wifi.WifiManager.ERROR_AUTHENTICATING;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;

public class ParamsFragment extends Fragment implements IParamsView, View.OnClickListener {
    private final static String TAG = "HeadwindWiFi";
    private WiFiItem item;

    @BindView(R.id.titleLevel) TextView titleLevel;
    @BindView(R.id.level) TextView level;
    @BindView(R.id.titleSpeed) TextView titleSpeed;
    @BindView(R.id.speed) TextView speed;
    @BindView(R.id.dividerSpeed) LinearLayout dividerSpeed;
    @BindView(R.id.titleIP) TextView titleIP;
    @BindView(R.id.ip) TextView ip;
    @BindView(R.id.dividerIP) LinearLayout dividerIP;
    @BindView(R.id.titleMAC) TextView titleMAC;
    @BindView(R.id.mac) TextView mac;
    @BindView(R.id.dividerMAC) LinearLayout dividerMAC;
    @BindView(R.id.titleEncryption) TextView titleEncryption;
    @BindView(R.id.encryption) TextView encryption;
    @BindView(R.id.dividerEncryption) LinearLayout dividerEncryption;
    @BindView(R.id.titlePassword) TextView titlePassword;
    @BindView(R.id.layoutPassword) FrameLayout layoutPassword;
    @BindView(R.id.password) EditText password;
    @BindView(R.id.passwordVisibility) ImageView passwordVisibility;
    //@BindView(R.id.dividerPassword) LinearLayout dividerPassword;
    @BindView(R.id.action) TextView action;

    public ParamsFragment() {}

    public static ParamsFragment newInstance(WiFiItem item) {
        Bundle args = new Bundle();
        args.putParcelable("item", item);

        ParamsFragment fragment = new ParamsFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        item = getArguments().getParcelable("item");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_params, container, false);
        ButterKnife.bind(this, view);

        Presenter.getInstance().setiParamsView(this);

        updateUI(Presenter.getInstance().getLastScan() == null ? null : Presenter.getInstance().getLastScan().get(item.scanResult.SSID),
                Presenter.getInstance().getConnectionInfo(), Presenter.getInstance().getConnectedState());

        passwordVisibility.setOnClickListener(this);
        action.setOnClickListener(this);

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Presenter.getInstance().setiParamsView(null);
    }

    private void showMAC(int visibility, String value) {
        titleMAC.setVisibility(visibility);
        mac.setText(value);
        mac.setVisibility(visibility);
        dividerMAC.setVisibility(visibility);
    }

    private void updateUI(ScanResult scanResult, @Nullable WifiInfo connectionInfo, NetworkInfo.State connectedState) {
        if (scanResult != null) {
            level.setText(getResources().getStringArray(R.array.signal_levels)[WifiManager.calculateSignalLevel(scanResult.level,
                    getResources().getStringArray(R.array.signal_levels).length)]);

            if (connectionInfo != null && Utils.getSSIDWithoutQuotes(connectionInfo.getSSID()).equals(item.scanResult.SSID)
                    && connectedState == NetworkInfo.State.CONNECTED) {
                speed.setText(String.format(Locale.US, "%d %s", connectionInfo.getLinkSpeed(), getString(R.string.mbps)));
                ip.setText(String.format(Locale.US, "%d.%d.%d.%d", (connectionInfo.getIpAddress() & 0xff),
                        (connectionInfo.getIpAddress() >> 8 & 0xff), (connectionInfo.getIpAddress() >> 16 & 0xff),
                        (connectionInfo.getIpAddress() >> 24 & 0xff)));

                showSpeed(VISIBLE);
                showIp(VISIBLE);
                showMAC(VISIBLE, connectionInfo.getBSSID());
            }
            else {
                showSpeed(GONE);
                showIp(GONE);
                showMAC(GONE, "");
            }

            Capabilities capabilities = Capabilities.parse(item.scanResult.capabilities);
            encryption.setText(capabilities.format());

            if (connectionInfo != null && Utils.getSSIDWithoutQuotes(connectionInfo.getSSID()).equals(item.scanResult.SSID)
                    && connectedState == NetworkInfo.State.CONNECTED) {
                showPassword(GONE);

                if (!capabilities.isOpen()) {
                    if (item.userActions) {
                        action.setText(getString(R.string.delete_network));
                        action.setVisibility(VISIBLE);
                    } else
                        action.setVisibility(GONE);
                }
                else {
                    action.setText(getString(R.string.disconnect));
                    action.setVisibility(VISIBLE);
                }
            }
            else {
                if (!capabilities.isOpen()) {
                    if (TextUtils.isEmpty(password.getText().toString()) && !item.isWrong)
                        password.setText(Presenter.getInstance().getPasswordFromAllowed(item.scanResult.SSID));
                    showPassword(VISIBLE);
                }
                else
                    showPassword(GONE);

                action.setText(getString(R.string.connect));
                action.setVisibility(VISIBLE);
            }

            if (!action.isEnabled())
                action.setEnabled(true);
        }
    }

    private void showSpeed(int visibility) {
        titleSpeed.setVisibility(visibility);
        speed.setVisibility(visibility);
        dividerSpeed.setVisibility(visibility);
    }

    private void showIp(int visibility) {
        titleIP.setVisibility(visibility);
        ip.setVisibility(visibility);
        dividerIP.setVisibility(visibility);
    }

    private void showPassword(int visibility) {
        titlePassword.setVisibility(visibility);
        layoutPassword.setVisibility(visibility);
    }

    @Override
    public void onParamsResults(Map<String, ScanResult> lastScan, WifiInfo connectionInfo, NetworkInfo.State connectedState) {
        MDMService.Log.d(TAG, "onParamsResults(); lastScan: " + lastScan.size() + "; connectionInfo: " + (connectionInfo == null ? "null" : connectionInfo.toString())
                + "; connectedState: " + (connectedState == null ? "null" : connectedState.toString()));

        updateUI(lastScan.get(item.scanResult.SSID), connectionInfo, connectedState);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.passwordVisibility:
                changePasswordVisibility();
                break;
            case R.id.action:
                action();
                break;
        }
    }

    @Override
    public void onConnectionError(int supplicantError, String ssid) {
        String error = "";

        if (!TextUtils.isEmpty(ssid)) {
            if (supplicantError != -1) {
                if (supplicantError == ERROR_AUTHENTICATING) {
                    error = String.format(Locale.getDefault(), "%s %s %s", getString(R.string.failed_connect_to),
                            ssid, getString(R.string.wrong_password));
                }
                else {
                    error = String.format(Locale.getDefault(), "%s %s %s %d", getString(R.string.failed_connect_to),
                            ssid, getString(R.string.error), supplicantError);
                }
            }
        }
        else {
            if (supplicantError != -1) {
                if (supplicantError == ERROR_AUTHENTICATING) {
                    error = String.format(Locale.getDefault(), "%s %s", getString(R.string.error_on_connection),
                            getString(R.string.wrong_password));
                }
                else {
                    error = String.format(Locale.getDefault(), "%s %s %d", getString(R.string.error_on_connection),
                            getString(R.string.error), supplicantError);
                }
            }
            else
                error = getString(R.string.error_on_connection);
        }

        Toast.makeText(getActivity(), error, Toast.LENGTH_LONG).show();
    }

    private void changePasswordVisibility() {
        if (password.getTransformationMethod() instanceof PasswordTransformationMethod) {
            password.setTransformationMethod(null);
            passwordVisibility.setImageResource(R.drawable.ic_visibility);
        }
        else {
            password.setTransformationMethod(PasswordTransformationMethod.getInstance());
            passwordVisibility.setImageResource(R.drawable.ic_visibility_off);
        }
    }

    private void action() {
        if (Presenter.getInstance().getConnectionInfo() == null
                && !Capabilities.parse(item.scanResult.capabilities).isOpen()
                && (TextUtils.isEmpty(password.getText().toString()) || password.getText().toString().length() < 8)) {
            Toast.makeText(getActivity(), getString(R.string.password_toast), Toast.LENGTH_LONG).show();
            return;
        }

        Utils.hideKeyboardFrom(getActivity(), password);

        MDMService.Log.d(TAG, "onClick(); action; item.scanResult: " + item.scanResult.toString()
                + "; password: " + password);

        action.setEnabled(false);
        if (Presenter.getInstance().getConnectionInfo() != null)
            action.setText(getString(R.string.disconnection));
        else
            action.setText(getString(R.string.connection));

        Presenter.getInstance().userAction(item.scanResult, password.getText().toString());
        password.setText("");
    }
}
