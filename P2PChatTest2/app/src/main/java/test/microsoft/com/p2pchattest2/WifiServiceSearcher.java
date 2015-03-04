package test.microsoft.com.p2pchattest2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.CountDownTimer;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION;
import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION;
import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION;





/**
 * Created by juksilve on 28.2.2015.
 */
public class WifiServiceSearcher {

    static final public String DSS_WIFISS_VALUES = "test.microsoft.com.mywifimesh.DSS_WIFISS_VALUES";
    static final public String DSS_WIFISS_MESSAGE = "test.microsoft.com.mywifimesh.DSS_WIFISS_MESSAGE";

    static final public String DSS_WIFISS_PEERCOUNT = "test.microsoft.com.mywifimesh.DSS_WIFISS_PEERCOUNT";
    static final public String DSS_WIFISS_COUNT = "test.microsoft.com.mywifimesh.DSS_WIFISS_COUNT";

    static final public String DSS_WIFISS_PEERAPINFO = "test.microsoft.com.mywifimesh.DSS_WIFISS_PEERAPINFO";
    static final public String DSS_WIFISS_SERVICECNT = "test.microsoft.com.mywifimesh.DSS_WIFISS_SERVICECNT";

    WifiServiceSearcher that = this;
    LocalBroadcastManager broadcaster;
    Context context;

    private BroadcastReceiver receiver;
    private IntentFilter filter;

    private WifiP2pManager p2p;
    private WifiP2pManager.Channel channel;
    private WifiP2pManager.DnsSdServiceResponseListener serviceListener;
    private WifiP2pManager.PeerListListener peerListListener;

    enum ServiceState{
        NONE,
        DiscoverPeer,
        DiscoverService
    }
    ServiceState myServiceState = ServiceState.NONE;

    public class ServiceItem{
        public ServiceItem(String instance,String type,String address, String name){
            this.instanceName = instance;
            this.serviceType = type;
            this.deviceAddress = address;
            this.deviceName =  name;
        }
        public String instanceName;
        public String serviceType;
        public String deviceAddress;
        public String deviceName;
    }
    List<ServiceItem> myServiceList = new ArrayList<ServiceItem>();

    CountDownTimer peerDiscoveryTimer = null;

    public WifiServiceSearcher(Context Context, WifiP2pManager Manager, WifiP2pManager.Channel Channel) {
        this.context = Context;
        this.p2p = Manager;
        this.channel = Channel;
        this.broadcaster = LocalBroadcastManager.getInstance(this.context);

        Random ran = new Random(System.currentTimeMillis());

        long millisInFuture = 4000 + (ran.nextInt(6000));

        debug_print("peerDiscoveryTimer timeout value:" + millisInFuture);

        peerDiscoveryTimer = new CountDownTimer(millisInFuture, 1000) {
            public void onTick(long millisUntilFinished) {
                // not using
            }
            public void onFinish() {
                if (broadcaster != null) {
                    Intent intent = new Intent(DSS_WIFISS_PEERAPINFO);
                    intent.putExtra(DSS_WIFISS_SERVICECNT,myServiceList.size());
                    broadcaster.sendBroadcast(intent);
                }
            }
        };
    }

    public List<ServiceItem> GetServiceList(){
        return  myServiceList;
    }

    public void Start() {

        receiver = new ServiceSearcherReceiver();
        filter = new IntentFilter();
        filter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
        filter.addAction(WIFI_P2P_PEERS_CHANGED_ACTION);

        this.context.registerReceiver(receiver, filter);

        peerListListener = new WifiP2pManager.PeerListListener() {

            public void onPeersAvailable(WifiP2pDeviceList peers) {

                final WifiP2pDeviceList pers = peers;
                int numm = 0;
                for (WifiP2pDevice peer : pers.getDeviceList()) {
                    numm++;
                    debug_print("\t" + numm + ": " + peer.deviceName + " " + peer.deviceAddress);
                }

                if (numm > 0) {
                    // this is called still multiple time time-to-time
                    // so need to make sure we only make one service discovery call
                    if(myServiceState != ServiceState.DiscoverService) {
                        startServiceDiscovery();
                    }
                }

                if (broadcaster != null) {
                    Intent intent = new Intent(DSS_WIFISS_PEERCOUNT);
                    intent.putExtra(DSS_WIFISS_COUNT, numm);
                    broadcaster.sendBroadcast(intent);
                }
            }
        };

        serviceListener = new WifiP2pManager.DnsSdServiceResponseListener() {

            public void onDnsSdServiceAvailable(String instanceName, String serviceType, WifiP2pDevice device) {

                if (serviceType.startsWith(MainActivity.SERVICE_TYPE)) {

                    boolean addService = true;
                    for (int i=0; i<myServiceList.size(); i++) {
                        if(myServiceList.get(i).deviceAddress.equals(device.deviceAddress)){
                            addService = false;
                        }
                    }
                    if(addService) {
                        myServiceList.add(new ServiceItem(instanceName, serviceType, device.deviceAddress,device.deviceName));
                    }


                } else {
                    debug_print("Not our Service, :" + MainActivity.SERVICE_TYPE + "!=" + serviceType + ":");
                }

                peerDiscoveryTimer.cancel();
                peerDiscoveryTimer.start();
            }
        };

        p2p.setDnsSdResponseListeners(channel, serviceListener, null);
        startPeerDiscovery();

    }


    public void Stop() {
        this.context.unregisterReceiver(receiver);
        peerDiscoveryTimer.cancel();
        stopDiscovery();
        stopPeerDiscovery();
    }

    private void startPeerDiscovery() {
        p2p.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            public void onSuccess() {
                myServiceState = ServiceState.DiscoverPeer;
                debug_print("Started peer discovery");
            }
            public void onFailure(int reason) {debug_print("Starting peer discovery failed, error code " + reason);}
        });
    }

    private void stopPeerDiscovery() {
        p2p.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
            public void onSuccess() {
                debug_print("Stopped peer discovery");
            }

            public void onFailure(int reason) {
                debug_print("Stopping peer discovery failed, error code " + reason);
            }
        });
    }

    private void startServiceDiscovery() {

        myServiceState = ServiceState.DiscoverService;

        WifiP2pDnsSdServiceRequest request = WifiP2pDnsSdServiceRequest.newInstance(MainActivity.SERVICE_TYPE);
        final Handler handler = new Handler();
        p2p.addServiceRequest(channel, request, new WifiP2pManager.ActionListener() {

            public void onSuccess() {
                debug_print("Added service request");
                handler.postDelayed(new Runnable() {
                    //There are supposedly a possible race-condition bug with the service discovery
                    // thus to avoid it, we are delaying the service discovery start here
                    public void run() {
                        p2p.discoverServices(channel, new WifiP2pManager.ActionListener() {
                            public void onSuccess() {
                                myServiceList.clear();
                                debug_print("Started service discovery");
                                myServiceState = ServiceState.DiscoverService;
                            }
                            public void onFailure(int reason) {
                                stopDiscovery();
                                myServiceState = ServiceState.NONE;
                                debug_print("Starting service discovery failed, error code " + reason);
                            }
                        });
                    }
                }, 1000);
            }

            public void onFailure(int reason) {
                myServiceState = ServiceState.NONE;
                debug_print("Adding service request failed, error code " + reason);
                // No point starting service discovery
            }
        });

    }

    private void stopDiscovery() {
        p2p.clearServiceRequests(channel, new WifiP2pManager.ActionListener() {
            public void onSuccess() {debug_print("Cleared service requests");}
            public void onFailure(int reason) {debug_print("Clearing service requests failed, error code " + reason);}
        });
    }
    private void debug_print(String buffer) {

        if(broadcaster != null) {
            Intent intent = new Intent(DSS_WIFISS_VALUES);
            if (buffer != null)
                intent.putExtra(DSS_WIFISS_MESSAGE, buffer);
            broadcaster.sendBroadcast(intent);
        }
    }
    private class ServiceSearcherReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                if(myServiceState != ServiceState.DiscoverService) {
                    p2p.requestPeers(channel, peerListListener);
                }
            } else if(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION.equals(action)) {

                int state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED);
                String persTatu = "Discovery state changed to ";

                if(state == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED){
                    persTatu = persTatu + "Stopped.";
                    startPeerDiscovery();
                }else if(state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED){
                    persTatu = persTatu + "Started.";
                }else{
                    persTatu = persTatu + "unknown  " + state;
                }
                debug_print(persTatu);

            }
        }
    }
}
