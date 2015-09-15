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
  private static final int COLOR_REQUEST=1337;
  private TextView color=null;
  private ColorMixer mixer=null;

  // Log settings
  private static final String TAG = ColorMixer.class.getSimpleName();

  // Orb Settings
  private EditText editTextOrbID;

  // Multicast settings
  MulticastSocket m_socket;
  InetAddress mMulticastAddress;

  // Current Orb command information
  private static int cRed;
  private static int cGreen;
  private static int cBlue;
  private static int cCommandOption;
  private static String cOrbIDs;

  @Override
  public void onCreate(Bundle icicle) {

    /* Turn off multicast filter */
    WifiManager wm = (WifiManager)getSystemService(Context.WIFI_SERVICE);
    WifiManager.MulticastLock multicastLock = wm.createMulticastLock("debuginfo");
    multicastLock.acquire();

    super.onCreate(icicle);
    setContentView(R.layout.main);

    color=(TextView)findViewById(R.id.color);

    mixer=(ColorMixer)findViewById(R.id.mixer);
    mixer.setOnColorChangedListener(onColorChange);

    editTextOrbID = (EditText) findViewById(R.id.tbOrbId);

    MultiCastInstance();
    LoadSettings();

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

  public void MultiCastInstance()
  {
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
    }
      catch (IOException e) {
        e.printStackTrace();
      }
  }

  public class SettingsActivity extends PreferenceActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      addPreferencesFromResource(R.xml.preferences);
    }
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode,
                               Intent result) {
    if (requestCode==COLOR_REQUEST && resultCode==RESULT_OK) {
      mixer.setColor(result.getIntExtra(ColorMixerActivity.COLOR,
              mixer.getColor()));
    }
    else {
      super.onActivityResult(requestCode, resultCode, result);
    }
  }

  private void LoadSettings()
  {
    editTextOrbID.setText(PreferenceManager.getDefaultSharedPreferences(this).getString("OrbID", ""));

    int argb = PreferenceManager.getDefaultSharedPreferences(this).getInt("OrbColors", 0);
    mixer.setColor(argb);
  }

  private void SaveSettings()
  {
    int argb = mixer.getColor();

    SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
    editor.putString("OrbID", editTextOrbID.getText().toString());
    editor.putInt("OrbColors", argb);
    editor.commit();
  }

  private ColorMixer.OnColorChangedListener onColorChange=
          new ColorMixer.OnColorChangedListener() {
            public void onColorChange(int argb) {

              /// On color change send commands to Orb
              Log.d(TAG, String.valueOf(Color.red(argb)));
              Log.d(TAG, String.valueOf(Color.green(argb)));
              Log.d(TAG, String.valueOf(Color.blue(argb)));

              int red = Color.red(argb);
              int green = Color.green(argb);
              int blue = Color.blue(argb);

              String colorText = "R: " + red + " / " + "G: " + green + " / " +"B: " + blue;
              color.setText(colorText);
              String OrbID = String.valueOf(editTextOrbID.getText());
              SaveSettings();
              setColor(red,green,blue, OrbID, 2);
            }
          };

  private void setColor(int red, int green, int blue, String orbID, int commandOption)
  {
    cRed = red;
    cGreen = green;
    cBlue = blue;
    cOrbIDs = orbID;
    cCommandOption = commandOption;

    new SendMultiCastData().execute("");
  }

  private class SendMultiCastData extends AsyncTask<String, Void, String> {
    protected String doInBackground(String... params) {
      {
        try {

          List<String> Orbs = new ArrayList<>();
          Orbs.add(cOrbIDs);

          if (cOrbIDs.contains(",")) {
            Orbs.clear();
            String[] splitcOrbIDs = cOrbIDs.split(",");

            for (String orb : splitcOrbIDs) {
              Orbs.add(orb);
            }
          }

          for (String orb : Orbs) {
            byte commandCount = 24;
            byte[] bytes = new byte[3 + commandCount * 3];

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
        }
        catch (Exception e){}
      }
      return null;
    }
  }

  private ColorMixer.OnColorChangedListener onDialogSet=
          new ColorMixer.OnColorChangedListener() {
            public void onColorChange(int argb) {
              mixer.setColor(argb);
            }
          };

  public void btnTurnOffLights(View v) {
    String OrbID = String.valueOf(editTextOrbID.getText());
    setColor(0, 0, 0, OrbID, 1);
  }
}