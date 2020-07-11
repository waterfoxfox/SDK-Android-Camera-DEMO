package com.mediapro.demo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.support.v7.app.AppCompatActivity;
import java.util.List;

import com.sd.Constant;
import com.sd.Constant.LogLevel;
import com.sd.Constant.SystemStatusType;

import com.sd.SDPublishHandler;
import com.sd.SDCameraView;
import com.sd.SDInterfaceCameraPublisher;
import com.sd.SDInterface;

import com.seu.magicfilter.utils.MagicFilterType;



public class MainActivity extends AppCompatActivity implements SDPublishHandler.SendListener {

    private static final String TAG = "SDMedia";

    private Button mBtnPublish = null;
    private Button mBtnSwitchCamera = null;
    private Button mBtnSwitchEncoder = null;

    //采集窗口
    private SDCameraView mSurfaceViewCamera = null;
    //主接口类
    private SDInterface mInterface = null;

    //用户设置相关
    private SharedPreferences mSharedPreferences;
    private UserSetting mUserSet;

    //是否开始发布音视频流
    private boolean mPublishStart = false;
    //是否已登录服务器
    private boolean mLoginSuccess = false;

    // 相机采集分辨率
    private int mCameraCapWidth = 640;
    private int mCameraCapHeight = 480;

    // 编码分辨率
    private int mEncodeWith = 640;
    private int mEncodeHeight = 480;
    //编码码率bps
    private int mBiterate = 400*1000;

    //日志文件存放路径
    private String mLogfileDir = "/sdcard/mediapro/";

    //来自底层消息的处理
    private final Handler mHandler = new Handler(){

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            switch (msg.what)
            {
                case SystemStatusType.SYS_NOTIFY_EXIT_KICKED:
                    //账号在其他位置登录，停止音视频发送，做下线处理
                    stopPublishAndPlay();
                    offLineProcess();
                    Toast.makeText(MainActivity.this, "账号在其他位置登录，与服务器断开连接", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "user with the same id login on other device");
                    break;
                case SystemStatusType.SYS_NOTIFY_RECON_START:
                    Toast.makeText(MainActivity.this, "网络超时，开始重连服务器...", Toast.LENGTH_SHORT).show();
                    Log.w(TAG, "start reconnect server....");
                    break;
                case SystemStatusType.SYS_NOTIFY_RECON_SUCCESS:
                    Toast.makeText(MainActivity.this, "连服务器成功", Toast.LENGTH_SHORT).show();
                    Log.w(TAG, "reconnect server success");
                    break;
                case SystemStatusType.SYS_NOTIFY_ONPOSITION:
                    long uid_on = (long)msg.arg1;
                    int on_position = msg.arg2;
                    Toast.makeText(MainActivity.this, uid_on + " 加入房间，位置：" + on_position, Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "user with id:" + uid_on + " onposition to:" + on_position);
                    break;
                case SystemStatusType.SYS_NOTIFY_OFFPOSITION:
                    long uid_off = (long)msg.arg1;
                    int off_position = msg.arg2;
                    Toast.makeText(MainActivity.this, uid_off + " 离开房间，位置：" + off_position, Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "user with id:" + uid_off + " offposition from:" + off_position);
                    break;
                default:
                    break;
            }
        }

    };

    //设置页面变更设置后
    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            Toast.makeText(getApplicationContext(), "设置变更在重启后生效", Toast.LENGTH_LONG).show();
        }
    };



    private void initView()
    {
        mBtnPublish = (Button) findViewById(R.id.publish);
        mBtnSwitchCamera = (Button) findViewById(R.id.swCam);
        mBtnSwitchEncoder = (Button) findViewById(R.id.swEnc);
        mSurfaceViewCamera = (SDCameraView)findViewById(R.id.sv_camera);

        mBtnPublish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mBtnPublish.getText().toString().contentEquals("发送"))
                {
                    startPublishAndPlay();
                }
                else if (mBtnPublish.getText().toString().contentEquals("停止"))
                {
                    stopPublishAndPlay();
                }
            }
        });

        mBtnSwitchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                if (mLoginSuccess == true)
                {
                    SDInterfaceCameraPublisher.Inst().switchCameraFace((SDInterfaceCameraPublisher.Inst().getCamraId() + 1) % Camera.getNumberOfCameras());
                }
            }
        });

        mBtnSwitchEncoder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mBtnSwitchEncoder.getText().toString().contentEquals("硬编码")) {
                    mBtnSwitchEncoder.setText("软编码");
                    SDInterfaceCameraPublisher.Inst().switchToSoftEncoder();
                } else if (mBtnSwitchEncoder.getText().toString().contentEquals("软编码")) {
                    mBtnSwitchEncoder.setText("硬编码");
                    SDInterfaceCameraPublisher.Inst().switchToHardEncoder();
                }
            }
        });
    }

    //初始化基础API、推送API
    private void initAvResource()
    {
        //读取配置参数
        mSharedPreferences = getSharedPreferences("UserInfo", 0);
        
        mUserSet = new UserSetting();
        mUserSet.UserId = mSharedPreferences.getInt(UserSettingKey.UserIdKey, 136136);
        mUserSet.ServerIp = mSharedPreferences.getString(UserSettingKey.ServerIpKey, "123.57.145.98");
        mUserSet.DomainId = mSharedPreferences.getInt(UserSettingKey.DomainIdKey, 1);
        mUserSet.RoomId = mSharedPreferences.getInt(UserSettingKey.RoomIdKey, 1);
        mUserSet.VFPS = mSharedPreferences.getInt(UserSettingKey.VFPSKey, 30);
        mUserSet.Bitrate = mSharedPreferences.getString(UserSettingKey.BitrateKey, "600kbps");
        mUserSet.Resolution = mSharedPreferences.getString(UserSettingKey.ResolutionKey, "640*480");
        mUserSet.FEC = mSharedPreferences.getInt(UserSettingKey.FECKey, 30);
        mUserSet.EnableNack = mSharedPreferences.getBoolean(UserSettingKey.EnableNackKey, true);
        mUserSet.SendPosition = mSharedPreferences.getInt(UserSettingKey.SendPositionKey, -1);

        String[] array = mUserSet.Resolution.split("\\*");
        if (array.length > 1)
        {
            mEncodeWith =  Integer.parseInt(array[0]);
            mEncodeHeight = Integer.parseInt(array[1]);
        }

        String[] arrBitrate = mUserSet.Bitrate.split("kbps");
        if (arrBitrate.length > 0)
        {
            mBiterate = Integer.parseInt(arrBitrate[0]) * 1000;
        }

        mInterface = new SDInterface(mHandler);
        // 初始化主SDK，指定服务器IP地址、本地客户端输出日志文件级别和存放路径
        int ret = mInterface.SDsysinit(mUserSet.ServerIp, mLogfileDir, LogLevel.LOG_LEVEL_INFO);
        if(0 != ret)
        {
            Log.e(TAG, "SDsysinit failed return:" + ret);
            Toast.makeText(this, "初始化音视频资源返回错误编码:" + ret, Toast.LENGTH_LONG).show();
        }
        // 初始化推送SDK
        SDInterfaceCameraPublisher.Inst().Init(mSurfaceViewCamera, false);
        SDInterfaceCameraPublisher.Inst().setSendHandler(new SDPublishHandler(this));
    }
    
    
    //反初始化基础API、推送API
    private void uninitAvResource()
    {
        SDInterfaceCameraPublisher.Inst().Destroy();
        mInterface.SDsysexit();
    }
    
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        //关闭屏幕旋转
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);

        //初始化相关资源
        initView();

        initAvResource();

        //登录服务器
        onLineProcess();

        //开始发布
        startPublishAndPlay();

        IntentFilter filter = new IntentFilter(UserSettingActivity.action);
        registerReceiver(broadcastReceiver, filter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
            // automatically handle clicks on the Home/Up button, so long
            // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_userSettings)
        {
            // popu user serttig activity
            Intent intent = new Intent(MainActivity.this, UserSettingActivity.class);
            startActivity(intent);
        }
        else {
            switch (id) {
                case R.id.cool_filter:
                    SDInterfaceCameraPublisher.Inst().switchCameraFilter(MagicFilterType.COOL);
                    break;
                case R.id.beauty_filter:
                    SDInterfaceCameraPublisher.Inst().switchCameraFilter(MagicFilterType.BEAUTY);
                    break;
                case R.id.romance_filter:
                    SDInterfaceCameraPublisher.Inst().switchCameraFilter(MagicFilterType.ROMANCE);
                    break;
                case R.id.sunrise_filter:
                    SDInterfaceCameraPublisher.Inst().switchCameraFilter(MagicFilterType.SUNRISE);
                    break;
                case R.id.sunset_filter:
                    SDInterfaceCameraPublisher.Inst().switchCameraFilter(MagicFilterType.SUNSET);
                    break;
                case R.id.warm_filter:
                    SDInterfaceCameraPublisher.Inst().switchCameraFilter(MagicFilterType.WARM);
                    break;
                case R.id.original_filter:
                default:
                    SDInterfaceCameraPublisher.Inst().switchCameraFilter(MagicFilterType.NONE);
                    break;
            }
            setTitle(item.getTitle());
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        final Button btn = (Button) findViewById(R.id.publish);
        btn.setEnabled(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(broadcastReceiver);

        //停止发布
        stopPublishAndPlay();

        //下线服务器
        offLineProcess();

        //回收资源
        uninitAvResource();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        SDInterfaceCameraPublisher.Inst().stopPublish();
        SDInterfaceCameraPublisher.Inst().setScreenOrientation(newConfig.orientation);
        if (mBtnPublish.getText().toString().contentEquals("停止"))
        {
            SDInterfaceCameraPublisher.Inst().startPublish();
        }

    }

    //获得摄像头支持的宽高
    public List<Camera.Size> getCameraParameters()
    {
        Camera camera = Camera.open();
        Camera.Parameters parameters = camera.getParameters();
        List<Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
        camera.release();
        return  supportedPreviewSizes;
    }

    //开始发布
    private void startPublishAndPlay()
    {
        if (mLoginSuccess == true)
        {
            //设置采集分辨率
            try {
                //优先使用用户指定的摄像头分辨率，若无符合指定项则使用第一项
                boolean bHasChooseCameraSize = false;
                List<Camera.Size> listCameraParameters = getCameraParameters();
                for (int i = 0; i < listCameraParameters.size(); i++)
                {
                    Camera.Size size= listCameraParameters.get(i);
                    if (size.width == mCameraCapWidth && size.height == mCameraCapHeight)
                    {
                        bHasChooseCameraSize = true;
                        break;
                    }
                }

                if (bHasChooseCameraSize)
                {
                    SDInterfaceCameraPublisher.Inst().setPreviewResolution(mCameraCapWidth, mCameraCapHeight);
                    Log.i(TAG, "setPreviewResolution Width = " + mCameraCapWidth + " Height = " + mCameraCapHeight);
                }
                else {
                    SDInterfaceCameraPublisher.Inst().setPreviewResolution(listCameraParameters.get(0).width, listCameraParameters.get(0).height);
                    Log.i(TAG, "setPreviewResolution Width = " + mCameraCapWidth + " Height = " + mCameraCapHeight);
                }
            }
            catch (Exception e)
            {
                Toast.makeText(this, "打开摄像头失败，请检查相关权限是否开启", Toast.LENGTH_LONG).show();
                Log.e(TAG, "open camera failed! should check app privilege for camera");
                return;
            }

            //设置编码发送分辨率
            SDInterfaceCameraPublisher.Inst().setOutputResolution(mEncodeWith, mEncodeHeight);

            //设置编码码率和帧率
            SDInterfaceCameraPublisher.Inst().setVideoEncParams(mBiterate, mUserSet.VFPS);

            Log.i(TAG, "setVideoEncParams Bitrate:" + mBiterate + " Framerate:" + mUserSet.VFPS + " Width:" + mEncodeWith + " Height:" + mEncodeHeight);

            //设置硬编码或软编码
            if (mBtnSwitchEncoder.getText().toString().contentEquals("硬编码"))
            {
                Toast.makeText(getApplicationContext(), "使用硬编码", Toast.LENGTH_SHORT).show();
                SDInterfaceCameraPublisher.Inst().switchToHardEncoder();
            }
            else {
                Toast.makeText(getApplicationContext(), "使用软编码", Toast.LENGTH_SHORT).show();
                SDInterfaceCameraPublisher.Inst().switchToSoftEncoder();
            }

            //开始推送
            boolean bRet = SDInterfaceCameraPublisher.Inst().startPublish();
            if (bRet == false)
            {
                Toast.makeText(getApplicationContext(), "摄像头or麦克风设备打开失败", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "startPublish failed!");
                SDInterfaceCameraPublisher.Inst().stopPublish();
                mPublishStart = false;
            }
            else
            {
                mPublishStart = true;

                mBtnPublish.setText("停止");
                mBtnSwitchEncoder.setEnabled(false);
            }
        }
    }

    //停止发布
    private void stopPublishAndPlay()
    {
        if (mPublishStart == true)
        {
            //停止推送
            SDInterfaceCameraPublisher.Inst().stopPublish();
            mPublishStart = false;

            mBtnPublish.setText("发送");
            mBtnSwitchEncoder.setEnabled(true);
        }
    }

    //登录服务器
    private int onLineProcess()
    {

        //根据码率选择合适的FEC参数GROUP
        int nGroupSize = 16;
        if (mBiterate >= 1500*1000)
        {
            nGroupSize = 28;
        }		
        else if (mBiterate >= 900*1000)
        {
            nGroupSize = 28;
        }
        else if (mBiterate >= 600*1000)
        {
            nGroupSize = 22;
        }

        //设置传输参数，未调用则使用默认值
        mInterface.SDSetTransParams(mUserSet.FEC, nGroupSize, mUserSet.EnableNack == true ? 1:0, 200);
        Log.i(TAG, "SDSetTransParams with Fec redun:" + mUserSet.FEC + " Groupsize:" + nGroupSize);


        //本DEMO仅发送 不接收
        mInterface.SDSetAvDownMasks(0x0, 0x0);

        //登录服务器
        int ret = mInterface.SDOnlineUser(mUserSet.RoomId, mUserSet.UserId, Constant.UserType.USER_TYPE_AV_SEND_ONLY, mUserSet.DomainId);
        Log.i(TAG, "SDOnlineUser return:" + ret);
        if (ret != 0)
        {
            Toast.makeText(this, "登录服务器失败:" + ret, Toast.LENGTH_LONG).show();
            Log.e(TAG, "SDOnlineUser failed");
            return ret;
        }
        else
        {

            if (mUserSet.SendPosition != -1)
            {
                //请求向指定位置发送音视频
                byte byPosition = (byte) mUserSet.SendPosition;
                Log.i(TAG, "SDOnPosition to:" + byPosition);
                ret = mInterface.SDOnPosition(byPosition);
                if (ret != 0)
                {
                    mInterface.SDOfflineUser();
                    Toast.makeText(this, "请求发送音视频失败:" + ret, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "SDOnPosition failed to " + byPosition);
                    return ret;
                }
            }
            else {
                //255表示由服务器分配可用的位置
                byte byPosition = (byte) 255;
                Log.i(TAG, "SDOnPosition to:" + byPosition);
                ret = mInterface.SDOnPosition(byPosition);
                if (ret != 0)
                {
                    mInterface.SDOfflineUser();
                    Toast.makeText(this, "请求发送音视频失败:" + ret, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "SDOnPosition failed to " + byPosition);
                    return ret;
                }
            }
        }

        mLoginSuccess = true;

        return ret;
    }

    //下线服务器
    protected void offLineProcess()
    {
        mLoginSuccess = false;
        mInterface.SDOfflineUser();
    }

    // Implementation of SDSendHandler
    @Override
    public void onSendVideoStreaming(byte[] buffer, int size) {
        mInterface.SDSendVideoStreamData(buffer, size, 0);
    }

    @Override
    public void onSendAudioStreaming(byte[] buffer, int size) {
        mInterface.SDSendAudioStreamData(buffer, size, 0);
    }

}
