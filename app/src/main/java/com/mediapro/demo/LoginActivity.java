package com.mediapro.demo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo.State;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;


public class LoginActivity extends Activity implements OnClickListener {
	private static final String TAG = "SDMedia";
	
	private EditText etIP;
	private EditText etRoom;
	private EditText etSendPosition;

	private Button loginButton;
	private Button serverIpClearBtn;
	private Button roomIdClearBtn;
	private Button sendPositionClearBtn;

	private int userId = 0;
	private int domainId = 1;
	private int roomId = 0;
	private int sendPostison = -1;

	private boolean hasNetworkConnected = false;
	private boolean bRemeber = false;

	private SharedPreferences sharedPreferences;

	private TextWatcher serverIpClearTextWatcher = new TextWatcher() {

		@Override
		public void afterTextChanged(Editable s) {
			// TODO Auto-generated method stub
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count,
                                      int after) {
			// TODO Auto-generated method stub
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before,
                                  int count) {
			etRoom.setText("");
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.login);

		init();
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
	}

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
	}

	private void init() {
		sharedPreferences = getSharedPreferences("UserInfo", 0);
		bRemeber = sharedPreferences.getBoolean("remember", false);

		etIP = (EditText)findViewById(R.id.login_edit_ip);
		etIP.addTextChangedListener(serverIpClearTextWatcher);
		etRoom = (EditText)findViewById(R.id.login_edit_room);
		etSendPosition = (EditText) findViewById(R.id.et_send_position);

		serverIpClearBtn = (Button)findViewById(R.id.ip_clear_btn);
		serverIpClearBtn.setOnClickListener(this);
		roomIdClearBtn = (Button)findViewById(R.id.room_clear_btn);
		roomIdClearBtn.setOnClickListener(this);
		sendPositionClearBtn = (Button) findViewById(R.id.send_position_clear_btn);
		sendPositionClearBtn.setOnClickListener(this);

		loginButton = (Button)findViewById(R.id.login_button_login);
		loginButton.setOnClickListener(this);

		if (bRemeber) {
			etIP.setText(sharedPreferences.getString(UserSettingKey.ServerIpKey,"")+"");
			etRoom.setText(sharedPreferences.getInt(UserSettingKey.RoomIdKey, 0)+"");
			etSendPosition.setText(sharedPreferences.getInt(UserSettingKey.SendPositionKey, -1)+"");
		}
	}


	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		switch(v.getId()){
		case R.id.login_button_login:
			login();
			break;
		case R.id.ip_clear_btn:
			etIP.setText("");
			break;
		case R.id.room_clear_btn:
			etRoom.setText("");
			break;
		case R.id.send_position_clear_btn:
			etSendPosition.setText("");
			break;
		}
	}
	
public void login() {

		ConnectivityManager manager = (ConnectivityManager) getApplicationContext()
		.getSystemService(Context.CONNECTIVITY_SERVICE);
		//确保移动运营商网络或者WIFI网络连通
		State state = manager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).getState();
		if(State.CONNECTED == state) {
			hasNetworkConnected = true;
		}

		if (hasNetworkConnected == false) {
			state = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
			if(State.CONNECTED == state)
			{
				hasNetworkConnected = true;
			}
		}
		
		if(hasNetworkConnected == false) {
			Toast.makeText(LoginActivity.this, "设置失败，请链接网络", Toast.LENGTH_SHORT).show();
			return;
		}

		//IP地址校验
		String sServerIp = etIP.getText().toString().trim();
		if(sServerIp.length() == 0) {
			Toast.makeText(this, "请输入服务器IP地址", Toast.LENGTH_SHORT).show();
			return;
		}

		if (!isIpv4(sServerIp)){
			Toast.makeText(this, "请输入合法的IP地址", Toast.LENGTH_SHORT).show();
			return;
		}

		//房间ID校验
		final String sRoomId = etRoom.getText().toString().trim();
		if(sRoomId.length() == 0) {
			Toast.makeText(this, "请输入房间ID", Toast.LENGTH_SHORT).show();
			return;
		}
		if (!TextUtils.isDigitsOnly(sRoomId)){
			Toast.makeText(this, "请输入正确的房间ID", Toast.LENGTH_SHORT).show();
			return;
		}

		String sSendPosition = etSendPosition.getText().toString().trim();
		if(sSendPosition.length() == 0) {
			Toast.makeText(this, "请输入发送位置", Toast.LENGTH_SHORT).show();
			return;
		}
		if (!TextUtils.isDigitsOnly(sSendPosition)){
			Toast.makeText(this, "请输入正确的发送位置", Toast.LENGTH_SHORT).show();
			return;
		}

		try {
			roomId = Integer.parseInt(sRoomId);
			sendPostison = Integer.parseInt(sSendPosition);
		} catch (Exception e) {
			Toast.makeText(this, "请输入正确的房间ID、位置信息", Toast.LENGTH_SHORT).show();
			return;
		}

		//DEMO使用随机生成的用户ID
		userId = 100000 + (int)(Math.random() * (999999 - 100000));
		//DEMO使用固定的1号域
		domainId = 1;

		sharedPreferences.edit()
				.putString(UserSettingKey.ServerIpKey, sServerIp)
				.putInt(UserSettingKey.RoomIdKey, roomId)
				.putInt(UserSettingKey.UserIdKey, userId)
				.putInt(UserSettingKey.DomainIdKey, domainId)
				.putInt(UserSettingKey.SendPositionKey, sendPostison)
				.commit();

		Intent intent = new Intent(LoginActivity.this, MainActivity.class);
		startActivity(intent);

	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {  
			case KeyEvent.KEYCODE_BACK:
			Log.i(TAG,"KEYCODE_BACK");
			moveTaskToBack(true);
			return true;  
		}  
	
		return super.onKeyDown(keyCode, event);
	}


	public static boolean isIpv4(String ipv4){
		if(ipv4==null || ipv4.length()==0){
			return false;//字符串为空或者空串
		}
		String[] parts=ipv4.split("\\.");//因为java doc里已经说明, split的参数是reg, 即正则表达式, 如果用"|"分割, 则需使用"\\|"
		if(parts.length!=4){
			return false;//分割开的数组根本就不是4个数字
		}
		for(int i=0;i<parts.length;i++){
			try{
				int n=Integer.parseInt(parts[i]);
				if(n<0 || n>255){
					return false;//数字不在正确范围内
				}
			}catch (NumberFormatException e) {
				return false;//转换数字不正确
			}
		}
		return true;
	}
}
