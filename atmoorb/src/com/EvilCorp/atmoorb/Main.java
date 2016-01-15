package com.EvilCorp.atmoorb;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.view.View;
import com.commonsware.cwac.colormixer.ColorMixer;
import com.commonsware.cwac.colormixer.ColorMixerActivity;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Main extends Activity {

    // Default ColorMixer settings
    private static final int COLOR_REQUEST = 1337;
    private TextView color = null;
    private ColorMixer mixer = null;

    // Log settings
    private static final String TAG = ColorMixer.class.getSimpleName();

    // Orb Settings
    private EditText editTextOrbID;
    private EditText editTextOrbLedCount;

    // Multicast settings
    MulticastSocket m_socket;
    InetAddress mMulticastAddress;

    // Current Orb command information
    private static int cRed;
    private static int cGreen;
    private static int cBlue;
    private static int cCommandOption;
    private static String cOrbIDs;
    private static String cOrbLedCount;

    @Override
    public void onCreate(Bundle icicle) {

    /* Turn off multicast filter */
        WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        WifiManager.MulticastLock multicastLock = wm.createMulticastLock("debuginfo");
        multicastLock.acquire();

        super.onCreate(icicle);
        setContentView(R.layout.main);

        color = (TextView) findViewById(R.id.color);

        mixer = (ColorMixer) findViewById(R.id.mixer);
        mixer.setOnColorChangedListener(onColorChange);

        editTextOrbID = (EditText) findViewById(R.id.tbOrbId);
        editTextOrbLedCount = (EditText) findViewById(R.id.tbOrbLedCount);

        MultiCastInstance();
        LoadSettings();
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        editTextOrbID.addTextChangedListener(new TextWatcher() {

            public void afterTextChanged(Editable s) {
                SaveSettings();
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                SaveSettings();
            }
        });
    }

    public void MultiCastInstance() {
        try {
            mMulticastAddress = InetAddress.getByName("239.15.18.2");
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        try {
            m_socket = new MulticastSocket(49692);
            m_socket.setSendBufferSize(50000);
            m_socket.setReceiveBufferSize(50000);

            m_socket.joinGroup(mMulticastAddress);
            m_socket.setLoopbackMode(true);
            m_socket.setTimeToLive(16);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 Intent result) {
        if (requestCode == COLOR_REQUEST && resultCode == RESULT_OK) {
            mixer.setColor(result.getIntExtra(ColorMixerActivity.COLOR,
                    mixer.getColor()));
        } else {
            super.onActivityResult(requestCode, resultCode, result);
        }
    }

    private void LoadSettings() {
        String orbID = PreferenceManager.getDefaultSharedPreferences(this).getString("OrbID", "");
        String ledCount = PreferenceManager.getDefaultSharedPreferences(this).getString("OrbLedCount", "");

        if (orbID == "") {
            editTextOrbID.setText("1");
        } else {
            editTextOrbID.setText(orbID);
        }

        if (ledCount == "") {
            editTextOrbLedCount.setText("24");
        } else {
            editTextOrbLedCount.setText(ledCount);
        }

        int argb = PreferenceManager.getDefaultSharedPreferences(this).getInt("OrbColors", 0);
        mixer.setColor(argb);
    }

    private void SaveSettings() {
        int argb = mixer.getColor();

        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString("OrbID", editTextOrbID.getText().toString());
        editor.putString("OrbLedCount", editTextOrbLedCount.getText().toString());
        editor.putInt("OrbColors", argb);
        editor.commit();
    }

    private ColorMixer.OnColorChangedListener onColorChange =
            new ColorMixer.OnColorChangedListener() {
                public void onColorChange(int argb) {

                    /// On color change send commands to Orb
                    Log.d(TAG, String.valueOf(Color.red(argb)));
                    Log.d(TAG, String.valueOf(Color.green(argb)));
                    Log.d(TAG, String.valueOf(Color.blue(argb)));

                    int red = Color.red(argb);
                    int green = Color.green(argb);
                    int blue = Color.blue(argb);

                    String colorText = "R: " + red + " / " + "G: " + green + " / " + "B: " + blue;
                    color.setText(colorText);
                    String OrbID = String.valueOf(editTextOrbID.getText());
                    String OrbLedCount = String.valueOf(editTextOrbLedCount.getText());
                    SaveSettings();
                    setColor(red, green, blue, OrbID, OrbLedCount, 2);
                }
            };

    private void setColor(int red, int green, int blue, String orbID, String orbLedCount, int commandOption) {
        cRed = red;
        cGreen = green;
        cBlue = blue;
        cOrbIDs = orbID;
        cOrbLedCount = orbLedCount;
        cCommandOption = commandOption;

        new SendMultiCastData().execute("");
    }

    private class SendMultiCastData extends AsyncTask<String, Void, String> {
        protected String doInBackground(String... params) {
            {
                try {

                    List<String> Orbs = new ArrayList<String>();
                    Orbs.add(cOrbIDs);

                    if (cOrbIDs.contains(",")) {
                        Orbs.clear();
                        String[] splitcOrbIDs = cOrbIDs.split(",");

                        for (String orb : splitcOrbIDs) {
                            Orbs.add(orb);
                        }
                    }

                    for (String orb : Orbs) {
                        byte LedCount = Byte.parseByte(cOrbLedCount);
                        byte[] bytes = new byte[5 + LedCount * 3];

                        // Command identifier: C0FFEE
                        bytes[0] = (byte) 0xC0;
                        bytes[1] = (byte) 0xFF;
                        bytes[2] = (byte) 0xEE;
                        bytes[3] = (byte) cCommandOption;

                        // Orb ID
                        bytes[4] = Byte.parseByte(orb);

                        // RED / GREEN / BLUE
                        bytes[5] = (byte) cRed;
                        bytes[6] = (byte) cGreen;
                        bytes[7] = (byte) cBlue;

                        DatagramPacket dp = new DatagramPacket(bytes, bytes.length, mMulticastAddress, 49692);
                        try {
                            m_socket.send(dp);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (Exception e) {
                }
            }
            return null;
        }
    }

    private ColorMixer.OnColorChangedListener onDialogSet =
            new ColorMixer.OnColorChangedListener() {
                public void onColorChange(int argb) {
                    mixer.setColor(argb);
                    editTextOrbID.clearFocus();
                    editTextOrbLedCount.clearFocus();
                }
            };

    public void btnTurnOffLights(View v) {
        String OrbID = String.valueOf(editTextOrbID.getText());
        String OrbLedCount = String.valueOf(editTextOrbLedCount.getText());
        setColor(0, 0, 0, OrbID, OrbLedCount, 1);
    }

    public void btnTurnOnLights(View v) {
        int argb = mixer.getColor();
        setColor(Color.red(argb), Color.green(argb), Color.blue(argb), cOrbIDs, cOrbLedCount, 2);
    }

    public void btnShowColorPicker(View v) {
        ColorPickerDialog colorPickerDialog = new ColorPickerDialog(this, mixer.getColor(), cOrbIDs, cOrbLedCount, new ColorPickerDialog.OnColorSelectedListener() {

            @Override
            public void onColorSelected(int color) {
                mixer.setColor(color);
            }
        });
        colorPickerDialog.show();
    }
}