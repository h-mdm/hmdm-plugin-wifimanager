package com.hmdm.wifimanager;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.Manifest;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.hmdm.MDMService;
import com.hmdm.wifimanager.model.MDMConfig;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedHashSet;

/**
 * Loads WiFi manager provisioning data from an external JSON file.
 * Intended for one-time provisioning on first launch. The file is read only when
 * the provisioning payload is not yet applied. The file is searched in public
 * external locations (Downloads and external storage root) when read access is
 * allowed. After applying, the file is deleted.
 *
 * Example WiFiManagerProvisioning.json:
 * {
 *   "allAllowed": false,
 *   "freeAllowed": false,
 *   "allowed": [
 *     {
 *       "ssid": "CorpWiFi",
 *       "bssid": "00:11:22:33:44:55",
 *       "password": "secret-password",
 *       "hidden": false,
 *       "security": "WPA_PSK"
 *     }
 *   ]
 * }
 */
public class ExternalConfigLoader {

    private static final String TAG = "HeadwindWiFi";
    private static final String FILE_NAME = "WiFiManagerProvisioning.json";

    private final Context context;
    private final Gson gson = new Gson();

    public ExternalConfigLoader(Context context) {
        this.context = context.getApplicationContext();
    }

    @Nullable
    public MDMConfig loadIfPresent() {
        File configFile = locateConfigFile();
        if (configFile == null) {
            return null;
        }
        try {
            String payload = readFile(configFile);
            if (TextUtils.isEmpty(payload)) {
                MDMService.Log.w(TAG, "Provisioning file is empty: " + configFile);
                return null;
            }
            MDMConfig config = gson.fromJson(payload, MDMConfig.class);
            if (config == null) {
                MDMService.Log.w(TAG, "Failed to parse provisioning file: " + configFile);
                return null;
            }
            if (!configFile.delete()) {
                MDMService.Log.w(TAG, "Unable to delete provisioning file: " + configFile);
            }
            MDMService.Log.i(TAG, "WiFi manager provisioning applied from " + configFile.getAbsolutePath());
            return config;
        } catch (IOException | JsonSyntaxException e) {
            MDMService.Log.w(TAG, "Failed to load WiFi provisioning file: " + e.getMessage());
        }
        return null;
    }

    @Nullable
    private File locateConfigFile() {
        LinkedHashSet<File> candidates = new LinkedHashSet<>();
        // Public locations only when we are allowed to read them.
        if (canAccessPublicExternal()) {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (downloadsDir != null) {
                candidates.add(new File(downloadsDir, FILE_NAME));
            }
            File externalRoot = Environment.getExternalStorageDirectory();
            if (externalRoot != null) {
                candidates.add(new File(externalRoot, FILE_NAME));
            }
        }
        for (File candidate : candidates) {
            if (candidate != null && candidate.exists()) {
                return candidate;
            }
        }
        return null;
    }

    private boolean canAccessPublicExternal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private String readFile(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            return baos.toString("UTF-8");
        } finally {
            fis.close();
        }
    }
}
