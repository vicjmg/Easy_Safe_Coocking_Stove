package com.android.easy_safe_coocking_stove;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

public class Controlling extends Activity {
    private static final String TAG = "BlueTest5-Controlling";
    private int mMaxChars = 50000;
    private UUID mDeviceUUID;
    private BluetoothSocket mBTSocket;
    private ReadInput mReadThread = null;

    private boolean mIsUserInitiatedDisconnect = false;
    private boolean mIsBluetoothConnected = false;

    private BluetoothDevice mDevice;
    private ProgressDialog progressDialog;

    final byte BYTESTART = 0x31;
    final byte SETTIME  =  0x41;
    final byte STOP    =   0x51;
    final byte READ    =   0x61;

    Button Start;
    Button Stop;
    TextView myLabel;
    TextView myLabel2;
    ImageView myImgView;
    TextView flameHigh;
    TextView flameLow;

    byte[] buffer = new byte[256];
    int pointBuf = 0;

    int timeMax=0;
    int timeMin=0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_controlling);

        ActivityHelper.initialize(this);

        Intent intent = getIntent();
        Bundle b = intent.getExtras();
        mDevice = b.getParcelable(MainActivity.DEVICE_EXTRA);
        mDeviceUUID = UUID.fromString(b.getString(MainActivity.DEVICE_UUID));
        mMaxChars = b.getInt(MainActivity.BUFFER_SIZE);

        Log.d(TAG, "Ready");

        Start=(Button)findViewById(R.id.btnStart);
        Stop=(Button)findViewById(R.id.btnStop);

        myLabel=(TextView) findViewById(R.id.label);
        myLabel2=(TextView) findViewById(R.id.label2);
        myImgView=(ImageView) findViewById(R.id.stove);
        flameHigh=(TextView) findViewById(R.id.inFlameHigh);
        flameLow=(TextView) findViewById(R.id.inFlameLow);

        Thread timer = new Thread() {
            @Override
            public void run() {
                try {
                    while (!this.isInterrupted()) {

                        if(((String)myLabel.getText()).indexOf(':')!=-1){
                            String[] comp1 = ((String)myLabel.getText()).split(":");
                            String[] comp2 = ((String)myLabel2.getText()).split(":");
                            timeMax = Integer.parseInt(comp1[0])*3600 + Integer.parseInt(comp1[1])*60 + Integer.parseInt(comp1[2]);
                            timeMin = Integer.parseInt(comp2[0])*3600 + Integer.parseInt(comp2[1])*60 + Integer.parseInt(comp2[2]);
                        }  else if(myLabel.getText()=="Detenido"){
                            timeMax = 0;
                            timeMin = 0;
                        }

                        Thread.sleep(750);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {

                                if(timeMax>0||timeMin>0){
                                    if(timeMax>0){
                                        timeMax--;
                                        int hoursMax  = timeMax/3600;
                                        int minutesMax = (timeMax/60)%60;
                                        int secondsMax = timeMax%60;

                                        String strHoursMax = hoursMax<10? "0"+hoursMax : ""+hoursMax;
                                        String strMinutesMax = minutesMax<10? "0"+minutesMax : ""+minutesMax;
                                        String strSecondsMax = secondsMax<10? "0"+secondsMax : ""+secondsMax;;

                                        myLabel.setText(strHoursMax + ":" + strMinutesMax + ":" + strSecondsMax);
                                    } else {
                                        timeMin--;
                                        int hoursMin  = timeMin/3600;
                                        int minutesMin = (timeMin/60)%60;
                                        int secondsMin = timeMin%60;

                                        String strHoursMin = hoursMin<10? "0"+hoursMin : ""+hoursMin;
                                        String strMinutesMin = minutesMin<10? "0"+minutesMin : ""+minutesMin;
                                        String strSecondsMin = secondsMin<10? "0"+secondsMin : ""+secondsMin;

                                        myLabel2.setText(strHoursMin + ":" + strMinutesMin + ":" + strSecondsMin);
                                    }
                                }
                            }
                        });
                    }
                } catch (InterruptedException e) {
                }
            }
        };

        timer.start();

        Start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {

                    int hourMax = 0;
                    int minMax = 0;
                    int hourMin = 0;
                    int minMin = 0;

                    try {

                        String[] timeInMax = ((EditText) findViewById(R.id.timeFlameHigh)).getText().toString().split(":");
                        hourMax = Integer.parseInt(timeInMax[0].trim());
                        minMax = Integer.parseInt(timeInMax[1].trim());
                        String[] timeInMin = ((EditText) findViewById(R.id.timeFlameLow)).getText().toString().split(":");
                        hourMin = Integer.parseInt(timeInMin[0].trim());
                        minMin = Integer.parseInt(timeInMin[1].trim());
                        if (((hourMax == 12 && minMax == 00) || (hourMax < 12 && minMax < 60)) && ((hourMin < 12 && minMin < 60) || (hourMin == 12 && minMin == 00))) {
                            byte[] data = new byte[]{BYTESTART, 0, SETTIME, (byte) ('0' + hourMax / 10), (byte) ('0' + hourMax % 10), (byte) ('0' + minMax / 10), (byte) ('0' + minMax % 10), (byte) ('0' + hourMin / 10), (byte) ('0' + hourMin % 10), (byte) ('0' + minMin / 10), (byte) ('0' + minMin % 10), 0, 0};
                            data[1] = (byte) ((data.length - 4) & 0xFF);
                            final short crc = (short) CRC16_ARC(Arrays.copyOfRange(data, 0, data.length - 2));
                            data[data.length - 1] = (byte) (crc & 0xFF);
                            data[data.length - 2] = (byte) (crc >> 8);
                            mBTSocket.getOutputStream().write(data);
                        } else {
                            Toast.makeText(getApplicationContext(), "Especificar tiempos en formato hora, máximo 12 horas", Toast.LENGTH_SHORT).show();
                        }

                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), "Especificar tiempos en formato hora, máximo 12 horas", Toast.LENGTH_SHORT).show();
                    }
                } finally {

                }
            }
        });

        Stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                try {

                    byte[] data = new byte[]{BYTESTART, 0, STOP, 0, 0};
                    data[1] = (byte)((data.length-4)&0xFF);
                    final short crc = (short) CRC16_ARC(Arrays.copyOfRange(data, 0, data.length-2));
                    data[data.length-1] = (byte)(crc&0xFF);
                    data[data.length-2] = (byte)(crc>>8);
                    mBTSocket.getOutputStream().write(data);

                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        });
    }

    private class ReadInput implements Runnable {

        private boolean bStop = false;
        private Thread t;

        public ReadInput() {
            t = new Thread(this, "Input Thread");
            t.start();
        }

        public boolean isRunning() {
            return t.isAlive();
        }

        @Override
        public void run() {
            InputStream inputStream;

            try {
                inputStream = mBTSocket.getInputStream();
                while (!bStop) {
                    if (inputStream.available() > 0) {
                        inputStream.read(buffer);
                        int i = 0;
                        /*
                         * This is needed because new String(buffer) is taking the entire buffer i.e. 256 chars on Android 2.3.4 http://stackoverflow.com/a/8843462/1287554
                         */
                        for (i = 0; i < buffer.length && buffer[i] != 0; i++) {
                        }
                        final String strInput = new String(buffer, 0, i);

                        pointBuf = new Integer(i);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    getCommand();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        });

                        /*
                         * If checked then receive text, better design would probably be to stop thread if unchecked and free resources, but this is a quick fix
                         */

                    }
                    Thread.sleep(1000);
                }
            } catch (IOException e) {
// TODO Auto-generated catch block
                e.printStackTrace();
            } catch (InterruptedException e) {
// TODO Auto-generated catch block
                e.printStackTrace();
            }

        }

        public void stop() {
            bStop = true;
        }

    }

    private class DisConnectBT extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected Void doInBackground(Void... params) {

            if (mReadThread != null) {
                mReadThread.stop();
                while (mReadThread.isRunning())
                    ; // Wait until it stops
                mReadThread = null;

            }

            try {
                mBTSocket.close();
            } catch (IOException e) {
// TODO Auto-generated catch block
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            mIsBluetoothConnected = false;
            if (mIsUserInitiatedDisconnect) {
                finish();
            }
        }

    }

    private void msg(String s) {
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onPause() {
        if (mBTSocket != null && mIsBluetoothConnected) {
            new DisConnectBT().execute();
        }
        Log.d(TAG, "Paused");
        super.onPause();
    }

    @Override
    protected void onResume() {
        if (mBTSocket == null || !mIsBluetoothConnected) {
            new ConnectBT().execute();
        }
        Log.d(TAG, "Resumed");
        super.onResume();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "Stopped");
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
// TODO Auto-generated method stub
        super.onSaveInstanceState(outState);
    }

    private class ConnectBT extends AsyncTask<Void, Void, Void> {
        private boolean mConnectSuccessful = true;

        @Override
        protected void onPreExecute() {

            progressDialog = ProgressDialog.show(Controlling.this, "Hold on", "Connecting");// http://stackoverflow.com/a/11130220/1287554

        }

        @Override
        protected Void doInBackground(Void... devices) {

            try {
                if (mBTSocket == null || !mIsBluetoothConnected) {
                    mBTSocket = mDevice.createInsecureRfcommSocketToServiceRecord(mDeviceUUID);
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    mBTSocket.connect();
                }
            } catch (IOException e) {
                mConnectSuccessful = false;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            if (!mConnectSuccessful) {
                 try{
                    mIsBluetoothConnected = true;
                    mReadThread = new ReadInput();

                    Intent intent = getIntent();
                    Bundle b = intent.getExtras();
                    mDevice = b.getParcelable(MainActivity.DEVICE_EXTRA);
                    mDeviceUUID = UUID.fromString(b.getString(MainActivity.DEVICE_UUID));
                    mMaxChars = b.getInt(MainActivity.BUFFER_SIZE);

                    Log.d(TAG, "Ready");

                    mBTSocket.getOutputStream().write(0x00);
                    msg("Connected to device");
                } catch (IOException e) {
                    Toast.makeText(getApplicationContext(), "Could not connect to device.Please turn on your Hardware", Toast.LENGTH_LONG).show();
                    finish();
                }

            } else {
                msg("Connected to device");
                mIsBluetoothConnected = true;
                mReadThread = new ReadInput();
            }

            progressDialog.dismiss();
        }

    }
    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
    }

    public static int CRC16_ARC(byte[] data) {
        int crc = 0x0000;

        for (int i = 0; i < data.length; ++i) {
            final byte b = data[i];
            for (int j = 0; j < 8; j++) {
                final int k = 7 - j;
                final boolean bit = ((b >> (7 - k) & 1) == 1);
                final boolean c15 = ((crc >> 15 & 1) == 1);
                crc <<= 1;
                if (c15 ^ bit) crc ^= 0x8005;
            }
        }

        return (Integer.reverse(crc) >>> 16) ^ 0x0000;
    }

    public void getCommand() throws InterruptedException {

        if(buffer[0]==BYTESTART){
            if(buffer[1]==pointBuf-4){
                int crc = CRC16_ARC(Arrays.copyOfRange(buffer, 0, pointBuf-2));
                int crc2 = crc;
                if(((buffer[pointBuf-2]&0xFF)==(crc>>8)) && (buffer[pointBuf-1]&0xFF) == (crc&0x00FF)){
                    if(buffer[2]==STOP){
                        timeMax = 0;
                        timeMin = 0;

                        myLabel.setText("Detenido");
                        myLabel2.setText("");

                        flameHigh.setText("");
                        flameLow.setText("");

                        Thread.sleep(100);

                        myLabel.setText("Detenido");
                        myLabel2.setText("");

                        myImgView.setBackgroundResource(R.drawable.stove_off);
                    }

                    if(buffer[2]==READ){

                        String text1 = new String(Arrays.copyOfRange(buffer, 3, 5), StandardCharsets.UTF_8)+":";
                        text1 += new String(Arrays.copyOfRange(buffer, 5, 7), StandardCharsets.UTF_8)+":";
                        text1 += new String(Arrays.copyOfRange(buffer, 7, 9), StandardCharsets.UTF_8);
                        myLabel.setText(text1);

                        String text2 = new String(Arrays.copyOfRange(buffer, 9, 11), StandardCharsets.UTF_8)+":";
                        text2 += new String(Arrays.copyOfRange(buffer, 11, 13), StandardCharsets.UTF_8)+":";
                        text2 += new String(Arrays.copyOfRange(buffer, 13, 15), StandardCharsets.UTF_8);
                        myLabel2.setText(text2);

                        if(!text1.equals("00:00:00")){
                            flameHigh.setText("En flama máxima");
                            flameLow.setText("");
                        } else {
                            flameHigh.setText("");
                             flameLow.setText("En flama mínima");
                        }

                        myImgView.setBackgroundResource(R.drawable.stove_on);

                        if(((String)myLabel.getText()).indexOf(':')!=-1){
                            String[] comp1 = ((String)myLabel.getText()).split(":");
                            String[] comp2 = ((String)myLabel2.getText()).split(":");
                            timeMax = Integer.parseInt(comp1[0])*3600 + Integer.parseInt(comp1[1])*60 + Integer.parseInt(comp1[2]);
                            timeMin = Integer.parseInt(comp2[0])*3600 + Integer.parseInt(comp2[1])*60 + Integer.parseInt(comp2[2]);
                        }  else if(myLabel.getText()=="Detenido"){
                            timeMax = 0;
                            timeMin = 0;
                        }
                    }
                }
            }
        }
        buffer = new byte[256];
    }

}