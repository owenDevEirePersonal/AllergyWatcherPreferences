package com.deveire.dev.allergywatcherpreferences;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Geocoder;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ResultReceiver;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.deveire.dev.allergywatcherpreferences.bleNfc.DeviceManager;
import com.deveire.dev.allergywatcherpreferences.bleNfc.DeviceManagerCallback;
import com.deveire.dev.allergywatcherpreferences.bleNfc.Scanner;
import com.deveire.dev.allergywatcherpreferences.bleNfc.ScannerCallback;
import com.deveire.dev.allergywatcherpreferences.bleNfc.card.CpuCard;
import com.deveire.dev.allergywatcherpreferences.bleNfc.card.FeliCa;
import com.deveire.dev.allergywatcherpreferences.bleNfc.card.Iso14443bCard;
import com.deveire.dev.allergywatcherpreferences.bleNfc.card.Mifare;
import com.deveire.dev.allergywatcherpreferences.bleNfc.card.Ntag21x;
import com.deveire.dev.allergywatcherpreferences.bleNfc.card.SZTCard;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class SetupPatronActivity extends FragmentActivity implements DownloadCallback<String>
{




    private String currentUID;
    private float currentBalance;

    private TextView scannedCardUIDText;
    private EditText patronNameEditText;
    private EditText patronMaxSaltEditText;
    private Button cancelButton;
    private Button okButton;
    private CheckBox nearSightedCheckbox;
    private CheckBox remainAnomCheckbox;
    private CheckBox suggestionsCheckbox;
    private ArrayList<CheckBox> allergenRadioButtons;

    private double currentUser_MaxSalt;
    private double currentUser_CurrentSalt;
    private boolean currentUser_nearSighted;
    private boolean currentUser_remainAnom;
    private boolean currentUser_wantsSuggestions;
    private ArrayList<String> currentUser_Allergies;



    //[Tile Reader Variables]
    private DeviceManager deviceManager;
    private Scanner mScanner;

    private ProgressDialog dialog = null;

    private BluetoothDevice mNearestBle = null;
    private int lastRssi = -100;

    private int readCardFailCnt = 0;
    private int disconnectCnt = 0;
    private int readCardCnt = 0;
    private String startTimeString;
    private static volatile Boolean getMsgFlag = false;


    private Timer tileReaderTimer;
    private boolean uidIsFound;
    private boolean hasSufferedAtLeastOneFailureToReadUID;

    private boolean stopAllScans;


    private Timer resumeTileScannerConnectionDelayer;

    //[/Tile Reader Variables]

    //[Network Variables]
    private GoogleApiClient mGoogleApiClient;
    private Location locationReceivedFromLocationUpdates;
    private Location userLocation;
    private int locationScanInterval;

    LocationRequest request;
    private final int SETTINGS_REQUEST_ID = 8888;
    private final String SAVED_LOCATION_KEY = "79";

    private boolean pingingServer;

    //private final String serverIP = "http://34.251.66.61:9080/InstructaConServlet/AWServlet";
    //private final String serverIP = "http://192.168.1.188:8080/InstructaConServlet/AWServlet";
    private final String serverIP = "http://34.251.66.61:9080/AWServlet";
    //private final String serverIPAddress = "http://api.eirpin.com/api/TTServlet";
    private String serverURL;
    private NetworkFragment aNetworkFragment;
    private JSONObject jsonToPass;

    private boolean pingingServerFor_userData;
    private boolean pingingServerFor_saveUserData;
    //[/Network Variables]

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_patron);




        currentUID = "";

        scannedCardUIDText = (TextView) findViewById(R.id.scannedCardIDTextView);
        //patronNameEditText = (EditText) findViewById(R.id.nameEditText);
        patronMaxSaltEditText = (EditText) findViewById(R.id.saltEditText);
        cancelButton = (Button) findViewById(R.id.cancelButton);
        okButton = (Button) findViewById(R.id.okButton);

        allergenRadioButtons = new ArrayList<CheckBox>();
        allergenRadioButtons.add((CheckBox)findViewById(R.id.checkBox));
        allergenRadioButtons.add((CheckBox)findViewById(R.id.checkBox2));
        allergenRadioButtons.add((CheckBox)findViewById(R.id.checkBox3));
        allergenRadioButtons.add((CheckBox)findViewById(R.id.checkBox4));
        allergenRadioButtons.add((CheckBox)findViewById(R.id.checkBox5));
        allergenRadioButtons.add((CheckBox)findViewById(R.id.checkBox6));
        allergenRadioButtons.add((CheckBox)findViewById(R.id.checkBox7));

        nearSightedCheckbox = (CheckBox) findViewById(R.id.nearSightedCheckbox);
        remainAnomCheckbox = (CheckBox) findViewById(R.id.emailChefCheckbox);
        suggestionsCheckbox = (CheckBox) findViewById(R.id.suggestionsCheckbox);




        cancelButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                finish();
            }
        });

        okButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                //addPatron();
                //finish();
                if(!currentUID.matches(""))
                {
                    saveDataToServlet(currentUID);
                }
                else
                {
                    Toast.makeText(SetupPatronActivity.this, "Error: Please Scan an id card first", Toast.LENGTH_SHORT).show();
                }
            }
        });



        Log.i("Scanner Buggery", "SetupPatronActivity OnCreate.");
        setupTileScanner();

        //[Network Setup]
        pingingServer = false;
        pingingServerFor_userData = false;
        pingingServerFor_saveUserData = false;

        //aNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), "https://192.168.1.188:8080/smrttrackerserver-1.0.0-SNAPSHOT/hello?isDoomed=yes");
        serverURL = serverIP + "?request=storelocation" + Settings.Secure.ANDROID_ID.toString() + "&name=" + "&lat=" + 0000 + "&lon=" + 0000;

        //[/Network Setup]

    }

    protected  void onPause()
    {
        super.onPause();
        tileReaderTimer.cancel();
        tileReaderTimer.purge();

        resumeTileScannerConnectionDelayer.cancel();
        resumeTileScannerConnectionDelayer.purge();

        //if scanner is connected, disconnect it
        if(deviceManager.isConnection())
        {
            Log.i("Scanner Buggery", "SetupPatron OnPause is connected now disconnecting");
            stopAllScans = true;
            deviceManager.requestDisConnectDevice();
        }

        if(mScanner.isScanning())
        {
            Log.i("Scanner Buggery", "SetupPatron OnPause is scanning now not scanning");
            mScanner.stopScan();
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        Log.i("Scanner Buggery", "SetupPatron onResume resuming connection to scanner.");
        uidIsFound = false;
        hasSufferedAtLeastOneFailureToReadUID = true;
        tileReaderTimer = new Timer();

        //TODO: Consider alternative solution to the onResume Problem
        resumeTileScannerConnectionDelayer = new Timer();
        resumeTileScannerConnectionDelayer.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                Log.i("Scanner Buggery", "SetupPatronActivity OnResume resuming connection after delay.");
                connectToTileScanner();
            }
        }, 1000);

    }

    protected void onStop()
    {
        Log.i("State", "SetupPatronActivity On Stop");
        super.onStop();



        Log.i("Scanner Buggery", "SetupPatron OnStop");
    }



    
    private void updateUIFromData()
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                Log.i("Network Update", "Updating UI: start");

                patronMaxSaltEditText.setText("" + currentUser_MaxSalt);
                nearSightedCheckbox.setChecked(currentUser_nearSighted);
                remainAnomCheckbox.setChecked(currentUser_remainAnom);
                suggestionsCheckbox.setChecked(currentUser_wantsSuggestions);
                Log.i("Network Update", "Updating UI: matching: "  + currentUser_Allergies.toString());
                for (String anAllergy: currentUser_Allergies)
                {
                    Log.i("Network Update", "Updating UI: matching currentUsers: "  + anAllergy);
                    for (CheckBox aCheckbox :allergenRadioButtons)
                    {
                        Log.i("Network Update", "Updating UI: matching: " + aCheckbox.getText().toString().replace(" ", "-") + " to " + anAllergy);
                        if(aCheckbox.getText().toString().replace(" ", "-").equalsIgnoreCase(anAllergy)) //replaces " " with - as thats how its stored on the servlet to make it http friendly.
                        {
                            aCheckbox.setChecked(true);
                        }
                    }
                }
            }
        });
    }

    //+++[TileScanner Code]
    private void setupTileScanner()
    {
        dialog = new ProgressDialog(SetupPatronActivity.this);
        //Set processing bar style(round,revolving)
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        //Set a Button for ProgressDialog
        dialog.setButton("Cancel", new ProgressDialog.OnClickListener(){
            @Override
            public void onClick(DialogInterface dialog, int which) {
                deviceManager.requestDisConnectDevice();
            }
        });
        //set if the processing bar of ProgressDialog is indeterminate
        dialog.setIndeterminate(false);

        //Initial device operation classes
        mScanner = new Scanner(SetupPatronActivity.this, scannerCallback);
        deviceManager = new DeviceManager(SetupPatronActivity.this);
        deviceManager.setCallBack(deviceManagerCallback);


        tileReaderTimer = new Timer();
        uidIsFound = false;
        hasSufferedAtLeastOneFailureToReadUID = false;

        //connectToTileScanner is called from onResume
        //connectToTileScanner();
    }

    //Scanner CallBack
    private ScannerCallback scannerCallback = new ScannerCallback() {
        @Override
        public void onReceiveScanDevice(BluetoothDevice device, int rssi, byte[] scanRecord) {
            super.onReceiveScanDevice(device, rssi, scanRecord);
            System.out.println("Activity found a device：" + device.getName() + "Signal strength：" + rssi );
            //Scan bluetooth and record the one has the highest signal strength
            if ( (device.getName() != null) && (device.getName().contains("UNISMES") || device.getName().contains("BLE_NFC")) ) {
                if (mNearestBle != null) {
                    if (rssi > lastRssi) {
                        mNearestBle = device;
                    }
                }
                else {
                    mNearestBle = device;
                    lastRssi = rssi;
                }
            }
        }

        @Override
        public void onScanDeviceStopped() {
            super.onScanDeviceStopped();
        }
    };

    //Callback function for device manager
    private DeviceManagerCallback deviceManagerCallback = new DeviceManagerCallback()
    {
        @Override
        public void onReceiveConnectBtDevice(boolean blnIsConnectSuc) {
            super.onReceiveConnectBtDevice(blnIsConnectSuc);
            if (blnIsConnectSuc) {
                Log.i("TileScanner", "Activity Connection successful");
                Log.i("TileScanner", "Connection successful!\r\n");
                Log.i("TileScanner", "SDK version：" + deviceManager.SDK_VERSIONS + "\r\n");

                // Send order after 500ms delay
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                handler.sendEmptyMessage(3);
            }
        }

        @Override
        public void onReceiveDisConnectDevice(boolean blnIsDisConnectDevice) {
            super.onReceiveDisConnectDevice(blnIsDisConnectDevice);
            Log.i("TileScanner", "Activity Unlink");
            Log.i("TileScanner", "Unlink!");
            handler.sendEmptyMessage(5);
        }

        @Override
        public void onReceiveConnectionStatus(boolean blnIsConnection) {
            super.onReceiveConnectionStatus(blnIsConnection);
            System.out.println("Activity Callback for Connection Status");
        }

        @Override
        public void onReceiveInitCiphy(boolean blnIsInitSuc) {
            super.onReceiveInitCiphy(blnIsInitSuc);
        }

        @Override
        public void onReceiveDeviceAuth(byte[] authData) {
            super.onReceiveDeviceAuth(authData);
        }

        @Override
        public void onReceiveRfnSearchCard(boolean blnIsSus, int cardType, byte[] bytCardSn, byte[] bytCarATS)
        {
            super.onReceiveRfnSearchCard(blnIsSus, cardType, bytCardSn, bytCarATS);
            if (!blnIsSus)
            {
                return;
            }
            StringBuffer stringBuffer = new StringBuffer();
            for (int i = 0; i < bytCardSn.length; i++)
            {
                stringBuffer.append(String.format("%02x", bytCardSn[i]));
            }

            StringBuffer stringBuffer1 = new StringBuffer();
            for (int i = 0; i < bytCarATS.length; i++)
            {
                stringBuffer1.append(String.format("%02x", bytCarATS[i]));
            }

            final StringBuffer outUID = stringBuffer;
            if (hasSufferedAtLeastOneFailureToReadUID)
            {
                uidIsFound = true;
                onScanLogic(outUID);
            }
            else
            {
                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        Log.i("TileScanner", "UID found without a prior failure, assuming its a tag left on the scanner");
                    }
                });

            }

            Log.i("TileScanner","Activity Activate card callback received：UID->" + stringBuffer + " ATS->" + stringBuffer1);
        }

        @Override
        public void onReceiveRfmSentApduCmd(byte[] bytApduRtnData) {
            super.onReceiveRfmSentApduCmd(bytApduRtnData);

            StringBuffer stringBuffer = new StringBuffer();
            for (int i=0; i<bytApduRtnData.length; i++) {
                stringBuffer.append(String.format("%02x", bytApduRtnData[i]));
            }
            Log.i("TileScanner", "Activity APDU callback received：" + stringBuffer);
        }

        @Override
        public void onReceiveRfmClose(boolean blnIsCloseSuc) {
            super.onReceiveRfmClose(blnIsCloseSuc);
        }
    };


    private void connectToTileScanner()
    {
        if (deviceManager.isConnection()) {
            deviceManager.requestDisConnectDevice();
            return;
        }
        Log.i("TileScanner", "connect To Update: Searching Devices");
        //handler.sendEmptyMessage(0);
        if (!mScanner.isScanning()) {
            mScanner.startScan(0);
            mNearestBle = null;
            lastRssi = -100;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    int searchCnt = 0;
                    while ((mNearestBle == null) && (searchCnt < 50000) && (mScanner.isScanning())) {
                        searchCnt++;
                        try {
                            //Log.i("TileScanner", "connect to Update: Sleeping Thread while scanning");
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    try {
                        Log.i("TileScanner", "connect to Update: Sleeping Thread after scan comeplete");
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    //mScanner.stopScan();
                    if (mNearestBle != null && !deviceManager.isConnection()) {
                        mScanner.stopScan();
                        Log.i("TileScanner", "connect To Update: Connecting to Device");
                        handler.sendEmptyMessage(0);
                        deviceManager.requestConnectBleDevice(mNearestBle.getAddress());
                    }
                    else {
                        Log.i("TileScanner", "connect To Update: Cannot Find Devices");
                        handler.sendEmptyMessage(0);
                    }
                }
            }).start();
        }
    }

    //Read card Demo
    private void readCardDemo() {
        readCardCnt++;
        Log.i("TileScanner", "Activity Send scan/activate order");
        deviceManager.requestRfmSearchCard((byte) 0x00, new DeviceManager.onReceiveRfnSearchCardListener() {
            @Override
            public void onReceiveRfnSearchCard(final boolean blnIsSus, int cardType, byte[] bytCardSn, byte[] bytCarATS) {
                deviceManager.mOnReceiveRfnSearchCardListener = null;
                if ( !blnIsSus ) {
                    Log.i("TileScanner", "No card is found！Please put ShenZhen pass on the bluetooth card reading area first");
                    handler.sendEmptyMessage(0);
                    Log.i("TileScanner", "No card is found！");
                    hasSufferedAtLeastOneFailureToReadUID = true;
                    readCardFailCnt++;

                    runOnUiThread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            //alertDataText.setText("No card detected");
                        }
                    });
                    //handler.sendEmptyMessage(4);
                    return;
                }
                if ( cardType == DeviceManager.CARD_TYPE_ISO4443_B ) {   //Find ISO14443-B card（identity card）
                    final Iso14443bCard card = (Iso14443bCard)deviceManager.getCard();
                    if (card != null) {

                        Log.i("TileScanner", "found ISO14443-B card->UID:(Identity card send 0036000008 order to get UID)\r\n");
                        handler.sendEmptyMessage(0);
                        //Order stream to get Identity card DN code
                        final byte[][] sfzCmdBytes = {
                                {0x00, (byte)0xa4, 0x00, 0x00, 0x02, 0x60, 0x02},
                                {0x00, 0x36, 0x00, 0x00, 0x08},
                                {(byte)0x80, (byte)0xB0, 0x00, 0x00, 0x20},
                        };
                        System.out.println("Send order stream");
                        Handler readSfzHandler = new Handler(SetupPatronActivity.this.getMainLooper()) {
                            @Override
                            public void handleMessage(Message msg) {
                                final Handler theHandler = msg.getTarget();
                                if (msg.what < sfzCmdBytes.length) {  // Execute order stream recurrently
                                    final int index = msg.what;
                                    StringBuffer stringBuffer = new StringBuffer();
                                    for (int i=0; i<sfzCmdBytes[index].length; i++) {
                                        stringBuffer.append(String.format("%02x", sfzCmdBytes[index][i]));
                                    }
                                    Log.i("TileScanner", "Send：" + stringBuffer + "\r\n");
                                    handler.sendEmptyMessage(0);
                                    card.bpduExchange(sfzCmdBytes[index], new Iso14443bCard.onReceiveBpduExchangeListener() {
                                        @Override
                                        public void onReceiveBpduExchange(boolean isCmdRunSuc, byte[] bytBpduRtnData) {
                                            if (!isCmdRunSuc) {
                                                card.close(null);
                                                return;
                                            }
                                            StringBuffer stringBuffer = new StringBuffer();
                                            for (int i=0; i<bytBpduRtnData.length; i++) {
                                                stringBuffer.append(String.format("%02x", bytBpduRtnData[i]));
                                            }
                                            Log.i("TileScanner", "Return：" + stringBuffer + "\r\n");
                                            handler.sendEmptyMessage(0);
                                            theHandler.sendEmptyMessage(index + 1);
                                        }
                                    });
                                }
                                else{ //Order stream has been excuted,shut antenna down
                                    card.close(null);
                                    handler.sendEmptyMessage(4);
                                }
                            }
                        };
                        readSfzHandler.sendEmptyMessage(0);  //Start to execute the first order
                    }
                }
                else if (cardType == DeviceManager.CARD_TYPE_ISO4443_A){  //Find ACPU card
                    Log.i("TileScanner", "Card activation status：" + blnIsSus);
                    Log.i("TileScanner", "Send APDU order - Select main file");

                    final CpuCard card = (CpuCard)deviceManager.getCard();
                    if (card != null) {
                        Log.i("TileScanner", "Found CPU card->UID:" + card.uidToString() + "\r\n");
                        handler.sendEmptyMessage(0);
                        card.apduExchange(SZTCard.getSelectMainFileCmdByte(), new CpuCard.onReceiveApduExchangeListener() {
                            @Override
                            public void onReceiveApduExchange(boolean isCmdRunSuc, byte[] bytApduRtnData) {
                                if (!isCmdRunSuc) {
                                    Log.i("TileScanner", "Main file selection failed");
                                    card.close(null);
                                    readCardFailCnt++;
                                    handler.sendEmptyMessage(4);
                                    return;
                                }
                                Log.i("TileScanner", "Send APDU order- read balance");
                                card.apduExchange(SZTCard.getBalanceCmdByte(), new CpuCard.onReceiveApduExchangeListener() {
                                    @Override
                                    public void onReceiveApduExchange(boolean isCmdRunSuc, byte[] bytApduRtnData) {
                                        if (SZTCard.getBalance(bytApduRtnData) == null) {
                                            Log.i("TileScanner", "This is not ShenZhen Pass！");
                                            handler.sendEmptyMessage(0);
                                            Log.i("TileScanner", "This is not ShenZhen Pass！");
                                            card.close(null);
                                            readCardFailCnt++;
                                            handler.sendEmptyMessage(4);
                                            return;
                                        }
                                        Log.i("TileScanner", "ShenZhen Pass balance：" + SZTCard.getBalance(bytApduRtnData));
                                        handler.sendEmptyMessage(0);
                                        System.out.println("Balance：" + SZTCard.getBalance(bytApduRtnData));
                                        System.out.println("Send APDU order -read 10 trading records");
                                        Handler readSztHandler = new Handler(SetupPatronActivity.this.getMainLooper()) {
                                            @Override
                                            public void handleMessage(Message msg) {
                                                final Handler theHandler = msg.getTarget();
                                                if (msg.what <= 10) {  //Read 10 trading records recurrently
                                                    final int index = msg.what;
                                                    card.apduExchange(SZTCard.getTradeCmdByte((byte) msg.what), new CpuCard.onReceiveApduExchangeListener() {
                                                        @Override
                                                        public void onReceiveApduExchange(boolean isCmdRunSuc, byte[] bytApduRtnData) {
                                                            if (!isCmdRunSuc) {
                                                                card.close(null);
                                                                readCardFailCnt++;
                                                                handler.sendEmptyMessage(4);
                                                                return;
                                                            }
                                                            Log.i("TileScanner", "\r\n" + SZTCard.getTrade(bytApduRtnData));
                                                            handler.sendEmptyMessage(0);
                                                            theHandler.sendEmptyMessage(index + 1);
                                                        }
                                                    });
                                                }
                                                else if (msg.what == 11){ //Shut antenna down
                                                    card.close(null);
                                                    handler.sendEmptyMessage(4);
                                                }
                                            }
                                        };
                                        readSztHandler.sendEmptyMessage(1);
                                    }
                                });
                            }
                        });
                    }
                    else {
                        readCardFailCnt++;
                        handler.sendEmptyMessage(4);
                    }
                }
                else if (cardType == DeviceManager.CARD_TYPE_FELICA) { //find Felica card
                    FeliCa card = (FeliCa) deviceManager.getCard();
                    if (card != null) {
                        Log.i("TileScanner", "Read data block 0000 who serves 008b：\r\n");
                        handler.sendEmptyMessage(0);
                        byte[] pServiceList = {(byte) 0x8b, 0x00};
                        byte[] pBlockList = {0x00, 0x00, 0x00};
                        card.read((byte) 1, pServiceList, (byte) 1, pBlockList, new FeliCa.onReceiveReadListener() {
                            @Override
                            public void onReceiveRead(boolean isSuc, byte pRxNumBlocks, byte[] pBlockData) {
                                if (isSuc) {
                                    StringBuffer stringBuffer = new StringBuffer();
                                    for (int i = 0; i < pBlockData.length; i++) {
                                        stringBuffer.append(String.format("%02x", pBlockData[i]));
                                    }
                                    Log.i("TileScanner", stringBuffer + "\r\n");
                                    handler.sendEmptyMessage(0);
                                }
                                else {
                                    Log.i("TileScanner", "\r\n READing FeliCa FAILED");
                                    handler.sendEmptyMessage(0);
                                }
                            }
                        });

//                        card.write((byte) 1, pServiceList, (byte) 1, pBlockList, new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x18, 0x19, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55}, new FeliCa.onReceiveWriteListener() {
//                            @Override
//                            public void onReceiveWrite(boolean isSuc, byte[] returnBytes) {
//                                msgBuffer.append("" + isSuc + returnBytes);
//                                handler.sendEmptyMessage(0);
//                            }
//                        });
                    }
                }
                else if (cardType == DeviceManager.CARD_TYPE_ULTRALIGHT) { //find Ultralight卡
                    final Ntag21x card  = (Ntag21x) deviceManager.getCard();
                    if (card != null) {
                        Log.i("TileScanner", "find Ultralight card ->UID:" + card.uidToString() + "\r\n");
                        Log.i("TileScanner", "Read tag NDEFText\r\n");
                        handler.sendEmptyMessage(0);

                        card.NdefTextRead(new Ntag21x.onReceiveNdefTextReadListener() {
                            @Override
                            public void onReceiveNdefTextRead(String eer, String returnString) {
                                if (returnString != null) {
                                    Log.i("TileScanner", "read NDEFText successfully：\r\n" + returnString);
                                }
                                if (eer != null) {
                                    Log.i("TileScanner", "reading NDEFText failed：" + eer);
                                }
                                handler.sendEmptyMessage(0);
                                card.close(null);
                            }
                        });
                    }
                }
                else if (cardType == DeviceManager.CARD_TYPE_MIFARE) {
                    final Mifare card = (Mifare)deviceManager.getCard();
                    if (card != null) {
                        Log.i("TileScanner", "Found Mifare card->UID:" + card.uidToString() + "\r\n");
                        Log.i("TileScanner", "Start to verify the first password block\r\n");
                        handler.sendEmptyMessage(0);
                        byte[] key = {(byte) 0xff, (byte) 0xff,(byte) 0xff,(byte) 0xff,(byte) 0xff,(byte) 0xff,};
                        card.authenticate((byte) 1, Mifare.MIFARE_KEY_TYPE_A, key, new Mifare.onReceiveAuthenticateListener() {
                            @Override
                            public void onReceiveAuthenticate(boolean isSuc) {
                                if (!isSuc) {
                                    Log.i("TileScanner", "Verifying password failed\r\n");
                                    handler.sendEmptyMessage(0);
                                }
                                else {
                                    Log.i("TileScanner", "Verify password successfully\r\n");

                                    Log.i("TileScanner", "Charge e-Wallet block 1 1000 Chinese yuan\r\n");
                                    handler.sendEmptyMessage(0);
                                    card.decrementTransfer((byte) 1, (byte) 1, card.getValueBytes(1000), new Mifare.onReceiveDecrementTransferListener() {
                                        @Override
                                        public void onReceiveDecrementTransfer(boolean isSuc) {
                                            if (!isSuc) {
                                                Log.i("TileScanner", "e-Walle is not initialized!\r\n");
                                                handler.sendEmptyMessage(0);
                                                card.close(null);
                                            }
                                            else {
                                                Log.i("TileScanner", "Charge successfully！\r\n");
                                                handler.sendEmptyMessage(0);
                                                card.readValue((byte) 1, new Mifare.onReceiveReadValueListener() {
                                                    @Override
                                                    public void onReceiveReadValue(boolean isSuc, byte address, byte[] valueBytes) {
                                                        if (!isSuc || (valueBytes == null) || (valueBytes.length != 4)) {
                                                            Log.i("TileScanner", "Reading e-Wallet balance failed！\r\n");
                                                            handler.sendEmptyMessage(0);
                                                            card.close(null);
                                                        }
                                                        else {
                                                            int value = card.getValue(valueBytes);
                                                            Log.i("TileScanner", "e-Wallet balance is：" + (value & 0x0ffffffffl) + "\r\n");
                                                            handler.sendEmptyMessage(0);
                                                            card.close(null);
                                                        }
                                                    }
                                                });
                                            }
                                        }
                                    });

//                                    //Increase value
//                                    card.incrementTransfer((byte) 1, (byte) 1, card.getValueBytes(1000), new Mifare.onReceiveIncrementTransferListener() {
//                                        @Override
//                                        public void onReceiveIncrementTransfer(boolean isSuc) {
//                                            if (!isSuc) {
//                                                msgBuffer.append("e-Walle is not initialized!\r\n");
//                                                handler.sendEmptyMessage(0);
//                                                card.close(null);
//                                            }
//                                            else {
//                                                msgBuffer.append("Charge successfully！\r\n");
//                                                handler.sendEmptyMessage(0);
//                                                card.readValue((byte) 1, new Mifare.onReceiveReadValueListener() {
//                                                    @Override
//                                                    public void onReceiveReadValue(boolean isSuc, byte address, byte[] valueBytes) {
//                                                        if (!isSuc || (valueBytes == null) || (valueBytes.length != 4)) {
//                                                            msgBuffer.append("Reading e-Wallet balance failed！\r\n");
//                                                            handler.sendEmptyMessage(0);
//                                                            card.close(null);
//                                                        }
//                                                        else {
//                                                            int value = card.getValue(valueBytes);
//                                                            msgBuffer.append("e-Wallet balance is：" + (value & 0x0ffffffffl) + "\r\n");
//                                                            handler.sendEmptyMessage(0);
//                                                            card.close(null);
//                                                        }
//                                                    }
//                                                });
//                                            }
//                                        }
//                                    });

//                                    //Test read and write block
//                                    msgBuffer.append("write 00112233445566778899001122334455 to block 1\r\n");
//                                    handler.sendEmptyMessage(0);
//                                    card.write((byte) 1, new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55}, new Mifare.onReceiveWriteListener() {
//                                        @Override
//                                        public void onReceiveWrite(boolean isSuc) {
//                                            if (isSuc) {
//                                                msgBuffer.append("Write successfully！\r\n");
//                                                msgBuffer.append("read data from block 1\r\n");
//                                                handler.sendEmptyMessage(0);
//                                                card.read((byte) 1, new Mifare.onReceiveReadListener() {
//                                                    @Override
//                                                    public void onReceiveRead(boolean isSuc, byte[] returnBytes) {
//                                                        if (!isSuc) {
//                                                            msgBuffer.append("reading data from block 1 failed！\r\n");
//                                                            handler.sendEmptyMessage(0);
//                                                        }
//                                                        else {
//                                                            StringBuffer stringBuffer = new StringBuffer();
//                                                            for (int i=0; i<returnBytes.length; i++) {
//                                                                stringBuffer.append(String.format("%02x", returnBytes[i]));
//                                                            }
//                                                            msgBuffer.append("Block 1 data:\r\n" + stringBuffer);
//                                                            handler.sendEmptyMessage(0);
//                                                        }
//                                                        card.close(null);
//                                                    }
//                                                });
//                                            }
//                                            else {
//                                                msgBuffer.append("Write fails！\r\n");
//                                                handler.sendEmptyMessage(0);
//                                            }
//                                        }
//                                    });
                                }
                            }
                        });
                    }
                }
            }
        });
    }

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            getMsgFlag = true;
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy MM dd HH:mm:ss ");
            Date curDate = new Date(System.currentTimeMillis());//Get current time
            String str = formatter.format(curDate);

            if (deviceManager.isConnection()) {
                Log.i("TileScanner", "Ble is connected");
            }
            else {
                Log.i("TileScanner", "Search device");
            }

            if (msg.what == 1) {
                dialog.show();
            }
            else if (msg.what == 2) {
                dialog.dismiss();
            }
            else if (msg.what == 3) {
                handler.sendEmptyMessage(4);
//                deviceManager.requestVersionsDevice(new DeviceManager.onReceiveVersionsDeviceListener() {
//                    @Override
//                    public void onReceiveVersionsDevice(byte versions) {
//                        msgBuffer.append("Device version:" + String.format("%02x", versions) + "\r\n");
//                        handler.sendEmptyMessage(0);
//                        deviceManager.requestBatteryVoltageDevice(new DeviceManager.onReceiveBatteryVoltageDeviceListener() {
//                            @Override
//                            public void onReceiveBatteryVoltageDevice(double voltage) {
//                                msgBuffer.append("Device battery voltage:" + String.format("%.2f", voltage) + "\r\n");
//                                if (voltage < 3.4) {
//                                    msgBuffer.append("Device has low battery, please charge！");
//                                }
//                                else {
//                                    msgBuffer.append("Device has enough battery！");
//                                }
//                                handler.sendEmptyMessage(4);
//                            }
//                        });
//                    }
//                });
            }
            else if (msg.what == 4) {
                if (deviceManager.isConnection()) {
                    getMsgFlag = false;

                    Log.i("TileScanner", "Stuff is happening");

                    scheduleCallForUID();

                    /*readCardDemo();
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            if (getMsgFlag == false) {
                                handler.sendEmptyMessage(4);


                            }
                        }
                    }).start();*/
                }
            }
            else if (msg.what == 5) {
                disconnectCnt++;
                //searchButton.performClick();
                if(!stopAllScans)
                {
                    connectToTileScanner();
                }
            }
        }
    };

    //Recursive Method that schedules a call to the TileScanner to read the card currently on the scanner and return the UID. Then the method calls itself, creating a periodic call to the TileScanner.
    //  ceases calling itself if a UID has already been received and then calls scheduleRestartOfCallForUID().
    private void scheduleCallForUID()
    {
        try
        {
            Log.i("TileScanner", " scheduling the next cycle of the call for uid loop");
            tileReaderTimer.schedule(new TimerTask()
            {
                @Override
                public void run()
                {
                    if (!uidIsFound)
                    {
                        Log.i("TileScanner", " running the next cycle of the call for uid loop");
                        readCardDemo();
                        scheduleCallForUID();
                    }
                    else
                    {
                        scheduleRestartOfCallForUID();
                    }
                }
            }, 2000);
        }
        catch (IllegalStateException e)
        {
            Log.e("TileScanner", "Timer has been canceled, aborting the call for uid loop");

            if(deviceManager.isConnection())
            {
                stopAllScans = true;
                deviceManager.requestDisConnectDevice();
            }

            if(mScanner.isScanning())
            {
                mScanner.stopScan();
            }
        }
    }

    //Schedules a task to restart the recursive method scheduleCallForUID (and thus the periodic calling to the TileScanner to read the card) after a short delay.
    private void scheduleRestartOfCallForUID()
    {
        Log.i("TileScanner", " scheduling the restart of the call for uid loop");
        tileReaderTimer.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                if(uidIsFound)
                {
                    uidIsFound = false;
                    hasSufferedAtLeastOneFailureToReadUID = false; //is used to check if the previously read card was left on the scanner, preventing false positive readings.
                    // If the card was removed then the scanner will report a failure to read.
                    Log.i("TileScanner", " restarting the call for uid loop");
                    scheduleCallForUID();
                }
            }
        }, 3000);
    }

    private void onScanLogic(final StringBuffer outUID)
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                Log.i("TileScanner", "callback received: UID = " + outUID.toString());

                currentUID = outUID.toString();
                Log.e("Setup Patron", "scanned UID: " + currentUID);


                scannedCardUIDText.setText(currentUID);
                retrieveUserDataFromServlet(currentUID);
            }
        });
    }
//+++[/TileScanner Code]




//+++[Network Code]
//**********[Location Update and server pinging Code]
    private void saveDataToServlet(String inUserID)
    {
        try
        {

            serverURL = serverIP + "?request=updateuserdata" + "&" + "userid=" + inUserID;
            serverURL += "&maxsalt=" + patronMaxSaltEditText.getText().toString();
            serverURL += "&currentsalt=" + currentUser_CurrentSalt;


            if(nearSightedCheckbox.isChecked())
            {
                serverURL += "&isnearsighted=true";
            }
            else
            {
                serverURL += "&isnearsighted=false";
            }

            if(remainAnomCheckbox.isChecked())
            {
                serverURL += "&isanom=true";
            }
            else
            {
                serverURL += "&isanom=false";
            }

            if(suggestionsCheckbox.isChecked())
            {
                serverURL += "&iswantingsuggestions=true";
            }
            else
            {
                serverURL += "&iswantingsuggestions=false";
            }

            serverURL += "&allergies=";
            currentUser_Allergies = new ArrayList<String>();
            int count = 0;
            for (CheckBox anAllergyCheckbox : allergenRadioButtons)
            {
                if(anAllergyCheckbox.isChecked())
                {
                    currentUser_Allergies.add(anAllergyCheckbox.getText().toString());
                    if(count == 0) //the first entry shouldn't have a ___ before it.
                    {
                        serverURL += anAllergyCheckbox.getText().toString().replace(" ", "-"); //"-" replaces white spaces so as to be compatible with http
                    }
                    else
                    {
                        serverURL += "___" + anAllergyCheckbox.getText().toString().replace(" ", "-");
                        //___ is used as a divider between the different allergies in the string which is used to pass the allergies to the servlet.
                    }
                    count++;
                }
            }


            //lat and long are doubles, will cause issue? nope

            pingingServerFor_saveUserData = true;
            Log.i("Network Update", "Attempting to start download from saveDataToServlet. " + serverURL);
            aNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), serverURL);
        }
        catch (Exception e)
        {
            Log.e("Network Update", "Error: " + e.toString());
        }

    }

    private void retrieveUserDataFromServlet(String inUserID)
    {
        try
        {
            serverURL = serverIP + "?request=getuserdata" + "&" + "userid=" + currentUID;

            pingingServerFor_userData = true;

            Log.i("Network Update", "Attempting to start download from saveDataToServlet. " + serverURL);
            aNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), serverURL);
        }
        catch (Exception e)
        {
            Log.e("Network Update", "Error: " + e.toString());
        }

    }

    private void createTroubleJson()
    {
        JSONObject paras = new JSONObject();

        jsonToPass = new JSONObject();
        try
        {
            jsonToPass.put("sender", "android_client");
            jsonToPass.put("method_name", "toilet_trouble_ticket");
            jsonToPass.put("params", paras);
        }
        catch (JSONException e)
        {
            Log.e("WebSocket", "Json Error while creating jsonToPass Json: " + e.toString());
        }

        Log.i("WebSocket", "Json Creation Complete: " + jsonToPass.toString());
    }


    @Override
    public void onSaveInstanceState(Bundle savedState)
    {
        savedState.putParcelable(SAVED_LOCATION_KEY, locationReceivedFromLocationUpdates);
        super.onSaveInstanceState(savedState);
    }


    //Update activity based on the results sent back by the servlet.
    @Override
    public void updateFromDownload(String result) {
        //intervalTextView.setText("Interval: " + result);

        if(result != null)
        {
            // matches uses //( as matches() takes a regular expression, where ( is a special character.
            if(!result.matches("failed to connect to /192.168.1.188 \\(port 8080\\) after 3000ms: isConnected failed: ECONNREFUSED \\(Connection refused\\)"))
            {
                Log.e("Download", result);
                try
                {
                    Log.i("Network UPDATE", "Non null result received.");

                    if(pingingServerFor_userData)
                    {
                        pingingServerFor_userData = false;

                        JSONArray jsonResultFromServer = new JSONArray(result);


                        currentUser_MaxSalt = jsonResultFromServer.getJSONObject(0).getDouble("maxSalt");
                        currentUser_CurrentSalt = jsonResultFromServer.getJSONObject(1).getDouble("currentSalt");
                        currentUser_nearSighted = jsonResultFromServer.getJSONObject(2).getBoolean("isNearSighted");
                        currentUser_remainAnom = jsonResultFromServer.getJSONObject(3).getBoolean("isAnom");
                        currentUser_wantsSuggestions = jsonResultFromServer.getJSONObject(4).getBoolean("isWantingSuggestions");
                        currentUser_Allergies = new ArrayList<String>();
                        Log.i("Network Update", "retrieving allergies from json of length: " + jsonResultFromServer.length());
                        for (int i = 5; i < jsonResultFromServer.length(); i++)
                        {
                            currentUser_Allergies.add(jsonResultFromServer.getJSONObject(i).getString("allergy"));
                            Log.i("Network Update", "retrieving allergies from json at index: " + i + " with value: " + jsonResultFromServer.getJSONObject(i).getString("allergy"));
                        }
                        
                        updateUIFromData();

                    }
                    else if (pingingServerFor_saveUserData)
                    {
                        pingingServerFor_saveUserData = false;
                        if(result.matches("Successfully updated user data\n"))
                        {
                            Toast aToast = Toast.makeText(getApplicationContext(), result, Toast.LENGTH_SHORT);
                            aToast.show();
                            finish();
                        }
                        else
                        {
                            Toast aToast = Toast.makeText(getApplicationContext(), "Failed to update user settings: " + result, Toast.LENGTH_LONG);
                            aToast.show();
                        }
                    }


                    /*if (pingingServerFor_alertData)
                    {
                        pingingServerFor_alertData = false;
                        instructionsDataText.setText(result);

                        currentTagName = jsonResultFromServer.getJSONObject(0).getString("name");
                        if(currentTagName.matches("No Name Found"))
                        {
                            currentTagName = currentUID;
                        }

                        switch (jsonResultFromServer.getJSONObject(1).getString("type"))
                        {
                            case "Security Guard": currentTagType = ID_TYPE_Security; break;
                            case "Janitor": currentTagType = ID_TYPE_Janitor; break;
                            default: currentTagType = 0; break;
                        }

                        ArrayList<String> results = new ArrayList<String>();
                        for (int i = 2; i < jsonResultFromServer.length(); i++)
                        {
                            results.add(jsonResultFromServer.getJSONObject(i).getString("alert"));
                        }

                        //mapText.setText(result);
                        speakAlerts(results);
                    }
                    else
                    {

                    }*/
                }
                catch (JSONException e)
                {
                    Log.e("Network Update", "ERROR in Json: " + e.toString());
                }

            }
            else
            {
                //mapText.setText("Error: network unavaiable");
                Log.e("Network UPDATE", "Error: network unavaiable, error: " + result);
                //speakNetworkError();
            }
        }
        else
        {
            //mapText.setText("Error: network unavaiable");
            Log.e("Network UPDATE", "Error: network unavaiable");
            //speakNetworkError();
        }

        Log.e("Download Output", "" + result);
    }

    @Override
    public NetworkInfo getActiveNetworkInfo() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo;
    }

    @Override
    public void onProgressUpdate(int progressCode, int percentComplete) {
        switch(progressCode) {
            // You can add UI behavior for progress updates here.
            case Progress.ERROR:
                Log.e("Progress Error", "there was an error during a progress report at: " + percentComplete + "%");
                break;
            case Progress.CONNECT_SUCCESS:
                Log.i("Progress ", "connection successful during a progress report at: " + percentComplete + "%");
                break;
            case Progress.GET_INPUT_STREAM_SUCCESS:
                Log.i("Progress ", "input stream acquired during a progress report at: " + percentComplete + "%");
                break;
            case Progress.PROCESS_INPUT_STREAM_IN_PROGRESS:
                Log.i("Progress ", "input stream in progress during a progress report at: " + percentComplete + "%");
                break;
            case Progress.PROCESS_INPUT_STREAM_SUCCESS:
                Log.i("Progress ", "input stream processing successful during a progress report at: " + percentComplete + "%");
                break;
        }
    }

    @Override
    public void finishDownloading() {
        pingingServer = false;
        Log.i("Network Update", "finished Downloading");
        if (aNetworkFragment != null) {
            Log.e("Network Update", "network fragment found, canceling download");
            aNetworkFragment.cancelDownload();
        }
    }


//**********[/Location Update and server pinging Code]
//+++[/Network Code]
}






/*


 */