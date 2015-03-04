package test.microsoft.com.p2pchattest2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;

import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.CountDownTimer;
import android.support.v4.content.LocalBroadcastManager;

import java.util.Collection;

import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION;


/**
 * Created by juksilve on 3.3.2015.
 */
public class WifiServiceConnection implements  WifiP2pManager.ConnectionInfoListener,WifiP2pManager.GroupInfoListener{

    static final public String DSS_WIFICON_VALUES = "test.microsoft.com.mywifimesh.DSS_WIFICON_VALUES";
    static final public String DSS_WIFICON_MESSAGE = "test.microsoft.com.mywifimesh.DSS_WIFICON_MESSAGE";

    static final public String DSS_WIFICON_CONINFO = "test.microsoft.com.mywifimesh.DSS_WIFICON_CONINFO";
    static final public String DSS_WIFICON_ISGROUP = "test.microsoft.com.mywifimesh.DSS_WIFICON_ISGROUP";

    //7 second timer to exit after we get disconnected event
    // to prevent us exiting when getting new clients connecting
    // if we already have a client, and we get new
    //we always get disconnected event first, and then connected
    // but if we lose all clients, we just get disconnect event, and not connected following it.
    CountDownTimer exitTimerOnDisconnexr = new CountDownTimer(7000, 1000) {
        public void onTick(long millisUntilFinished) {
            // not using
        }
        public void onFinish() {
            disconnectedEvent();
        }
    };

    //If we start connecting same time with other, or if the other party
    // does remove service, i.e. got other connection
    // our timing out might take minutes, and if we get successfull connection
    // it should really be established within a minute
    //thus we cancel connect, in event of it taking over 60 seconds.
    CountDownTimer cancellConnectTimer = new CountDownTimer(60000, 1000) {
        public void onTick(long millisUntilFinished) {
            // not using
        }
        public void onFinish() {
            disconnectedEvent();
        }
    };



    WifiServiceConnection that = this;

    LocalBroadcastManager broadcaster;
    Context context;
    private WifiP2pManager p2p;
    private WifiP2pManager.Channel channel;

    private BroadcastReceiver receiver;
    private IntentFilter filter;

    WifiP2pInfo lastInfo = null;
    WifiServiceSearcher.ServiceItem selectedItem;

    boolean connecting = false;
    public WifiServiceConnection(Context Context, WifiP2pManager Manager, WifiP2pManager.Channel Channel) {
        this.context = Context;
        this.p2p = Manager;
        this.channel = Channel;
        this.broadcaster = LocalBroadcastManager.getInstance(this.context);
    }

    public WifiP2pInfo GetConnectionInfo(){
        return lastInfo;
    }

    public void Start() {

        selectedItem = null;
        receiver = new ConectionReceiver();

        filter = new IntentFilter();
        filter.addAction(WIFI_P2P_CONNECTION_CHANGED_ACTION);
        this.context.registerReceiver(receiver, filter);
    }

    public void Connect(WifiServiceSearcher.ServiceItem item) {

        selectedItem = item;
        connecting = true;

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = item.deviceAddress;
        config.wps.setup = WpsInfo.PBC;

        //we'll cancel connection attempt if it took over 60 seconds
        cancellConnectTimer.start();
        p2p.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                debug_print("Connecting to service in " + selectedItem.deviceName);
            }
            @Override
            public void onFailure(int errorCode) {
                debug_print("Failed connecting to service : " + errorCode);
            }
        });
    }


    public void Stop() {
        this.context.unregisterReceiver(receiver);

        exitTimerOnDisconnexr.cancel();
        cancellConnectTimer.cancel();

        if(connecting){
            connecting = false;
            p2p.cancelConnect(channel,new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {debug_print("Connecting cancelled");}
                @Override
                public void onFailure(int errorCode) {debug_print("Failed cancelling connection: " + errorCode);}
            });
        }
        disconnect();
    }

    public  void disconnect() {
        if (p2p != null && channel != null) {
            p2p.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
                @Override
                public void onGroupInfoAvailable(WifiP2pGroup group) {
                    if (group != null && p2p != null && channel != null && group.isGroupOwner()) {
                        p2p.removeGroup(channel, new WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {
                                debug_print("removeGroup onSuccess -");
                                disconnectedEvent();
                            }
                            @Override
                            public void onFailure(int reason) {debug_print("removeGroup onFailure -" + reason);}
                        });
                    }
                }
            });
        }
    }

    private void debug_print(String buffer) {

        if(broadcaster != null) {
            Intent intent = new Intent(DSS_WIFICON_VALUES);
            if (buffer != null)
                intent.putExtra(DSS_WIFICON_MESSAGE, buffer);
            broadcaster.sendBroadcast(intent);
        }
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        lastInfo = info;
        connecting = true;
        if (broadcaster != null) {
            Intent intent = new Intent(DSS_WIFICON_CONINFO);
            intent.putExtra(DSS_WIFICON_ISGROUP, info.isGroupOwner);
            broadcaster.sendBroadcast(intent);
        }

        p2p.requestGroupInfo(channel,this);
    }

    public void disconnectedEvent() {
        lastInfo = null;
        if (broadcaster != null) {
            Intent intent = new Intent(DSS_WIFICON_CONINFO);
            intent.putExtra(DSS_WIFICON_ISGROUP, false);
            broadcaster.sendBroadcast(intent);
        }
    }

    @Override
    public void onGroupInfoAvailable(WifiP2pGroup group) {

        int numm = 0;
        for (WifiP2pDevice peer : group.getClientList()) {
            numm++;
            debug_print("Client " + numm + " : "  + peer.deviceName + " " + peer.deviceAddress);
        }
    }

    private class ConectionReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
           if (WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
               //we'll cancel the connection time-out timer here
               cancellConnectTimer.cancel();
               NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                if (networkInfo.isConnected()) {
                    exitTimerOnDisconnexr.cancel();
                    debug_print("We have connection (exit timer cancelled)!!!");
                    p2p.requestConnectionInfo(channel, that);
                } else {
                    debug_print("We are DIS-connected!!!: " + networkInfo.getDetailedState());

                    if(connecting) {
                        if((lastInfo != null) && lastInfo.isGroupOwner){
                            debug_print("Started timer for exitting");
                            exitTimerOnDisconnexr.start();
                            // we are just getting a new Client connecting to us
                        }else {
                            disconnectedEvent();
                        }
                    }
                }
            }
        }
    }
}
