# WiFi Manager Plugin for Headwind MDM

This project is an open source WiFi manager for Android. The network configuration is remotely managed on the server. It is a plugin for the open source mobile device management system for Android:

https://h-mdm.com

## Building

Run in command line:

    gradlew build

or open the project in Android Studio and select **Build - Make Project**

## Configuration

Once you install this WiFi manager through Headwind MDM system, the list of allowed WiFi networks can be configured in Headwind MDM web panel. Open the configuration details, select the **Application settings** tab, and add the following configuration attribute:

* Application: **com.hmdm.wifimanager**
* Attribute: **config**
* Value: **JSON configuration**

The JSON configuration has the following format:

    {
        "allAllowed": <boolean, determines if all networks are allowed, defaults to true>;
        "freeAllowed": <boolean, works only if allAllowed is false, determines if non-protected networks are allowed, defaults to false>;
        "allowed": [
            {
                "ssid": "<SSID of the allowed network, required>",
                "bssid": "<BSSID of the allowed network, optional>",
                "password":<password for this network, optional>"
            },
            ...
        ]
    }

You can setup the policy of WiFi connection by specifying the allowed networks (by default all networks are allowed). If you specify the network password, the user doesn't need to enter the password manually to connect, the network connection will be automatically established.

Sample configuration setting up automatic configuration of a specified WiFi network:

    {
        "allAllowed": false,
        "allowed": [
            "ssid": "My Corporate WiFi",
            "password": "Top$ecret"
        ]
    }



After you add this configuration attribute, click "Save" to apply the configuration.

## Compatibility

On Android 10 and above, the app works in managed (Device Owner) mode only. It should be signed with the same keys as the MDM agent, and use the same shared user. See the deprecation exemption comment here: https://developer.android.com/reference/android/net/wifi/WifiManager#disableNetwork(int)

On Android 11 and 12, apparently due to permission-related bugs in Android OS, the app doesn't work even when running with Device Owner permissions. To make the app available on these OS versions, we added an optional flag WIFI_SUGGESTION, which switches to an alternative (WiFi suggestion) SDK, which is however less powerful and requires user interaction to manage WiFi networks.

Starting with Android 13, the device owner permission issue is apparently fixed, and WiFi manager works well in the device owner mode.