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
import android.net.wifi.WifiInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hmdm.MDMService;
import com.hmdm.wifimanager.R;
import com.hmdm.wifimanager.model.WiFiItem;
import com.hmdm.wifimanager.Presenter;
import com.hmdm.wifimanager.ui.activities.MainActivity;
import com.hmdm.wifimanager.ui.adapters.NetsAdapter;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainFragment extends Fragment implements IMainView,
        CompoundButton.OnCheckedChangeListener,
        NetsAdapter.INetsAdapter {
    private final static String TAG = "HeadwindWiFi";

    @BindView(R.id.wifiState) Switch wifiState;
    @BindView(R.id.topDivider) LinearLayout topDivider;
    @BindView(R.id.recycler) RecyclerView recycler;

    private NetsAdapter adapter;

    public MainFragment() {}

    public static MainFragment newInstance() {

        Bundle args = new Bundle();

        MainFragment fragment = new MainFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);
        ButterKnife.bind(this, view);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wifiState.setVisibility(View.GONE);
            topDivider.setVisibility(View.GONE);
        }
        else
            wifiState.setOnCheckedChangeListener(this);

        Presenter.getInstance().setiMainView(this);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Presenter.getInstance().setiMainView(null);
    }

    @Override
    public void onSetWiFiState(boolean enabled) {
        wifiState.setOnCheckedChangeListener(null);
        wifiState.setChecked(enabled);
        wifiState.setOnCheckedChangeListener(this);
    }

    @Override
    public void onScanComplete(ArrayList<WiFiItem> items) {
        MDMService.Log.d(TAG, "onScanComplete(); items: " + items.size());

        if (adapter == null) {
            adapter = new NetsAdapter(items);
            adapter.setiNetsAdapter(this);
            recycler.setLayoutManager(new LinearLayoutManager(getActivity()));
            recycler.setAdapter(adapter);
        }
        else
            adapter.update(items);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        MDMService.Log.d(TAG, "onCheckedChanged(); isChecked: " + isChecked);
        Presenter.getInstance().setWiFiState(isChecked);
    }

    @Override
    public void onSetConnectionParams(WifiInfo connectionInfo, NetworkInfo.State connectedState) {
        MDMService.Log.d(TAG, "onSetConnectionParams(); connectionInfo: " + (connectionInfo == null ? "null" : connectionInfo.toString())
                + "; connectedState: " + (connectedState == null ? "null" : connectedState.toString()));

        if (adapter != null) adapter.update(connectionInfo, connectedState);
    }

    @Override
    public void onNetClick(WiFiItem item) {
        MDMService.Log.d(TAG, "onItemClick(); item.scanResult.SSID: " + item.scanResult.SSID
                + "; item.userActions: " + item.userActions + "; item.allowed: " + item.allowed);

        if (item.allowed && getActivity() != null && getActivity() instanceof MainActivity) {
            ((MainActivity)getActivity()).showWiFiParams(item);
        }
    }
}
