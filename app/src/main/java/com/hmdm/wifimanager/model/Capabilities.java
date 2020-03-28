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

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Describes the parameters of network encryption.
 */
public class Capabilities {
    enum AuthMethod {WPA3, WPA2, WPA, OTHER, CCKM, OPEN}
    enum KeyManagementAlgorithm {IEEE8021X, EAP, PSK, WEP, SAE, OWE, NONE}
    enum ChiperMethod {WEP, TKIP, CCMP, NONE}
    enum TopologyMode {IBSS, BSS, ESS}

    public static class Capability {
        public AuthMethod authMethod;
        public KeyManagementAlgorithm keyManagementAlgorithm;
        public ChiperMethod chiperMethod;

        public Capability(AuthMethod authMethod, KeyManagementAlgorithm keyManagementAlgorithm, ChiperMethod chiperMethod) {
            this.authMethod = authMethod;
            this.keyManagementAlgorithm = keyManagementAlgorithm;
            this.chiperMethod = chiperMethod;
        }
    }

    public List<Capability> capabilities = new ArrayList<>();
    public TopologyMode topologyMode;
    public boolean isWps;

    public static Capabilities parse(String capabilitiesString) {
        Capabilities result = new Capabilities();

        if (!TextUtils.isEmpty(capabilitiesString)) {
            Matcher matcher = Pattern.compile("\\[(.*?)\\]").matcher(capabilitiesString);
            ArrayList<String> tokens = new ArrayList<>();
            while (matcher.find()) {
                tokens.add(matcher.group().replace("[", "").replace("]", ""));
            }

            /*String delimiter="\\s+|]\\s*|\\[\\s*";
            String [] array = capabilitiesString.split(delimiter);*/

            if (tokens.size() > 0) {
                for (String item : tokens) {
                    if (item.equals("WPS"))
                        result.isWps = true;
                    else if (item.equals("IBSS"))
                        result.topologyMode = TopologyMode.IBSS;
                    else if (item.equals("BSS"))
                        result.topologyMode = TopologyMode.BSS;
                    else if (item.equals("ESS "))
                        result.topologyMode = TopologyMode.ESS;
                    else {
                        if (item.contains("WEP")) {
                            result.capabilities.add(new Capability(AuthMethod.OTHER, KeyManagementAlgorithm.WEP, ChiperMethod.WEP));
                        }
                        else {
                            AuthMethod authMethod = null;
                            if (item.contains("WPA3"))
                                authMethod = AuthMethod.WPA3;
                            else if (item.contains("WPA2"))
                                authMethod = AuthMethod.WPA2;
                            else if (item.contains("WPA"))
                                authMethod = AuthMethod.WPA;

                            KeyManagementAlgorithm keyManagementAlgorithm = null;
                            if (item.contains("IEEE802.1X"))
                                keyManagementAlgorithm = KeyManagementAlgorithm.IEEE8021X;
                            else if (item.contains("EAP"))
                                keyManagementAlgorithm = KeyManagementAlgorithm.EAP;
                            else if (item.contains("PSK"))
                                keyManagementAlgorithm = KeyManagementAlgorithm.PSK;
                            else if (item.contains("SAE"))
                                keyManagementAlgorithm = KeyManagementAlgorithm.SAE;
                            else if (item.contains("OWE"))
                                keyManagementAlgorithm = KeyManagementAlgorithm.OWE;

                            if (item.contains("TKIP") || item.contains("CCMP")) {
                                if (item.contains("TKIP"))
                                    result.capabilities.add(new Capability(authMethod == null ? AuthMethod.OPEN : authMethod,
                                            keyManagementAlgorithm == null ? KeyManagementAlgorithm.NONE : keyManagementAlgorithm,
                                        ChiperMethod.TKIP));

                                if (item.contains("CCMP"))
                                    result.capabilities.add(new Capability(authMethod == null ? AuthMethod.OPEN : authMethod,
                                            keyManagementAlgorithm == null ? KeyManagementAlgorithm.NONE : keyManagementAlgorithm,
                                            ChiperMethod.CCMP));
                            }
                            else if (authMethod != null || keyManagementAlgorithm != null) {
                                result.capabilities.add(new Capability(authMethod, keyManagementAlgorithm, ChiperMethod.NONE));
                            }
                        }
                    }
                }
            }

            if (result.capabilities.size() <= 0)
                result.capabilities.add(new Capability(AuthMethod.OPEN, KeyManagementAlgorithm.NONE, ChiperMethod.NONE));
        }

        return result;
    }

    public boolean isOpen() {
        return capabilities.size() > 0 && capabilities.get(0).authMethod == AuthMethod.OPEN;
    }

    public String testFormat() {
        String result = "";

        if (capabilities.size() > 0) {
            for (Capability item : capabilities) {
                if (!TextUtils.isEmpty(result))
                    result += "\n";

                if (item.authMethod != null)
                    result += "authMethod: " + item.authMethod.name();
                else
                    result += "authMethod: null";

                if (item.keyManagementAlgorithm != null)
                    result += "; keyManagementAlgorithm: " + item.keyManagementAlgorithm.name();
                else
                    result += "; keyManagementAlgorithm: null";

                if (item.chiperMethod != null)
                    result += "; chiperMethod: " + item.chiperMethod.name();
                else
                    result += "; chiperMethod: null";
            }
        }
        else {
            result += "no capabilities";
        }

        if (topologyMode != null)
            result += "\ntopologyMode: " + topologyMode.name();

        if (isWps)
            result += "\nWPS";
        else
            result += "\nno WPS";

        return result;
    }

    public String format() {
        String result = "";

        if (capabilities.size() > 0) {
            for (Capability item : capabilities) {
                if (item.authMethod == AuthMethod.OPEN)
                    return "";
                else {
                    if (!TextUtils.isEmpty(result))
                        result += "/";

                    result += item.authMethod.name();

                    if (item.keyManagementAlgorithm != KeyManagementAlgorithm.NONE || item.chiperMethod != ChiperMethod.NONE) {
                        result += "(";

                        if (item.keyManagementAlgorithm != KeyManagementAlgorithm.NONE)
                            result += item.keyManagementAlgorithm.name();

                        if (item.keyManagementAlgorithm != KeyManagementAlgorithm.NONE && item.chiperMethod != ChiperMethod.NONE)
                            result += "+";

                        if (item.chiperMethod != ChiperMethod.NONE)
                            result += item.chiperMethod.name();

                        result += ")";
                    }
                }
            }
        }

        return result;
    }
}
