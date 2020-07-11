package com.mediapro.demo;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

public class UserSettingActivity extends Activity {

    private static final String TAG = "SDMedia";

    private EditText et_ServerIp;
    private EditText et_RoomId;
    private EditText et_UserId;
    private EditText et_DomainId;
    private Spinner spinner_Resolution;
    private Spinner spinner_Bitrate;
    private Spinner spinner_VFPS;
    private Spinner spinner_FEC;
    private CheckBox cb_Remeber;
    private CheckBox cb_EnableNack;
    private Button bntOK;
    private Button bntCancel;

    private SharedPreferences sharedPreferences;
    private UserSetting us;

    private List<String> listResolution;
    private List<String> listBitrate;
    private List<Integer> listVFPS;
    private List<Integer> listFEC;

    private ArrayAdapter<String> adapterResolution;
    private ArrayAdapter<String> adapterRate;
    private ArrayAdapter<Integer> adapterVFPS;
    private ArrayAdapter<Integer> adapterFEC;

    // Spinner 选中值
    private String spinnerResolutionChoose;
    private String spinnerBitrateChoose;
    private int spinnerVFPSChoose;
    private int spinnerFECChoose;

    public static final String action = "UserSettingActivity.broadcast.action";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowManager m = getWindowManager();
        Display d = m.getDefaultDisplay();
        WindowManager.LayoutParams p = getWindow().getAttributes();
        p.height = (int) (d.getHeight() * .8);
        p.width = (int) (d.getWidth() * 0.7);
        p.alpha = 1.0f;
        p.dimAmount = 0.0f;

        getWindow().setAttributes(p);

//        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_user_setting);

        // response screen rotation event
//        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);

        setTitle(R.string.user_setting_title);

        // restore data.
        us = new UserSetting();
        sharedPreferences = getSharedPreferences("UserInfo", 0);
        us.ServerIp = sharedPreferences.getString(UserSettingKey.ServerIpKey, "123.57.145.98");
        us.UserId = sharedPreferences.getInt(UserSettingKey.UserIdKey, 136136);
        us.DomainId = sharedPreferences.getInt(UserSettingKey.DomainIdKey, 1);
        us.RoomId = sharedPreferences.getInt(UserSettingKey.RoomIdKey, 1);
        us.Resolution = sharedPreferences.getString(UserSettingKey.ResolutionKey, "640*480");
        us.VFPS = sharedPreferences.getInt(UserSettingKey.VFPSKey, 30);
        us.Bitrate = sharedPreferences.getString(UserSettingKey.BitrateKey, "600kbps");
        us.FEC = sharedPreferences.getInt(UserSettingKey.FECKey, 30);
        us.Remeber = sharedPreferences.getBoolean(UserSettingKey.RemeberKey, false);
        us.EnableNack = sharedPreferences.getBoolean(UserSettingKey.EnableNackKey, true);

        et_ServerIp = (EditText)findViewById(R.id.et_ServerIp);
        et_RoomId = (EditText)findViewById(R.id.et_RoomId);
        et_UserId = (EditText)findViewById(R.id.et_UserId);
        et_DomainId = (EditText)findViewById(R.id.et_DomainId);
        spinner_Resolution = (Spinner) findViewById(R.id.spinner_Resolution);
        spinner_Bitrate = (Spinner)findViewById(R.id.spinner_Bitrate);
        spinner_VFPS = (Spinner)findViewById(R.id.spinner_VFPS);
        spinner_FEC = (Spinner)findViewById(R.id.spinner_FEC);
        cb_Remeber = (CheckBox)findViewById(R.id.cb_Remeber);
        cb_EnableNack = (CheckBox)findViewById(R.id.cb_EnableNack);

        listResolution = new ArrayList<String>();

        listResolution.add("352*288");
        listResolution.add("640*480");
        listResolution.add("1280*720");

        adapterResolution = new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item, listResolution);
        adapterResolution.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner_Resolution.setAdapter(adapterResolution);
        spinner_Resolution.setOnItemSelectedListener(new Spinner.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                // TODO Auto-generated method stub

                arg0.setVisibility(View.VISIBLE);
                spinnerResolutionChoose = (String) spinner_Resolution.getSelectedItem();
            }
            public void onNothingSelected(AdapterView<?> arg0) {
                // TODO Auto-generated method stub

                arg0.setVisibility(View.VISIBLE);
            }
        });
//        spinner_Resolution.setOnTouchListener(new Spinner.OnTouchListener(){
//            public boolean onTouch(View v, MotionEvent event) {
//                // TODO Auto-generated method stub
//                return false;
//            }
//        });
//        spinner_Resolution.setOnFocusChangeListener(new Spinner.OnFocusChangeListener(){
//            public void onFocusChange(View v, boolean hasFocus) {
//                // TODO Auto-generated method stub
//            }
//        });

        spinner_Resolution.setSelection(0, true);
        for (int i = 0; i < listResolution.size(); i++) {
          if (us.Resolution.equals(listResolution.get(i))) {
              spinner_Resolution.setSelection(i, true);
              break;
          }
        }

        listBitrate = new ArrayList<String>();
        listBitrate.add("200kbps");
        listBitrate.add("400kbps");
        listBitrate.add("600kbps");
        listBitrate.add("1000kbps");
        listBitrate.add("1500kbps");
        listBitrate.add("2000kbps");

        adapterRate = new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item, listBitrate);
        adapterRate.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner_Bitrate.setAdapter(adapterRate);
        spinner_Bitrate.setOnItemSelectedListener(new Spinner.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                // TODO Auto-generated method stub

                arg0.setVisibility(View.VISIBLE);
                spinnerBitrateChoose = (String) spinner_Bitrate.getSelectedItem();
            }
            public void onNothingSelected(AdapterView<?> arg0) {
                // TODO Auto-generated method stub

                arg0.setVisibility(View.VISIBLE);
            }
        });

        spinner_Bitrate.setSelection(0, true);
        for (int i = 0; i < listBitrate.size(); i++) {
            if (us.Bitrate.equals(listBitrate.get(i))) {
                spinner_Bitrate.setSelection(i, true);
                break;
            }
        }


        // 帧率10 15 25 30 帧
        listVFPS = new ArrayList<Integer>();
        listVFPS.add(10);
        listVFPS.add(15);
        listVFPS.add(25);
        listVFPS.add(30);

        adapterVFPS = new ArrayAdapter<Integer>(this,android.R.layout.simple_spinner_item, listVFPS);
        adapterVFPS.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner_VFPS.setAdapter(adapterVFPS);
        spinner_VFPS.setOnItemSelectedListener(new Spinner.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                // TODO Auto-generated method stub

                arg0.setVisibility(View.VISIBLE);
                spinnerVFPSChoose = ((Integer)spinner_VFPS.getSelectedItem()).intValue();
            }
            public void onNothingSelected(AdapterView<?> arg0) {
                // TODO Auto-generated method stub

                arg0.setVisibility(View.VISIBLE);
            }
        });

        spinner_VFPS.setSelection(0, true);
        for (int i = 0; i < listVFPS.size(); i++) {
            if (us.VFPS == listVFPS.get(i)) {
                spinner_VFPS.setSelection(i, true);
                break;
            }
        }

//        FEC范围0(自适应冗余度) 20 30 40 50（默认30 ）
        listFEC = new ArrayList<Integer>();
        listFEC.add(0);
        listFEC.add(20);
        listFEC.add(30);
        listFEC.add(40);
        listFEC.add(50);

        adapterFEC = new ArrayAdapter<Integer>(this,android.R.layout.simple_spinner_item, listFEC);
        adapterFEC.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner_FEC.setAdapter(adapterFEC);
        spinner_FEC.setOnItemSelectedListener(new Spinner.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                // TODO Auto-generated method stub

                arg0.setVisibility(View.VISIBLE);
                spinnerFECChoose = ((Integer) spinner_FEC.getSelectedItem()).intValue();
            }
            public void onNothingSelected(AdapterView<?> arg0) {
                // TODO Auto-generated method stub

                arg0.setVisibility(View.VISIBLE);
            }
        });

        spinner_FEC.setSelection(0,true);
        for (int i = 0; i < listFEC.size(); i++) {
            if (us.FEC == listFEC.get(i)) {
                spinner_FEC.setSelection(i, true);
                break;
            }
        }

        et_ServerIp.setText(us.ServerIp);
        et_RoomId.setText(String.valueOf(us.RoomId));
        et_UserId.setText(String.valueOf(us.UserId));
        et_DomainId.setText(String.valueOf(us.DomainId));
        cb_Remeber.setChecked(us.Remeber);
        cb_EnableNack.setChecked(us.EnableNack);

        bntOK = (Button) findViewById(R.id.btn_Ok);
        bntCancel = (Button) findViewById(R.id.btn_cancel);

        bntOK.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    boolean bRestart = false;
                    boolean bWrite = false;
                    if (us.ServerIp != et_ServerIp.getText().toString()||
                            us.UserId != Integer.valueOf(et_UserId.getText().toString())||
                            us.DomainId != Integer.valueOf(et_DomainId.getText().toString())||
                            us.RoomId != Integer.valueOf(et_RoomId.getText().toString()) ||
                            us.Resolution.equals(spinnerResolutionChoose) == false ||
                            us.VFPS != Integer.valueOf(spinnerVFPSChoose) ||
                            us.Bitrate.equals(spinnerBitrateChoose) == false ||
                            us.FEC != spinnerFECChoose ||
                            us.EnableNack != cb_EnableNack.isChecked()) {

                        bRestart = true;
                    }

                    if (us.ServerIp != et_ServerIp.getText().toString()||
                            us.UserId != Integer.valueOf(et_UserId.getText().toString())||
                            us.DomainId != Integer.valueOf(et_DomainId.getText().toString())||
                            us.RoomId != Integer.valueOf(et_RoomId.getText().toString()) ||
                            us.Resolution.equals(spinnerResolutionChoose) == false ||
                            us.VFPS != Integer.valueOf(spinnerVFPSChoose) ||
                            us.Bitrate.equals(spinnerBitrateChoose) == false ||
                            us.FEC != spinnerFECChoose ||
                            us.Remeber != cb_Remeber.isChecked() ||
                            us.EnableNack != cb_EnableNack.isChecked()) {
                        bWrite = true;
                    }

                    if (bWrite == true) {

                        sharedPreferences.edit()
                                .putString(UserSettingKey.ServerIpKey, et_ServerIp.getText().toString())
                                .putInt(UserSettingKey.UserIdKey, Integer.valueOf(et_UserId.getText().toString()))
                                .putInt(UserSettingKey.DomainIdKey, Integer.valueOf(et_DomainId.getText().toString()))
                                .putInt(UserSettingKey.RoomIdKey, Integer.valueOf(et_RoomId.getText().toString()))
                                .putString(UserSettingKey.ResolutionKey, spinnerResolutionChoose)
                                .putInt(UserSettingKey.VFPSKey, Integer.valueOf(spinnerVFPSChoose))
                                .putString(UserSettingKey.BitrateKey, spinnerBitrateChoose)
                                .putInt(UserSettingKey.FECKey, spinnerFECChoose)
                                .putBoolean(UserSettingKey.RemeberKey, cb_Remeber.isChecked())
                                .putBoolean(UserSettingKey.EnableNackKey, cb_EnableNack.isChecked())
                                .commit();
                    }

                    if (bRestart == true) {
                        Intent intent = new Intent(action);
                        sendBroadcast(intent);
                        finish();
                    }

                } catch (Exception e) {

                }
            }
        });
        bntCancel.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });



    }

    private Number getNumberFormat(String format) {
        NumberFormat nf = NumberFormat.getPercentInstance();
        try {
            return nf.parse(format);
        } catch (ParseException e) {
            Log.e(TAG, "getNumberFormat error , format = " + format);
            e.printStackTrace();
        }
        return null;
    }

}
