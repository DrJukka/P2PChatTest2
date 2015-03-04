package test.microsoft.com.p2pchattest2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.format.Time;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class MainActivity extends ActionBarActivity implements WifiP2pManager.ChannelListener{

    MainActivity that = this;

    public static final String SERVICE_TYPE = "_test_p2p._tcp";
    public static final String SERVICE_INSTANCE = "P2P_test_Nr2";

    MyTextSpeech mySpeech = null;

    MainBCReceiver mBRReceiver;
    private IntentFilter filter;

    private WifiP2pManager p2p;
    private WifiP2pManager.Channel channel;

    private int mInterval = 1000; // 1 second by default, can be changed later
    private Handler timeHandler;
    private int timeCounter = 0;
    Runnable mStatusChecker = new Runnable() {
        @Override
        public void run() {
            // call function to update timer
            timeCounter = timeCounter + 1;
            ((TextView) findViewById(R.id.TimeBox)).setText("T: " + timeCounter);
            timeHandler.postDelayed(mStatusChecker, mInterval);
        }
    };


    Boolean serviceRunning = false;
    WifiServiceAdvertiser mWifiServiceAdvertiser = null;
    WifiServiceSearcher mWifiServiceSearcher = null;
    WifiServiceConnection mWifiServiceConnection = null;

    List<WifiServiceSearcher.ServiceItem> connectedArray = new ArrayList<WifiServiceSearcher.ServiceItem>();

    enum LastConnectionRole {
        NONE,
        GroupOwner,
        Client
    }

    int conCount = 0;
    long tGotData = 0;
    long tGoBigtData = 0;
    long mPeersDiscovered = 0;
    long ServiceDiscovered = 0;
    long tConnected = 0;

    String otherPartyVersion ="";
    File dbgFile;
    OutputStream dbgFileOs;

    LastConnectionRole mLastConnectionRole = LastConnectionRole.NONE;
    public static final int MESSAGE_READ = 0x400 + 1;
    public static final int MY_HANDLE = 0x400 + 2;

    public String CLIENT_PORT_INSTANCE = "38765";
    public String SERVICE_PORT_INSTANCE = "38765";
    GroupOwnerSocketHandler  groupSocket = null;
    ClientSocketHandler clientSocket = null;

    ChatManager chat = null;
    long msgByteCount = 0;
    Handler myHandler  = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_READ:

                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer

                    String readMessage = "";
                    if(msg.arg1 < 30) {
                        msgByteCount = 0;
                        tGoBigtData = 0; // reset me.
                        tGotData = System.currentTimeMillis();
                        readMessage = new String(readBuf, 0, msg.arg1);

                        if(mLastConnectionRole == LastConnectionRole.Client) {
                            final Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                // just to make sure we both got the message
                                public void run() {
                                    print_line("CHAT", "Sending the big buffer now");
                                    byte[] buffer = new byte[1048576]; //Megabyte buffer
                                    new Random().nextBytes(buffer);
                                    chat.write(buffer);
                                    tGoBigtData = System.currentTimeMillis();
                                    WriteDebugline();
                                }
                            }, 2000);
                        }
                        String[] separated = readMessage.split(":");
                        print_line("CHAT", "Buddy: (" + conCount + "): " + separated[0] + "using version: " + separated[1]);
                        otherPartyVersion = separated[1];
                        mySpeech.speak(readMessage + " having version " + otherPartyVersion);
                        conCount = conCount + 1;
                        ((TextView) findViewById(R.id.CountBox)).setText("Msg: " + conCount);
                    }else{
                        tGoBigtData = System.currentTimeMillis();
                        msgByteCount = msgByteCount + msg.arg1;
                        ((TextView) findViewById(R.id.CountBox)).setText("B: " + msgByteCount);
                        if(msgByteCount >= 1048576){
                            WriteDebugline();
                            ((TextView) findViewById(R.id.CountBox)).setText("Msg: " + conCount);
                            readMessage = "Megabyte received in " + ((tGoBigtData - tGotData)/1000) + " seconds";
                            print_line("CHAT", readMessage);
                            mySpeech.speak(readMessage);
                        }
                    }
                    break;

                case MY_HANDLE:
                    Object obj = msg.obj;
                    chat = (ChatManager) obj;

                    String helloBuffer = "Hello ";
                    if(mLastConnectionRole == LastConnectionRole.Client){
                        helloBuffer = helloBuffer + "From Client :";
                    }else{
                        helloBuffer = helloBuffer + "From Group owner :";
                    }

                    helloBuffer =  helloBuffer + Build.VERSION.SDK_INT;

                    chat.write(helloBuffer.getBytes());
            }
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Time t= new Time();
        t.setToNow();

        File path = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);

        String sFileName =  "/NewP2PTest"  + t.yearDay + t.hour+ t.minute + t.second + ".txt";

        try {
            dbgFile = new File(path, sFileName);
            dbgFileOs = new FileOutputStream(dbgFile);

            String dattaa = "Os ,Os other ,Type ,Got services ,Conneted ,first data ,GotBigData \n";

            dbgFileOs.write(dattaa.getBytes());
            dbgFileOs.flush();

            print_line("FILE","File created:" + path + " ,filename : " + sFileName);
        }catch(Exception e){
            print_line("FILE","FileWriter, create file error, :"  + e.toString() );
        }

        mySpeech = new MyTextSpeech(this);
        p2p = (WifiP2pManager) getSystemService(WIFI_P2P_SERVICE);
        if (p2p == null) {
            print_line("","This device does not support Wi-Fi Direct");
        } else {
            channel = p2p.initialize(this, getMainLooper(), this);

            Button showIPButton = (Button) findViewById(R.id.button3);
            showIPButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    MyP2PHelper.printLocalIpAddresses(that);
                }
            });

            Button clearButton = (Button) findViewById(R.id.button2);
            clearButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ((TextView) findViewById(R.id.debugdataBox)).setText("");
                }
            });

            Button toggleButton = (Button) findViewById(R.id.buttonToggle);
            toggleButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    if (serviceRunning) {
                        clearAllService();
                    } else {
                        restartService();
                    }
                }
            });

            mBRReceiver = new MainBCReceiver();
            filter = new IntentFilter();
            filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
            filter.addAction(WifiServiceAdvertiser.DSS_WIFISA_VALUES);
            filter.addAction(WifiServiceSearcher.DSS_WIFISS_PEERAPINFO);
            filter.addAction(WifiServiceSearcher.DSS_WIFISS_PEERCOUNT);
            filter.addAction(WifiServiceSearcher.DSS_WIFISS_VALUES);
            filter.addAction(WifiServiceConnection.DSS_WIFICON_VALUES);
            filter.addAction(WifiServiceConnection.DSS_WIFICON_CONINFO);

            LocalBroadcastManager.getInstance(this).registerReceiver((mBRReceiver), filter);
        }
        timeHandler  = new Handler();
        mStatusChecker.run();

        try{
            groupSocket = new GroupOwnerSocketHandler(myHandler,Integer.parseInt(SERVICE_PORT_INSTANCE),this);
            groupSocket.start();
        }catch (Exception e){
            print_line("CHAT", "groupseocket error, :" + e.toString());
        }
    }

    @Override
    public void onDestroy() {

        timeHandler.removeCallbacks(mStatusChecker);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBRReceiver);
        clearAllService();

        try {
            if (dbgFile != null) {
                dbgFileOs.close();
                dbgFile.delete();
            }
        }catch (Exception e){
            print_line("FILE","dbgFile close error :"  + e.toString() );
        }
    }

    private void clearAllService() {

        if (mWifiServiceAdvertiser != null) {
            mWifiServiceAdvertiser.Stop();
            mWifiServiceAdvertiser = null;
        }

        if(mWifiServiceSearcher  != null){
            mWifiServiceSearcher.Stop();
            mWifiServiceSearcher = null;
        }

        if(mWifiServiceConnection  != null){
            mWifiServiceConnection.Stop();
            mWifiServiceConnection = null;
        }

        serviceRunning = false;
        print_line("", "Stopped");
    }

    private void restartService() {
        // just to be sure, all previous services are cleared
        clearAllService();

        //we need this for listening incoming connection already now
        mWifiServiceConnection = new WifiServiceConnection(that, p2p, channel);
        mWifiServiceConnection.Start();

        mWifiServiceAdvertiser = new WifiServiceAdvertiser(that, p2p, channel);
        mWifiServiceAdvertiser.Start(SERVICE_INSTANCE);

        mWifiServiceSearcher = new WifiServiceSearcher(that, p2p, channel);
        mWifiServiceSearcher.Start();

        serviceRunning = true;
        print_line("", "Started");
    }

    public void print_line(String who,String line) {
        timeCounter = 0;
        ((TextView)findViewById(R.id.debugdataBox)).append(who + " : " + line + "\n");
    }

    public void WriteDebugline() {

        try {
            String dbgData = Build.VERSION.SDK_INT + " ," ;

            dbgData = dbgData + otherPartyVersion + " ,";

            if(mLastConnectionRole == LastConnectionRole.GroupOwner) {
                dbgData = dbgData + "GroupOwner ,";
            }else if(mLastConnectionRole == LastConnectionRole.Client){
                dbgData = dbgData  + "Client ,";
            }else {
                dbgData = dbgData + "Unknown ,";
            }

            dbgData = dbgData + (ServiceDiscovered - mPeersDiscovered) + " ,";
            dbgData = dbgData + (tConnected - ServiceDiscovered) + " ,";
            dbgData = dbgData + (tGotData - tConnected) + " ,";
            dbgData = dbgData + (tGoBigtData - tGotData) + " ,";


            print_line("FILE","write: " + dbgData);
            dbgFileOs.write(dbgData.getBytes());
            dbgFileOs.flush();

            print_line("FILE","From peer-discovert to data: " + ((tGotData - mPeersDiscovered) / 1000) + " seconds.");

            tGotData = 0;
            tGoBigtData = 0;
            mPeersDiscovered = 0;
            ServiceDiscovered = 0;
            tConnected = 0;

        }catch(Exception e){
            print_line("FILE","dbgFile write error :"  + e.toString() );
        }
    }


    @Override
    public void onChannelDisconnected() {
        // TOdo  something in  here
    }

    private WifiServiceSearcher.ServiceItem SelectServiceToConnect(List<WifiServiceSearcher.ServiceItem> available){

        WifiServiceSearcher.ServiceItem  ret = null;

        if(connectedArray.size() > 0 && available.size() > 0) {

            int firstNewMatch = -1;
            int firstOldMatch = -1;

                for (int i = 0; i < available.size(); i++) {
                    if(firstNewMatch >= 0) {
                        break;
                    }
                    for (int ii = 0; ii < connectedArray.size(); ii++) {
                        if (available.get(i).deviceAddress.equals(connectedArray.get(ii).deviceAddress)) {
                            if(firstOldMatch < 0 || firstOldMatch > ii){
                                //find oldest one available that we have connected previously
                                firstOldMatch = ii;
                            }
                            firstNewMatch = -1;
                            break;
                        } else {
                            if (firstNewMatch < 0) {
                                firstNewMatch = i; // select first not connected device
                            }
                        }
                    }
                }

            if (firstNewMatch >= 0){
                ret = available.get(firstNewMatch);
            }else if(firstOldMatch >= 0){
                ret = connectedArray.get(firstOldMatch);
                // we move this to last position
                connectedArray.remove(firstOldMatch);
            }

            //print_line("EEE", "firstNewMatch " + firstNewMatch + ", firstOldMatch: " + firstOldMatch);

        }else if(available.size() > 0){
            ret = available.get(0);
        }
        if(ret != null){
            connectedArray.add(ret);

            // just to set upper limit for the amount of remembered contacts
            // when we have 101, we remove the oldest (that's the top one)
            // from the array
            if(connectedArray.size() > 100){
                connectedArray.remove(0);
            }
        }

        return ret;
    }

    private class MainBCReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    // we got wifi back, so we can re-start now
                    restartService();
                } else {
                    //no wifi availavble, thus we need to stop doing anything;
                    clearAllService();
                }
            }else if (WifiServiceAdvertiser.DSS_WIFISA_VALUES.equals(action)) {
                String s = intent.getStringExtra(WifiServiceAdvertiser.DSS_WIFISA_MESSAGE);
                print_line("SA", s);

            }else if (WifiServiceSearcher.DSS_WIFISS_VALUES.equals(action)) {
                String s = intent.getStringExtra(WifiServiceSearcher.DSS_WIFISS_MESSAGE);
                print_line("SS", s);

            }else if (WifiServiceConnection.DSS_WIFICON_VALUES.equals(action)) {
                String s = intent.getStringExtra(WifiServiceConnection.DSS_WIFICON_MESSAGE);
                print_line("CON", s);

            }else if (WifiServiceSearcher.DSS_WIFISS_PEERCOUNT.equals(action)) {
                int s = intent.getIntExtra(WifiServiceSearcher.DSS_WIFISS_COUNT, -1);
                print_line("SS", "found " + s + " peers");
                mySpeech.speak(s + " peers discovered.");
                mPeersDiscovered  = System.currentTimeMillis();

            }else if (WifiServiceSearcher.DSS_WIFISS_PEERAPINFO.equals(action)) {
                int s = intent.getIntExtra(WifiServiceSearcher.DSS_WIFISS_SERVICECNT, -1);

                print_line("SS", "Services found: " + s );
                List<WifiServiceSearcher.ServiceItem> service = mWifiServiceSearcher.GetServiceList();
                // Select service, save it in a list and start connection with it
                // and do remember to cancel Searching

                if(service.size() > 0) {
                    ServiceDiscovered  = System.currentTimeMillis();

                    if(mWifiServiceConnection == null){
                        mWifiServiceConnection = new WifiServiceConnection(that, p2p, channel);
                        mWifiServiceConnection.Start();
                    }
                    WifiServiceSearcher.ServiceItem selItem = SelectServiceToConnect(service);
                    if(selItem != null){

                        mWifiServiceConnection.Connect(selItem);

                        if(mWifiServiceSearcher  != null){
                            mWifiServiceSearcher.Stop();
                            mWifiServiceSearcher = null;
                        }
                    }else{
                        // we'll get discovery stopped event soon enough
                        // and it starts the discovery again, so no worries :)
                        print_line("", "No devices selected");
                    }
                }
            }else if (WifiServiceConnection.DSS_WIFICON_CONINFO.equals(action)) {

                if(mWifiServiceConnection  != null){
                    WifiP2pInfo pInfo = mWifiServiceConnection.GetConnectionInfo();
                    if(pInfo != null){

                        tConnected  = System.currentTimeMillis();

                        //incase we did not initiate the connection,
                        // then we are indeed still having discovery on
                        if(mWifiServiceSearcher  != null){
                            mWifiServiceSearcher.Stop();
                            mWifiServiceSearcher = null;
                        }

                        String speakout = "";
                        if(pInfo.isGroupOwner){
                            speakout = "Connected as Group owner.";
                            mLastConnectionRole = LastConnectionRole.GroupOwner;
                        }else{
                            mLastConnectionRole = LastConnectionRole.Client;
                            // as we are client, we can not have more connections,
                            // thus we need to cancel advertising.
                            if (mWifiServiceAdvertiser != null) {
                                mWifiServiceAdvertiser.Stop();
                                mWifiServiceAdvertiser = null;
                            }

                            speakout = "Connected as Client, Group IP:" + pInfo.groupOwnerAddress.getHostAddress();
                            clientSocket = new ClientSocketHandler(myHandler,pInfo.groupOwnerAddress,Integer.parseInt(CLIENT_PORT_INSTANCE),that);
                            clientSocket.start();
                        }

                        mySpeech.speak(speakout);
                        print_line("CON", speakout);

                    }else{
                        //we'll get this when we have disconnection event
                        print_line("CON", "WifiP2pInfo is null, restarting all.");
                        restartService();
                    }
                }

            }
        }
    }
}
