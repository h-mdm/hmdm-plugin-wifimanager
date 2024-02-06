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

package com.hmdm.wifimanager.ui.activities;

import static android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.google.gson.Gson;
import com.hmdm.MDMPushHandler;
import com.hmdm.MDMPushMessage;
import com.hmdm.MDMService;
import com.hmdm.wifimanager.BuildConfig;
import com.hmdm.wifimanager.Presenter;
import com.hmdm.wifimanager.R;
import com.hmdm.wifimanager.model.MDMConfig;
import com.hmdm.wifimanager.model.WiFiItem;
import com.hmdm.wifimanager.ui.fragments.MainFragment;
import com.hmdm.wifimanager.ui.fragments.ParamsFragment;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements MDMService.ResultHandler {
    private final static int REQUEST_PERMISSIONS = 100;
    private final static int REQUEST_LOCATION_SETTINGS = 110;
    private final static String TAG = "HeadwindWiFi";
    private SharedPreferences preferences;
    String disableLocationPref = "DISABLE_LOCATION";

    /**
     * Handler of Headwind MDM notifications.
     */
    class PushHandler extends MDMPushHandler {
        @Override
        public void onMessageReceived(MDMPushMessage mdmPushMessage) {
            getConfig();
        }
    }

    @BindView(R.id.toolbar) Toolbar toolbar;

    private MDMService mdmService;
    private boolean mdmConnected = false;
    private PushHandler pushHandler;

    private void initPreferences() {
         preferences = getApplicationContext().getSharedPreferences("com.hmdm.wifimanager.PREFERENCES", Context.MODE_PRIVATE);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        initPreferences();

        setSupportActionBar(toolbar);

        mdmService = MDMService.getInstance();
        mdmService.connect(this, this);

        if (savedInstanceState == null)
            addFragment(MainFragment.newInstance(), MainFragment.class.getSimpleName());

        // Request permissions once only
        needRequestPermissions(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        initPreferences();

        if (!needRequestPermissions(false)) {
            // Check location service for Android 9 only
            if (!needEnableLocationServices()) {
                Presenter.getInstance().startScan();

                // Register the Headwind MDM notification handler
                if (pushHandler == null) {
                    pushHandler = new PushHandler();
                    pushHandler.register("configUpdated", this);
                }

                getConfig();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Presenter.getInstance().stopScan();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (pushHandler != null) {
            pushHandler.unregister(this);
            pushHandler = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (findByTag(ParamsFragment.class.getSimpleName()) != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            getSupportActionBar().setDisplayShowHomeEnabled(false);
            getSupportActionBar().setTitle(getString(R.string.app_name));

            removeFragment(ParamsFragment.class.getSimpleName());
            showFragment(MainFragment.class.getSimpleName());
            return;
        }

        super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home)
            onBackPressed();
        return true;
    }

    private boolean needRequestPermissions(boolean doRequest) {
        if (preferences.getBoolean(disableLocationPref, false)) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ArrayList<String> request = new ArrayList<>();
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                request.add(Manifest.permission.ACCESS_FINE_LOCATION);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                request.add(Manifest.permission.ACCESS_COARSE_LOCATION);

            if (request.size() > 0 && doRequest) {
                String[] permissions = new String[request.size()];
                permissions = request.toArray(permissions);
                ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);

                return true;
            }
        }

        return false;
    }

    private boolean needEnableLocationServices() {
        boolean needEnable = false;
        if (preferences.getBoolean(disableLocationPref, false)) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            needEnable = true;
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (locationManager != null) {
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                    needEnable = false;
            }

            if (needEnable) {
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setMessage(getString(R.string.need_enable_location_services))
                        .setPositiveButton(getString(R.string.enable), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startActivityForResult(new Intent(ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_LOCATION_SETTINGS);
                            }
                        })
                        .setNeutralButton(getString(R.string.run_anyway), new DialogInterface.OnClickListener(){

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                preferences.edit().putBoolean(disableLocationPref, true).apply();
                            }
                        })
                        .setNegativeButton(getString(R.string.exit), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        })
                        .create();

                dialog.setCancelable(false);
                dialog.setCanceledOnTouchOutside(false);
                dialog.show();
            }
        }

        return needEnable;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0) {
                final ArrayList<String> request = new ArrayList<>();

                for (int i = 0; i < grantResults.length; i++) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED)
                        request.add(permissions[i]);
                }

                if (request.size() > 0) {
                    AlertDialog dialog = new AlertDialog.Builder(this)
                            .setMessage(getString(R.string.need_location_permissions))
                            .setPositiveButton(getString(R.string.provide), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    String[] permissions = new String[request.size()];
                                    permissions = request.toArray(permissions);
                                    ActivityCompat.requestPermissions(MainActivity.this, permissions, REQUEST_PERMISSIONS);
                                }
                            })
                            .setNeutralButton(getString(R.string.run_anyway), new DialogInterface.OnClickListener(){

                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    preferences.edit().putBoolean(disableLocationPref, true).apply();
                                }
                            })
                            .setNegativeButton(getString(R.string.exit), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    finish();
                                }
                            })
                            .create();

                    dialog.setCancelable(false);
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.show();
                }
            }
            else {
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setMessage(getString(R.string.need_location_permissions))
                        .setPositiveButton(getString(R.string.provide), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ArrayList<String> request = new ArrayList<>();
                                request.add(Manifest.permission.ACCESS_FINE_LOCATION);
                                request.add(Manifest.permission.ACCESS_COARSE_LOCATION);

                                String[] permissions = new String[request.size()];
                                permissions = request.toArray(permissions);
                                ActivityCompat.requestPermissions(MainActivity.this, permissions, REQUEST_PERMISSIONS);
                            }
                        })
                        .setNegativeButton(getString(R.string.exit), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        })
                        .create();

                dialog.setCancelable(false);
                dialog.setCanceledOnTouchOutside(false);
                dialog.show();
            }
        }
    }

    @Nullable
    private Fragment findByTag(String tag) {
        return getSupportFragmentManager().findFragmentByTag(tag);
    }

    private void addFragment(Fragment fragment, String tag) {
        getSupportFragmentManager().beginTransaction().add(R.id.content, fragment, tag).commitAllowingStateLoss();
    }

    private void showFragment(String tag) {
        Fragment fragment = findByTag(tag);
        if (fragment != null)
            getSupportFragmentManager().beginTransaction().show(fragment).commitAllowingStateLoss();
    }

    private void hideFragment(String tag) {
        Fragment fragment = findByTag(tag);
        if (fragment != null)
            getSupportFragmentManager().beginTransaction().hide(fragment).commitAllowingStateLoss();
    }

    private void removeFragment(String tag) {
        Fragment fragment = findByTag(tag);
        if (fragment != null)
            getSupportFragmentManager().beginTransaction().remove(fragment).commitAllowingStateLoss();
    }

    @Override
    public void onMDMConnected() {
        mdmConnected = true;
        getConfig();
    }

    @Override
    public void onMDMDisconnected() {
        mdmConnected = false;
        // Retry the connection to MDM in 5 sec (here we are after the Headwind MDM launcher is updated or crashed)
        new Handler().postDelayed(new MDMReconnectRunnable(), 5000);
    }

    public void showWiFiParams(WiFiItem item) {
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setTitle(item.scanResult.SSID);

        hideFragment(MainFragment.class.getSimpleName());
        addFragment(ParamsFragment.newInstance(item), ParamsFragment.class.getSimpleName());
    }

    public class MDMReconnectRunnable implements Runnable {
        @Override
        public void run() {
            if (!mdmService.connect(MainActivity.this, MainActivity.this)) {
                // Retry connection to MDM in one minute
                new Handler().postDelayed(this, 60000);
            }
        }
    }

    private void getConfig() {
        MDMConfig config = null;

        if (mdmConnected) {
            try {
                config = new Gson().fromJson(MDMService.Preferences.get("config",
                        "{\"allAllowed\":true,\"allowed\":[]}"), MDMConfig.class);
            } catch (Exception e) {
                e.printStackTrace();
                config = null;
                //showAlertNoConfig();
            }
        }
        else
            mdmService.connect(this, this);

        if (config == null)
            config = new MDMConfig();

        Presenter.getInstance().setLastConfig(config);
        if (BuildConfig.WIFI_SUGGESTION && Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            Presenter.getInstance().suggestNetworks(this, config);
        }
    }

    private void showAlertNoConfig() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(getString(R.string.app_not_config))
                .setPositiveButton(getString(R.string.exit), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .create();

        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }
}
