package test.microsoft.com.p2pchattest2;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.support.v4.content.LocalBroadcastManager;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION;


/**
 * Created by juksilve on 28.2.2015.
 */
public class WifiServiceAdvertiser {

    static final public String DSS_WIFISA_VALUES = "test.microsoft.com.mywifimesh.DSS_WIFISA_VALUES";
    static final public String DSS_WIFISA_MESSAGE = "test.microsoft.com.mywifimesh.DSS_WIFISA_MESSAGE";

    LocalBroadcastManager broadcaster;
    Context context;
    private WifiP2pManager p2p;
    private WifiP2pManager.Channel channel;


    public WifiServiceAdvertiser(Context Context, WifiP2pManager Manager, WifiP2pManager.Channel Channel) {
        this.context = Context;
        this.p2p = Manager;
        this.channel = Channel;
        this.broadcaster = LocalBroadcastManager.getInstance(this.context);
    }

    public void Start(String instance) {
        startLocalService(instance);
    }

    public void Stop() {
        stopLocalServices();
    }

    private void startLocalService(String instance) {

        Map<String, String> record = new HashMap<String, String>();
        record.put("available", "visible");

        WifiP2pDnsSdServiceInfo service = WifiP2pDnsSdServiceInfo.newInstance(instance, MainActivity.SERVICE_TYPE, record);

        debug_print("Add local service :" + instance);
        p2p.addLocalService(channel, service, new WifiP2pManager.ActionListener() {
            public void onSuccess() {
                debug_print("Added local service");
            }

            public void onFailure(int reason) {
                debug_print("Adding local service failed, error code " + reason);
            }
        });
    }

    private void stopLocalServices() {
        p2p.clearLocalServices(channel, new WifiP2pManager.ActionListener() {
            public void onSuccess() {
                debug_print("Cleared local services");
            }

            public void onFailure(int reason) {
                debug_print("Clearing local services failed, error code " + reason);
            }
        });
    }

    private void debug_print(String buffer) {

        if(broadcaster != null) {
            Intent intent = new Intent(DSS_WIFISA_VALUES);
            if (buffer != null)
                intent.putExtra(DSS_WIFISA_MESSAGE, buffer);
            broadcaster.sendBroadcast(intent);
        }
    }
}
