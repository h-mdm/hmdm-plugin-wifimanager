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

package com.hmdm.wifimanager.ui.adapters;

import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hmdm.wifimanager.R;
import com.hmdm.wifimanager.Utils;
import com.hmdm.wifimanager.model.Capabilities;
import com.hmdm.wifimanager.model.WiFiItem;
import com.hmdm.wifimanager.Presenter;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;

public class NetsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final static int NO_ITEMS = 0;
    private final static int HAS_ITEMS = 1;

    private final static int[] SIGNALS = {R.drawable.ic_signal_0, R.drawable.ic_signal_1, R.drawable.ic_signal_2,
            R.drawable.ic_signal_3, R.drawable.ic_signal_4};

    private final static int[] SIGNALS_ACTIVE = {R.drawable.ic_signal_active_0, R.drawable.ic_signal_active_1, R.drawable.ic_signal_active_2,
            R.drawable.ic_signal_active_3, R.drawable.ic_signal_active_4};

    public interface INetsAdapter {
        void onNetClick(WiFiItem item);
    }

    class WiFiViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        LinearLayout root;
        @BindView(R.id.name) TextView name;
        @BindView(R.id.state) TextView state;
        @BindView(R.id.signal) ImageView signal;
        @BindView(R.id.lock) ImageView lock;
        @BindView(R.id.banned) ImageView banned;

        public WiFiViewHolder(@NonNull View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
            root = (LinearLayout)itemView;
        }

        public void onBindViewHolder(int position) {
            if (!items.get(position).allowed)
                banned.setVisibility(View.VISIBLE);
            else
                banned.setVisibility(View.GONE);

            boolean isActive = connectionInfo != null && Utils.getSSIDWithoutQuotes(connectionInfo.getSSID()) != null
                    && Utils.getSSIDWithoutQuotes(connectionInfo.getSSID()).equals(items.get(position).scanResult.SSID)
                    && connectedState != NetworkInfo.State.DISCONNECTED
                    && connectedState != NetworkInfo.State.SUSPENDED && connectedState != NetworkInfo.State.UNKNOWN;

            if (isActive) {
                name.setTextColor(name.getContext().getResources().getColor(R.color.color1));
                state.setTextColor(state.getContext().getResources().getColor(R.color.color1));

                signal.setImageResource(SIGNALS_ACTIVE[WifiManager.calculateSignalLevel(items.get(position).scanResult.level, SIGNALS_ACTIVE.length)]);
            }
            else {
                name.setTextColor(name.getContext().getResources().getColor(android.R.color.black));
                state.setTextColor(state.getContext().getResources().getColor(R.color.color4));

                signal.setImageResource(SIGNALS[WifiManager.calculateSignalLevel(items.get(position).scanResult.level, SIGNALS.length)]);
            }

            name.setText(items.get(position).scanResult.SSID);

            Capabilities capabilities = Capabilities.parse(items.get(position).scanResult.capabilities);

            if (isActive)
                state.setText(state.getResources().getString(R.string.connected));
            else {
                if (!capabilities.isOpen())
                    state.setText(state.getResources().getString(R.string.have_protection));
                else
                    state.setText(state.getResources().getString(R.string.no_protection));
            }

            if (!capabilities.isOpen()) {
                lock.setVisibility(View.VISIBLE);
            } else {
                lock.setVisibility(View.GONE);
            }

            root.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (iNetsAdapter != null ) iNetsAdapter.onNetClick(items.get(getAdapterPosition()));
                }
            });
        }

        @Override
        public void onClick(View v) {
            if (iNetsAdapter != null) iNetsAdapter.onNetClick(items.get(getAdapterPosition()));
        }
    }

    class NoItemsViewHolder extends RecyclerView.ViewHolder {
        public NoItemsViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    private ArrayList<WiFiItem> items = new ArrayList<>();
    private WifiInfo connectionInfo;
    private NetworkInfo.State connectedState;
    private INetsAdapter iNetsAdapter;

    public NetsAdapter(ArrayList<WiFiItem> items) {
        this.items = items;
        connectionInfo = Presenter.getInstance().getConnectionInfo();
        connectedState = Presenter.getInstance().getConnectedState();
    }

    public void setiNetsAdapter(INetsAdapter iNetsAdapter) {
        this.iNetsAdapter = iNetsAdapter;
    }

    public void update(ArrayList<WiFiItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    public void update(WifiInfo connectionInfo, NetworkInfo.State connectedState) {
        this.connectionInfo = connectionInfo;
        this.connectedState = connectedState;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (items.size() == 1 && items.get(0) == null)
            return NO_ITEMS;
        return HAS_ITEMS;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == HAS_ITEMS)
            return new WiFiViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_wifi, parent, false));
        else
            return new NoItemsViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_no_items, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof WiFiViewHolder)
            ((WiFiViewHolder)holder).onBindViewHolder(position);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
