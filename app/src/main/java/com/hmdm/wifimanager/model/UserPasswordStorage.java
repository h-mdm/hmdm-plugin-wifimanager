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

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class UserPasswordStorage {
    private static final String PREFERENCES_NAME = "com.hmdm.wifimanager.PASSWORDS";
    private static final String PASSWORDS_ATTRIBUTE = "p";
    private static UserPasswordStorage instance = null;
    private UserPasswordStorage(Context context) {
        loadPreferences(context);
    }
    private Map<String, String> passwords = new HashMap<>();
    private Gson gson = new Gson();

    public static UserPasswordStorage getInstance(Context context) {
        if (instance == null) {
            instance = new UserPasswordStorage(context);
        }
        return instance;
    }

    private void loadPreferences(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        Type type = new TypeToken<Map<String, String>>() {}.getType();
        passwords = gson.fromJson(preferences.getString(PASSWORDS_ATTRIBUTE, "{}"), type);
    }

    private void savePreferences(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        String json = gson.toJson(passwords);
        preferences.edit().putString(PASSWORDS_ATTRIBUTE, json).commit();
    }

    public void deletePassword(String id, Context context) {
        passwords.remove(id);
        savePreferences(context);
    }

    public void savePassword(String id, String password, Context context) {
        passwords.put(id, password);
        savePreferences(context);
    }

    public String getPasswordForNetwork(String id) {
        String result = passwords.get(id);
        return result != null ? result: "";
    }
}
