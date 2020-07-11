package com.sd;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.util.Log;
import android.content.res.Configuration;

import com.seu.magicfilter.utils.MagicFilterType;



public class SDInterfaceCameraPublisher {
    private static final String TAG = "SDMedia";
	
	//音频相关
    private static AudioRecord mMic = null;
    private static AcousticEchoCanceler mAec = null;
    private static AutomaticGainControl mAgc = null;
    private Thread mAudioCapEncThr = null;
	//摄像头本地渲染
    private SDCameraView mCameraView;
	//音视频编码器
    private SDEncoder mEncoder = null;

    private boolean mSendAudioOnly = false;
    private boolean mInited = false;
	
	private static int mPortraitWidth = 360;
    private static int mPortraitHeight = 640;
    private static int mLandscapeWidth = 640;
    private static int mLandscapeHeight = 360;

	//单实例
    private static class SingletonHolder 
	{
        private static final SDInterfaceCameraPublisher INSTANCE = new SDInterfaceCameraPublisher();
    }

    public static final SDInterfaceCameraPublisher Inst() 
	{
        return SingletonHolder.INSTANCE;
    }


   
    public SDInterfaceCameraPublisher() 
	{
        mEncoder = new SDEncoder();
    }

	/*********************************************************************************/
    public void Init(SDCameraView view, boolean sendAudioOnly) 
	{
        mInited = true;
        mSendAudioOnly = sendAudioOnly;
        mCameraView = view;
        if (mSendAudioOnly == false)
		{
			//注册视频采集回调函数
            mCameraView.setPreviewCallback(new SDCameraView.PreviewCallback() {
                @Override
                public void onGetRgbaFrame(byte[] data, int width, int height) 
				{
                    if ((mSendAudioOnly == false) && (mEncoder != null)) 
					{
                        mEncoder.onGetRgbaFrame(data, width, height);
                    }
                }
            });
        }
        else 
		{
            Log.i(TAG, "Init SDInterfaceCameraPublisher with audio only mode!");
        }
    }

    public void Destroy() 
	{

    }

    private AudioRecord mfChooseAudioRecord() 
	{
		int pcmBufSize = AudioRecord.getMinBufferSize(SDEncoder.mSamperate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT) + 8191;
        pcmBufSize -= (pcmBufSize % 8192);
        
		AudioRecord mic = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, SDEncoder.mSamperate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT, pcmBufSize * 4);
        if (mic.getState() != AudioRecord.STATE_INITIALIZED) 
		{
            Log.w(TAG, "create audio capture with stereo failed, will try mono!");
            mic = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, SDEncoder.mSamperate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, pcmBufSize * 4);

            if (mic.getState() != AudioRecord.STATE_INITIALIZED) 
			{
                Log.e(TAG, "create audio capture with stereo or mono failed!");
                mic = null;
            } 
			else 
			{
                Log.i(TAG, "audio capture with mono 1 channel mode success!");
                SDEncoder.setAudioEncChannel(1);
            }
        } 
		else 
		{
            Log.i(TAG, "audio capture with stereo 2 channel mode success!");
            SDEncoder.setAudioEncChannel(2);
        }
        return mic;
    }


    //创建音频采集模块并开始采集编码线程
    private boolean mfStartAudio() 
	{
        mMic = mfChooseAudioRecord();
        if (mMic == null) 
		{
            Log.e(TAG, "startAudio failed create audio cap faield.");
            return false;
        }

		//若支持AEC则开启
        if (AcousticEchoCanceler.isAvailable()) 
		{
            mAec = AcousticEchoCanceler.create(mMic.getAudioSessionId());
            if (mAec != null) 
			{
                mAec.setEnabled(true);
                Log.i(TAG, "AEC is available!");
            }
            else 
			{
                Log.w(TAG, "AEC is unavailable!");
            }
        }
        else 
		{
            Log.w(TAG, "AEC is unavailable!");
        }

		//若支持AGC则开启
        if (AutomaticGainControl.isAvailable()) 
		{
            mAgc = AutomaticGainControl.create(mMic.getAudioSessionId());
            if (mAgc != null) 
			{
                mAgc.setEnabled(true);
                Log.i(TAG, "AGC is available!");
            }
            else 
			{
                Log.w(TAG, "AGC is unavailable!");
            }
        }
        else 
		{
            Log.w(TAG, "AGC is unavailable!");
        }

        //音频采集编码一体线程
        mAudioCapEncThr = new Thread(new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
                mMic.startRecording();
				byte[] pcmBuffer = new byte[4096];
				
                while (!Thread.interrupted()) 
				{
                    int size = mMic.read(pcmBuffer, 0, pcmBuffer.length);
                    if (size > 0) 
					{
                        mEncoder.onGetPcmFrame(pcmBuffer, size);
                    }
                }
            }
        });
        mAudioCapEncThr.start();
        return true;
    }

    //停止音频采集编码
    private void mfStopAudio() 
	{
		//先停止音频采集编码线程
        if (mAudioCapEncThr != null) 
		{
            mAudioCapEncThr.interrupt();
            try 
			{
                mAudioCapEncThr.join();
            } 
			catch (InterruptedException e) 
			{
                mAudioCapEncThr.interrupt();
            }
            mAudioCapEncThr = null;
        }

        if (mMic != null) 
		{
            mMic.setRecordPositionUpdateListener(null);
            mMic.stop();
            mMic.release();
            mMic = null;
        }

        if (mAec != null) 
		{
            mAec.setEnabled(false);
            mAec.release();
            mAec = null;
        }

        if (mAgc != null) 
		{
            mAgc.setEnabled(false);
            mAgc.release();
            mAgc = null;
        }
    }

	//开始发布
    public boolean startPublish() 
	{
        if (mInited == false) 
		{
            Log.e(TAG, "startPublish failed should Init first.");
            return false;
        }

        //启动音频采集编码(一体线程)，同时将获得的音频采集参数（声道数）设置给编码器
        if (!mfStartAudio()) 
		{
            return false;
        }

        if (mSendAudioOnly == false)
        {
            //启动视频编码线程
            mCameraView.enableEncoding();
            //开始视频采集线程
            if (!mCameraView.startCamera())
            {
                Log.e(TAG, "startPublish failed. camera start failed!");
                mfStopAudio();
                return false;
            }

        }

        //启动编码器，在未启动前，采集送入的数据将被丢弃
        boolean bRet = mEncoder.start();
        if (bRet == false)
        {
            Log.e(TAG, "startPublish failed. encode start failed!");
            mfStopAudio();
            if (mSendAudioOnly == false)
            {
                mCameraView.stopCamera();
            }
        }
        return bRet;
    }

	//停止发布
    public void stopPublish() 
	{
        if (mInited == false) 
		{
            Log.e(TAG, "stopPublish failed should Init first.");
            return;
        }

        mfStopAudio();

        if (mSendAudioOnly == false)
        {
            mCameraView.stopCamera();
        }

        mEncoder.stop();
    }

	//使用软编码
    public void switchToSoftEncoder() 
	{
        mEncoder.switchToSoftEncoder();
    }
	
	//使用硬编码
    public void switchToHardEncoder() 
	{
        mEncoder.switchToHardEncoder();
    }

	//获得摄像头ID
    public int getCamraId() 
	{
        if (mSendAudioOnly == true)
        {
            return 0;
        }
        return mCameraView.getCameraId();
    }

    //指定期望的采集宽高
    public boolean setPreviewResolution(int width, int height) 
	{
        if (mInited == false) 
		{
            Log.e(TAG, "setPreviewResolution failed should Init first.");
            return false;
        }

        if (mSendAudioOnly == true)
        {
            Log.e(TAG, "setPreviewResolution failed, current state is audio only mode.");
            return false;
        }
        //请求设置的采集宽高 不一定 等于实际支持和使用的采集宽高
        int resolution[] = mCameraView.setPreviewResolution(width, height);
        return true;
    }

    //指定编码宽高
    public boolean setOutputResolution(int width, int height) 
	{
        if (mInited == false) 
		{
            Log.e(TAG, "setOutputResolution failed should Init first.");
            return false;
        }

        if (width <= height) 
		{
			mPortraitWidth = width;
			mPortraitHeight = height;
			mLandscapeWidth = height;
			mLandscapeHeight = width;
        }
		else 
		{
			mLandscapeWidth = width;
			mLandscapeHeight = height;
			mPortraitWidth = height;
			mPortraitHeight = width;
        }
        
		mEncoder.setOutputResolution(width, height);
        return true;
    }

    //当屏幕发生变化时为采集和编码提供屏幕横竖信息
    public boolean setScreenOrientation(int orientation) 
	{
        if (mInited == false) 
		{
            Log.e(TAG, "setScreenOrientation failed should Init first.");
            return false;
        }

        if (mSendAudioOnly == true)
        {
            return false;
        }
		
		//修改采集分辨率
        mCameraView.setPreviewOrientation(orientation);
		
		//修改编码分辨率
        if (orientation == Configuration.ORIENTATION_PORTRAIT) 
		{
            Log.i(TAG, "set enc screen orientation portrait.");
			mEncoder.setOutputResolution(mPortraitWidth, mPortraitHeight);
        }
		else if (orientation == Configuration.ORIENTATION_LANDSCAPE) 
		{
            Log.i(TAG, "set enc screen orientation landscape.");
			mEncoder.setOutputResolution(mLandscapeWidth, mLandscapeHeight);
        }
        return true;
    }

    //设置视频编码参数
    public boolean setVideoEncParams(int nBitrate, int nVFPS) 
	{
        if (mInited == false) 
		{
            Log.e(TAG, "setVideoEncParams failed should Init first.");
            return false;
        }
		
        if (mSendAudioOnly == true)
        {
            return false;
        }
		
        mEncoder.setVideoEncParams(nBitrate, nVFPS);
        return true;
    }

    //切换视频滤镜
    public boolean switchCameraFilter(MagicFilterType type) 
	{
        if (mInited == false) 
		{
            Log.e(TAG, "switchCameraFilter failed should Init first.");
            return false;
        }

        if (mSendAudioOnly == true)
        {
            return false;
        }
        return mCameraView.setFilter(type);
    }

    //切换摄像头
    public boolean switchCameraFace(int id) 
	{

        if (mInited == false) 
		{
            Log.e(TAG, "switchCameraFace failed should Init first.");
            return false;
        }

        if (mSendAudioOnly == true)
        {
            return false;
        }
        mCameraView.stopCamera();
        mCameraView.setCameraId(id);
        mCameraView.enableEncoding();
        boolean bRet = mCameraView.startCamera();
        if (bRet == false)
        {
            Log.e(TAG, "switchCameraFace failed. camera start failed!");
            return false;
        }
        return true;
    }

	//设置码流发送回调
    public void setSendHandler(SDPublishHandler handler) 
	{
        mEncoder.setSendHandler(handler);
    }

}
