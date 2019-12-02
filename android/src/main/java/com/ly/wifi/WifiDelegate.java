package com.ly.wifi;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import android.location.LocationManager;
import android.content.IntentSender;
import android.telephony.TelephonyManager;
import java.lang.reflect.Method;

public class
WifiDelegate implements PluginRegistry.RequestPermissionsResultListener {
    private Activity activity;
    private WifiManager wifiManager;
    private PermissionManager permissionManager;
    private static final int REQUEST_ACCESS_FINE_LOCATION_PERMISSION = 1;
    private static final int REQUEST_CHANGE_WIFI_STATE_PERMISSION = 2;
    private static final int REQUEST_ACCESS_COARSE_LOCATION_PERMISSION = 3;
    NetworkChangeReceiver networkReceiver;
    private ScanResultReceiver receiver;
    private WifiInfo info;
    private static final int REQUEST_CHECK = 100;
    private static final int REQUEST_MOBILE_DATA = 200;
    private ConnectivityManager connectivityManager;
    private BroadcastReceiver wifiReceiver;
    private int count = 0;
    private String TAG = this.getClass().getSimpleName();
    private String gSSID = "";

    interface PermissionManager {
        boolean isPermissionGranted(String permissionName);

        void askForPermission(String permissionName, int requestCode);
    }

    public WifiDelegate(final Activity activity, final WifiManager wifiManager) {
        this(activity, wifiManager, null, null, new PermissionManager() {

            @Override
            public boolean isPermissionGranted(String permissionName) {
                return ActivityCompat.checkSelfPermission(activity, permissionName) == PackageManager.PERMISSION_GRANTED;
            }

            @Override
            public void askForPermission(String permissionName, int requestCode) {
                ActivityCompat.requestPermissions(activity, new String[]{permissionName}, requestCode);
            }
        });
    }

    private MethodChannel.Result result;
    private MethodCall methodCall;

    WifiDelegate(
            Activity activity,
            WifiManager wifiManager,
            MethodChannel.Result result,
            MethodCall methodCall,
            PermissionManager permissionManager) {
        this.networkReceiver = new NetworkChangeReceiver();
        this.activity = activity;
        this.wifiManager = wifiManager;
        this.result = result;
        this.methodCall = methodCall;
        this.permissionManager = permissionManager;
    }

    public void getSSID(MethodCall methodCall, MethodChannel.Result result) {
        if (!setPendingMethodCallAndResult(methodCall, result)) {
            finishWithAlreadyActiveError();
            return;
        }
        launchSSID();
    }

    public void getLevel(MethodCall methodCall, MethodChannel.Result result) {
        if (!setPendingMethodCallAndResult(methodCall, result)) {
            finishWithAlreadyActiveError();
            return;
        }
        launchLevel();
    }

    private void launchSSID() {
        String wifiName = wifiManager != null ? wifiManager.getConnectionInfo().getSSID().replace("\"", "") : "";
        if (!wifiName.isEmpty()) {
            result.success(wifiName);
            clearMethodCallAndResult();
        } else {
            finishWithError("unavailable", "wifi name not available.");
        }
    }

    private String convertIp(int ip) {
        return (ip & 0xFF) + "." +
                ((ip >> 8) & 0xFF) + "." +
                ((ip >> 16) & 0xFF) + "." +
                ((ip >> 24) & 0xFF);
    }

    private void launchLevel() {
        int level = wifiManager != null ? wifiManager.getConnectionInfo().getRssi() : 0;
        if (level != 0) {
            if (level <= 0 && level >= -55) {
                result.success(3);
            } else if (level < -55 && level >= -80) {
                result.success(2);
            } else if (level < -80 && level >= -100) {
                result.success(1);
            } else {
                result.success(0);
            }
            clearMethodCallAndResult();
        } else {
            finishWithError("unavailable", "wifi level not available.");
        }
    }

    public void getIP(MethodCall methodCall, MethodChannel.Result result) {
        if (!setPendingMethodCallAndResult(methodCall, result)) {
            finishWithAlreadyActiveError();
            return;
        }
        launchIP();
    }

    public void forgetNetwork(MethodCall methodCall, MethodChannel.Result result) {
        if (!setPendingMethodCallAndResult(methodCall, result)) {
            finishWithAlreadyActiveError();
            return;
        }
        if (!permissionManager.isPermissionGranted(Manifest.permission.CHANGE_WIFI_STATE)) {
            permissionManager.askForPermission(Manifest.permission.CHANGE_WIFI_STATE, REQUEST_CHANGE_WIFI_STATE_PERMISSION);
            return;
        }
        forgetNetwork();
    }

    private boolean isGPSEnabled() {
        LocationManager manager = (LocationManager) activity.getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }


    public void getListDataESP(MethodCall methodCall, MethodChannel.Result result) {
        if (!setPendingMethodCallAndResult(methodCall, result)) {
            finishWithAlreadyActiveError();
            return;
        }
        if(isGPSEnabled()) {
            if (!permissionManager.isPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                permissionManager.askForPermission(Manifest.permission.ACCESS_COARSE_LOCATION, REQUEST_ACCESS_COARSE_LOCATION_PERMISSION);
                return;
            }   
            getListESP();
        }
    }

    public void getListWifi(MethodCall methodCall, MethodChannel.Result result) {
        if (!setPendingMethodCallAndResult(methodCall, result)) {
            finishWithAlreadyActiveError();
            return;
        }
        Log.e(TAG, "Is GPS Enabled : " + String.valueOf(isGPSEnabled()));
        if(isGPSEnabled()) {
            if (!permissionManager.isPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                permissionManager.askForPermission(Manifest.permission.ACCESS_COARSE_LOCATION, REQUEST_ACCESS_COARSE_LOCATION_PERMISSION);
                return;
            }   
            getListWifiNearby();
        }
    }

    private void getListWifiNearby() {
        if(wifiManager != null) {
            receiver = new ScanResultReceiver();
            activity.registerReceiver(receiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
            Log.e(TAG, "Scanning nearby wifi...");
            wifiManager.startScan();
        }
    }

    private void getListESP() {
        wifiReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    List<ScanResult> scanResult = wifiManager.getScanResults();
                    JSONArray data = new JSONArray();
                    info = wifiManager.getConnectionInfo();
                    activity.unregisterReceiver(this);
                    for(int i = 0; i < scanResult.size(); i++) {
                        String ssid = scanResult.get(i).SSID;
                        String status = "";
                        String capabilities = scanResult.get(i).capabilities;
                        if(ssid.replace("\"", "").indexOf("BLiving-") > -1) {
                            data.put(count, ssid);
                            count += 1;
                        }
                    }
                    result.success(data.toString());
                    clearMethodCallAndResult();
                    count = 0;
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        };
        activity.registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        Log.e(TAG, "Scanning nearby ESP ...");
        wifiManager.startScan();
    }

    public void getGateway(MethodCall methodCall, MethodChannel.Result result) {
        if (!setPendingMethodCallAndResult(methodCall, result)) {
            finishWithAlreadyActiveError();
            return;
        }
        getGateway();
    }

    private void getGateway() {
        String gateway = "";
        if(wifiManager != null) {
            gateway = convertIp(wifiManager.getDhcpInfo().gateway);
            if(!gateway.isEmpty()) {
                result.success(gateway);
                clearMethodCallAndResult();
            } else {
                finishWithError("unavailable", "Can't get the gateway!");
            }
        } else {
            finishWithError("unavailable", "WifiManager is null!");
        }
    }

    public int getNetworkId(String ssid) {
        int netId = 0;
        List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();
        for(WifiConfiguration config : list) {
            if(config.SSID.replace("\"", "").equals(ssid)) {
                netId = config.networkId;
            }
        }
        return netId;
    }

    private void enableCurrentNetwork() {
        int networkId = getNetworkId(wifiManager.getConnectionInfo().getSSID());
        wifiManager.enableNetwork(networkId, true);
    }

    private void forgetNetwork() {
        String ssid = methodCall.argument("ssid");
        if(wifiManager != null) {
            if(isConnected(activity.getApplicationContext())) {
                wifiManager.disconnect();
            }
            int netId = getNetworkId(ssid);
            Log.e(TAG, "Network ID : " + String.valueOf(netId));
            Log.e(TAG, "Forget Network..");
            if(wifiManager.removeNetwork(netId)) result.success(1); 
            else result.success(0);
        }
        clearMethodCallAndResult();
    }

    private boolean isConnected(Context context) {
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = null;
        if (connectivityManager != null) {
            networkInfo = connectivityManager.getActiveNetworkInfo();
        }
        return networkInfo != null && networkInfo.getState() == NetworkInfo.State.CONNECTED;
    }

    private void launchIP() {
        NetworkInfo info = ((ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        if (info != null && info.isConnected()) {
            if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
                try {
                    for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                        NetworkInterface intf = en.nextElement();
                        for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                            InetAddress inetAddress = enumIpAddr.nextElement();
                            if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                                result.success(inetAddress.getHostAddress());
                                clearMethodCallAndResult();
                            }
                        }
                    }
                } catch (SocketException e) {
                    e.printStackTrace();
                }
            } else if (info.getType() == ConnectivityManager.TYPE_WIFI) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                String ipAddress = intIP2StringIP(wifiInfo.getIpAddress());
                result.success(ipAddress);
                clearMethodCallAndResult();
            }
        } else {
            finishWithError("unavailable", "ip not available.");
        }
    }

    private static String intIP2StringIP(int ip) {
        return (ip & 0xFF) + "." +
                ((ip >> 8) & 0xFF) + "." +
                ((ip >> 16) & 0xFF) + "." +
                (ip >> 24 & 0xFF);
    }

    public void getWifiList(MethodCall methodCall, MethodChannel.Result result) {
        if (!setPendingMethodCallAndResult(methodCall, result)) {
            finishWithAlreadyActiveError();
            return;
        }
        if (!permissionManager.isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            permissionManager.askForPermission(Manifest.permission.ACCESS_FINE_LOCATION, REQUEST_ACCESS_COARSE_LOCATION_PERMISSION);
            return;
        }
        launchWifiList();
    }

    private void launchWifiList() {
        String key = methodCall.argument("key");
        List<HashMap> list = new ArrayList<>();
        if (wifiManager != null) {
            List<ScanResult> scanResultList = wifiManager.getScanResults();
            for (ScanResult scanResult : scanResultList) {
                String status = "Not connected";
                int level;
                if (scanResult.level <= 0 && scanResult.level >= -55) {
                    level = 3;
                } else if (scanResult.level < -55 && scanResult.level >= -80) {
                    level = 2;
                } else if (scanResult.level < -80 && scanResult.level >= -100) {
                    level = 1;
                } else {
                    level = 0;
                }
                if(getSsid().equals(scanResult.SSID)) status = "Connected";
                HashMap<String, Object> maps = new HashMap<>();
                if (key.equals("")) {
                    maps.put("ssid", scanResult.SSID);
                    maps.put("status", status);
                    maps.put("level", level);
                    list.add(maps);
                } else {
                    if (scanResult.SSID.contains(key)) {
                        maps.put("ssid", scanResult.SSID);
                        maps.put("level", level);
                        list.add(maps);
                    }
                }
            }
        }
        result.success(list);
        clearMethodCallAndResult();
    }

    public void connection(MethodCall methodCall, MethodChannel.Result result) {
        if (!setPendingMethodCallAndResult(methodCall, result)) {
            finishWithAlreadyActiveError();
            return;
        }
        if (!permissionManager.isPermissionGranted(Manifest.permission.CHANGE_WIFI_STATE)) {
            permissionManager.askForPermission(Manifest.permission.CHANGE_WIFI_STATE, REQUEST_ACCESS_FINE_LOCATION_PERMISSION);
            return;
        }
        connection();
    }

    private void connection() {
        String ssid = methodCall.argument("ssid");
        String password = methodCall.argument("password");
        int netId = 0;
        if(!ssid.equals("")) gSSID = ssid;
        if(!checkNetworkExist(ssid)) {
            WifiConfiguration wifiConfig = createWifiConfig(ssid, password);
            if (wifiConfig == null) {
                finishWithError("unavailable", "Wifi Configuration is null.");
                result.success(0);
                clearMethodCallAndResult();
                return;
            }
            netId = wifiManager.addNetwork(wifiConfig);
            wifiManager.saveConfiguration();
        } else netId = getNetworkId(ssid);
        
        Log.e(TAG, "Network ID : " + String.valueOf(netId));
        if (netId == -1) {
            result.success(0);
            clearMethodCallAndResult();
        } else {
            Log.e(TAG, "Connect to " + ssid);
            // support Android O
            // https://stackoverflow.com/questions/50462987/android-o-wifimanager-enablenetwork-cannot-work
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                wifiManager.enableNetwork(netId, true);
                wifiManager.reconnect();
                successConnected(gSSID);
            } else networkReceiver.connect(netId);
        }
    }

    private void successConnected(String ssid) {
        try {
            while(!isConnected(activity.getApplicationContext())) {
                Thread.sleep(1200);
            }
            if(wifiManager.getConnectionInfo().getSSID().replace("\"", "").equals(ssid)) result.success(1);
            else result.success(0);
            clearMethodCallAndResult();
        } catch(Exception e) {e.printStackTrace();}
    }

    private boolean checkNetworkExist(String ssid) {
        List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();
        for(WifiConfiguration config : list) {
            if(config.SSID.replace("\"", "").equals(ssid.trim())) return true;
        }
        return false;
    }

    private WifiConfiguration createWifiConfig(String ssid, String Password) {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"" + ssid + "\"";
        config.allowedAuthAlgorithms.clear();
        config.allowedGroupCiphers.clear();
        config.allowedKeyManagement.clear();
        config.allowedPairwiseCiphers.clear();
        config.allowedProtocols.clear();
        config.preSharedKey = "\"" + Password + "\"";
        config.hiddenSSID = true;
        config.priority = getHighestPriority() + 1;
        config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        config.status = WifiConfiguration.Status.ENABLED;
        return config;
    }

    private int getHighestPriority() {
        int highestPriority = 10;
        if(wifiManager != null) {
            List<WifiConfiguration> config = wifiManager.getConfiguredNetworks();
            for(WifiConfiguration conf : config) {
                if(conf.priority > highestPriority) highestPriority = conf.priority;
            }
        }
        return highestPriority;
    }

    private static boolean getHexKey(String s) {
        if (s == null) return false;
        int len = s.length();
        if (len != 10 && len != 26 && len != 58) return false;
        for (int i = 0; i < len; ++i) {
            char c = s.charAt(i);
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) continue;
            return false;
        }
        return true;
    }

    private WifiConfiguration isExist(WifiManager wifiManager, String ssid) {
        List<WifiConfiguration> existingConfigs = wifiManager.getConfiguredNetworks();
        for (WifiConfiguration existingConfig : existingConfigs) {
            if (existingConfig.SSID.equals("\"" + ssid + "\"")) {
                Log.e(TAG, "\n Wifi Configuration is exist. \n");
                return existingConfig;
            }
        }
        return null;
    }

    public void getMobileDataStatus(MethodCall methodCall, MethodChannel.Result result) {
        if (!setPendingMethodCallAndResult(methodCall, result)) {
            finishWithAlreadyActiveError();
            return;
        }
        // if (!permissionManager.isPermissionGranted(Manifest.permission.MODIFY_PHONE_STATE)) {
        //     Log.e("MobileStatus", "Requesting permission...");
        //     permissionManager.askForPermission(Manifest.permission.MODIFY_PHONE_STATE, REQUEST_CHANGE_MODIFY_PHONE_STATE);
        //     return;
        // }
        // Log.e("MobileStatus", "Getting mobile data status....");
        // getMobileDataState();
        Log.e("MobileStatus", "Getting mobile data status....");
        getMobileDataState();
        // if(permissionManager.isPermissionGranted(Manifest.permission.MODIFY_PHONE_STATE)) {
        // }
    }

    private void getMobileDataState() {
        try {
            TelephonyManager telephonyService = (TelephonyManager) activity.getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
            Method getMobileDataEnabledMethod = telephonyService.getClass().getDeclaredMethod("getDataEnabled");
            if (null != getMobileDataEnabledMethod) {
                boolean mobileDataEnabled = (Boolean) getMobileDataEnabledMethod.invoke(telephonyService);
                result.success(mobileDataEnabled);
            }
        } catch (Exception ex) {
            Log.e(TAG, "Error getting mobile data state", ex);
        }
        clearMethodCallAndResult();
    }

    private String getSsid() {
        return wifiManager.getConnectionInfo().getSSID().replace("\"", "");
    }

    private boolean setPendingMethodCallAndResult(MethodCall methodCall, MethodChannel.Result result) {
        if (this.result != null) {
            return false;
        }
        this.methodCall = methodCall;
        this.result = result;
        return true;
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        boolean permissionGranted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        switch (requestCode) {
            case REQUEST_ACCESS_FINE_LOCATION_PERMISSION:
                if (permissionGranted) {
                    launchWifiList();
                }
                break;
            case REQUEST_CHANGE_WIFI_STATE_PERMISSION:
                if (permissionGranted) {
                    connection();
                }
                break;
            default:
                return false;
        }
        if (!permissionGranted) {
            clearMethodCallAndResult();
        }
        return true;
    }

    private void finishWithAlreadyActiveError() {
        finishWithError("already_active", "wifi is already active");
    }

    private void finishWithError(String errorCode, String errorMessage) {
        result.error(errorCode, errorMessage, null);
        clearMethodCallAndResult();
    }

    private void clearMethodCallAndResult() {
        methodCall = null;
        result = null;
    }

    // support Android O
    // https://stackoverflow.com/questions/50462987/android-o-wifimanager-enablenetwork-cannot-work
    public class NetworkChangeReceiver extends BroadcastReceiver {
        private int netId;
        private boolean willLink = false;

        @Override
        public void onReceive(Context context, Intent intent) {
            NetworkInfo info = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
            if (info.getState() == NetworkInfo.State.DISCONNECTED && willLink) {
                Log.e(TAG, "Network ID Connection : " + String.valueOf(netId));
                wifiManager.enableNetwork(netId, true);
                wifiManager.reconnect();
                if(!gSSID.equals("")) successConnected(gSSID);
                willLink = false;
            }
        }

        public void connect(int netId) {
            wifiManager.disconnect();
            this.netId = netId;
            willLink = true;
        }
    }

    public class ScanResultReceiver extends BroadcastReceiver {

        JSONArray data = new JSONArray();
        List<ScanResult> scanResult = new ArrayList<>();
    
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                scanResult = wifiManager.getScanResults();
                info = wifiManager.getConnectionInfo();
                activity.unregisterReceiver(this);
                for(int i = 0; i < scanResult.size(); i++) {
                    String ssid = scanResult.get(i).SSID;
                    String status = "";
                    String capabilities = scanResult.get(i).capabilities;
                    int level = scanResult.get(i).level;
                    try {
                        JSONObject isi = new JSONObject();
                        if(ssid.equals(info.getSSID().replace("\"", ""))) status = "Connected";
                        else status = "Not connected";
                        isi.put("ssid", ssid);
                        isi.put("status", status);
                        isi.put("level", getLevel(level));
                        isi.put("capabilities", capabilities);
                        data.put(i, isi);
                    } catch(JSONException e) {
                        e.printStackTrace();
                    }
                }
                result.success(data.toString());
                clearMethodCallAndResult();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    
        public JSONArray getData() { return this.data; }
    
        private int getLevel(int level) {
            int result = 0;
            if (level <= 0 && level >= -55) {
                result = 1;
            } else if (level < -55 && level >= -80) {
                result = 2;
            } else if (level < -80 && level >= -100) {
                result = 3;
            } else {
                result = 0;
            }
            return result;
        }
    }
}
