package com.sd;

import android.content.res.Configuration;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;


public class SDEncoder {
    private static final String TAG = "SDMedia";

    public static final String VCODEC = "video/avc";
    public static final String ACODEC = "audio/mp4a-latm";
    public static String X264PRESET = "veryfast";

    public static int mOutWidth = 1280;
    public static int mOutHeight = 720;

    public static int mVideoBitrate = 300*1000;
    public static int mFramerate = 30;
    public static int mIdrIntervalSec = 2;

    public static final int mSamperate = 44100;
    public static int mChannelnum = 2;
    public static final int mAacbitrate = 32000;

    //音频采样率对应的索引（ADTS头封装所需），采样率与索引的映射关系如下
    /*
    *             96000, 88200, 64000, 48000, 44100, 32000,
    *             24000, 22050, 16000, 12000, 11025, 8000, 7350
    *             索引依次从0递增，44100的索引为4
    * */
    public static int ASAMPLERATE_INDEX_ADTS = 4;
    //音频通道对应的索引（ADTS头封装所需），通道数与索引的映射关系如下
    /*
        0: Defined in AOT Specifc Config
        1: 1 channel: front-center
        2: 2 channels: front-left, front-right
        3: 3 channels: front-center, front-left, front-right
        4: 4 channels: front-center, front-left, front-right, back-center
        5: 5 channels: front-center, front-left, front-right, back-left, back-right
        6: 6 channels: front-center, front-left, front-right, back-left, back-right, LFE-channel
        7: 8 channels: front-center, front-left, front-right, side-left, side-right, back-left, back-right, LFE-channel
        8-15: Reserved
    */
    public static int ACHANNEL_INDEX_ADTS = 2;


    private MediaCodecInfo mMediaCodecInfo;
    private MediaCodec mVideoEncoder;
    private MediaCodec mAudioEncoder;
    private MediaCodec.BufferInfo mVideoBuffInfo = new MediaCodec.BufferInfo();
    private MediaCodec.BufferInfo mAudioBuffInfo = new MediaCodec.BufferInfo();

    //默认优先硬编码
    private boolean mRequestHwEncoder = true;
    private boolean mCanSoftEncode = false;
    private boolean mCanHardwareEncode = false;

    private long mPresentTimeUs;

    private int mVideoColorFormat = 0;


    private SDPublishHandler mSendHandler = null;
    private byte[] mSpsPps = null;
    private byte[] mFrameStreamBuff = null;

    private boolean mStarted = false;

    // Y, U (Cb) and V (Cr)
    // yuv420                     yuv yuv yuv yuv
    // yuv420p (planar)   yyyy*2 uu vv
    // yuv420sp(semi-planner)   yyyy*2 uv uv
    // I420 -> YUV420P   yyyy*2 uu vv
    // YV12 -> YUV420P   yyyy*2 vv uu
    // NV12 -> YUV420SP  yyyy*2 uv uv
    // NV21 -> YUV420SP  yyyy*2 vu vu
    // NV16 -> YUV422SP  yyyy uv uv
    // YUY2 -> YUV422SP  yuyv yuyv

    public SDEncoder() 
	{
    }
	
	//********************************************对外接口************************************************************/
	//码流输出接口
    public void setSendHandler(SDPublishHandler handler) 
	{
        mSendHandler = handler;
    }
	
    //请求切换到软编码
    public void switchToSoftEncoder() 
	{
        mRequestHwEncoder = false;
    }

    //请求切换到硬编码（内部视情况响应）
    public void switchToHardEncoder() 
	{
        mRequestHwEncoder = true;
    }


	//设置编码分辨率
    public void setOutputResolution(int width, int height) 
	{
        mOutWidth = width;
        mOutHeight = height;
        if (mOutWidth % 32 != 0 || mOutHeight % 32 != 0) 
		{
            Log.w(TAG, String.format("Enc width(%d) height(%d) is not 16x, will use software enc.", mOutWidth, mOutHeight));
            mCanHardwareEncode = false;
        }
    }

	//设置编码码率帧率
    public void setVideoEncParams(int nBitrate, int nFramerate) 
	{
        mVideoBitrate = nBitrate;
        mFramerate = nFramerate;
        X264PRESET = "veryfast";
    }

	//设置音频编码声道
    public static void setAudioEncChannel(int nChannelNum) 
	{
        mChannelnum = nChannelNum;
        if (mChannelnum == 1) 
		{
            ACHANNEL_INDEX_ADTS = 1;
        }
        else 
		{
            ACHANNEL_INDEX_ADTS = 2;
        }
    }

	//编码器是否可用
    public boolean isEnabled() 
	{
        return mCanHardwareEncode || mCanSoftEncode;
    }
	
	//获取当前配置帧率
    public static int getEncFrameRate() 
	{
        return mFramerate;
    }
	
    //创建所需音视频编解码器
    public boolean start() 
	{
        //码流临时存放区
        mFrameStreamBuff = new byte[mOutWidth * mOutHeight];
        //SPS PPS存放区
        mSpsPps = null;

        //初始化时间戳
        mPresentTimeUs = System.nanoTime() / 1000;

		//通过native接口设置软编码、缩放（软硬共用）、色度空间转换（软硬共用）相关参数
        setEncoderResolution(mOutWidth, mOutHeight, 1);
        setEncoderFps(mFramerate);
        setEncoderGop(mFramerate * mIdrIntervalSec);
        setEncoderBitrate(mVideoBitrate);
        setEncoderPreset(X264PRESET);

        //创建X264软编码器
        mCanSoftEncode = openSoftEncoder();
        if (!mCanSoftEncode) 
		{
            Log.e(TAG, "create software video enc failed.");
            return false;
        }

        //尝试创建H264硬编码器
        try {
            //硬编码对宽高有对齐要求
            if ((mOutWidth % 32 != 0 || mOutHeight % 32 != 0)) 
			{
                Log.w(TAG, String.format("Enc width(%d) height(%d) is not 16x, will use software enc.", mOutWidth, mOutHeight));
                mCanHardwareEncode = false;
                mVideoEncoder = null;
            }
            else
            {
                //是否有所需的H264硬编码器，目前使用类型而非名字创建硬编码
                mMediaCodecInfo = mfGetSupportHwVideoEncInfo(null);
                if (mMediaCodecInfo == null)
                {
                    Log.w(TAG, "hw enc is not support, cannot found h264 hw enc");
                    mCanHardwareEncode = false;
                    mVideoEncoder = null;
                }
                else
                {
                    //硬编码器的输入格式是否符合要求
                    if (mfIsHwVideoFormatSupport())
                    {
                        mVideoEncoder = MediaCodec.createByCodecName(mMediaCodecInfo.getName());
                        if (mVideoEncoder != null)
                        {
                            mCanHardwareEncode = true;
                        }
                        else
                        {
                            Log.w(TAG, "create video hw encoder failed. will use sw encode");
                            mCanHardwareEncode = false;
                        }
                    }
                    else
                    {
                        Log.w(TAG, "hw enc format is not support. will use sw encode");
                        mCanHardwareEncode = false;
                        mVideoEncoder = null;
                    }
                }
            }

        } catch (IOException e) {
            Log.w(TAG, "create video hw encoder failed. will use sw encode");
            mVideoEncoder = null;
            mCanHardwareEncode = false;
        }

        if (mCanHardwareEncode)
        {
            //配置硬编码器
            MediaFormat videoFormat = MediaFormat.createVideoFormat(VCODEC, mOutWidth, mOutHeight);
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mVideoColorFormat);
            videoFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, mVideoBitrate);
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mFramerate);
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mIdrIntervalSec);
            mVideoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        }

        //创建音频硬编码器，目前未加入音频软编码支持
        try {
            mAudioEncoder = MediaCodec.createEncoderByType(ACODEC);
            if (mAudioEncoder == null)
            {
                Log.e(TAG, "create audio hw encoder failed.");
                return false;
            }
        } catch (IOException e) {
            Log.e(TAG, "create audio hw encoder failed.");
            e.printStackTrace();
            return false;
        }
        //配置硬编码器
        MediaFormat audioFormat = MediaFormat.createAudioFormat(ACODEC, mSamperate, mChannelnum);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, mAacbitrate);
		audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        mAudioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        //启动相关编码器
        mAudioEncoder.start();
        if (mCanHardwareEncode)
        {
            Log.i(TAG, "start audio hw enc and video hw enc success, with enc width:" + mOutWidth + " height:" + mOutHeight);
            mVideoEncoder.start();
        } 
		else 
		{
            Log.i(TAG, "start audio hw enc and video sw enc success, with enc width:" + mOutWidth + " height:" + mOutHeight);
        }
        mStarted = true;
        return true;
    }

    public void stop() 
	{
        mStarted = false;
        if (mCanSoftEncode) 
		{
            closeSoftEncoder();
            mCanSoftEncode = false;
        }

        if (mAudioEncoder != null) 
		{
            Log.i(TAG, "stop audio hw encoder");
            mAudioEncoder.stop();
            mAudioEncoder.release();
            mAudioEncoder = null;
        }

        if (mVideoEncoder != null) 
		{
			Log.i(TAG, "stop video hw encoder");
			mVideoEncoder.stop();
			mVideoEncoder.release();
			mVideoEncoder = null;
        }
    }




    //软编码时，C层回调本函数输出视频码流
    private void onSoftEncodedData(byte[] es, long pts, boolean isKeyFrame) 
	{
        if (mSendHandler != null) 
		{
            mSendHandler.notifySendVideoStreaming(es, es.length);
        }
    }

    //输出音频码流
    private void onEncodedAacFrame(ByteBuffer es, MediaCodec.BufferInfo bi) 
	{

        if (mSendHandler != null) 
		{
            mSendHandler.notifySendAudioStreaming(es, bi.size);
        }
    }
	
    //JNI调用C层X264软编码处理
    private void mfSoftwareEncode(byte[] data, int width, int height, long pts) 
	{
        RGBASoftEncode(data, width, height, true, 180, pts);
    }	
	
    //硬编码处理
    private void mfHardwareEncode(byte[] yuv420, long pts) 
	{
        int pos = 0;

        try {
            ByteBuffer[] inputBuffers = mVideoEncoder.getInputBuffers();
            ByteBuffer[] outputBuffers = mVideoEncoder.getOutputBuffers();
            int inputBufferIndex = mVideoEncoder.dequeueInputBuffer(-1);
            if (inputBufferIndex >= 0)
            {
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                inputBuffer.put(yuv420);
                mVideoEncoder.queueInputBuffer(inputBufferIndex, 0, yuv420.length, pts, 0);
            }

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = mVideoEncoder.dequeueOutputBuffer(bufferInfo,0);

            while (outputBufferIndex >= 0)
            {
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                byte[] outData = new byte[bufferInfo.size];
                outputBuffer.get(outData);

                if (mSpsPps != null)
                {
                    System.arraycopy(outData, 0,  mFrameStreamBuff, pos, outData.length);
                    pos += outData.length;

                }
                else
                {
                    //初次保存SPS PPS
                    ByteBuffer spsPpsBuffer = ByteBuffer.wrap(outData);
                    if (spsPpsBuffer.getInt() == 0x00000001)
                    {
                        mSpsPps = new byte[outData.length];
                        System.arraycopy(outData, 0, mSpsPps, 0, outData.length);
                    }
                    else
                    {
						Log.w(TAG, "sps pps nalu invalid");
                        return ;
                    }
                }

                mVideoEncoder.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = mVideoEncoder.dequeueOutputBuffer(bufferInfo, 0);
            }

            //每个IDR帧前面都附带好SPS PPS
            if((mFrameStreamBuff[4] & 0x1f) == 0x5) //key frame
            {
				//yuv420空间用作临时缓存
                System.arraycopy(mFrameStreamBuff, 0,  yuv420, 0, pos);
                System.arraycopy(mSpsPps, 0,  mFrameStreamBuff, 0, mSpsPps.length);
                System.arraycopy(yuv420, 0,  mFrameStreamBuff, mSpsPps.length, pos);
                pos += mSpsPps.length;
            }

        } catch (Throwable t) {
            t.printStackTrace();
        }

        //输出视频码流
        if (mSendHandler != null && pos > 0) 
		{
            mSendHandler.notifySendVideoStreaming(mFrameStreamBuff, pos);
        }
    }



    //外部音频采集回调函数
    public void onGetPcmFrame(byte[] data, int size) 
	{
        //入口保护，还未启动前不响应外部请求
        if (mStarted == false) 
		{
            return;
        }
		
		//音频硬编码
        ByteBuffer[] inBuffers = mAudioEncoder.getInputBuffers();
        ByteBuffer[] outBuffers = mAudioEncoder.getOutputBuffers();

        int inBufferIndex = mAudioEncoder.dequeueInputBuffer(-1);
        if (inBufferIndex >= 0) 
		{
            ByteBuffer bb = inBuffers[inBufferIndex];
            bb.clear();
            bb.put(data, 0, size);
            long pts = System.nanoTime() / 1000 - mPresentTimeUs;
            mAudioEncoder.queueInputBuffer(inBufferIndex, 0, size, pts, 0);
        }

        for (; ; ) 
		{
            int outBufferIndex = mAudioEncoder.dequeueOutputBuffer(mAudioBuffInfo, 0);
            if (outBufferIndex >= 0) 
			{
                ByteBuffer bb = outBuffers[outBufferIndex];
                onEncodedAacFrame(bb, mAudioBuffInfo);
                mAudioEncoder.releaseOutputBuffer(outBufferIndex, false);
            } 
			else 
			{
                break;
            }
        }
    }

    //外部视频采集回调函数
    public void onGetRgbaFrame(byte[] data, int width, int height) 
	{
        //入口保护，还未启动前不响应外部请求
        if (mStarted == false) 
		{
            return;
        }
		
        //在创建软编码或硬编码器之后再响应外部编码请求
        long pts = System.nanoTime() / 1000 - mPresentTimeUs;
        if ((mRequestHwEncoder == true) && (mCanHardwareEncode == true))
        {
            byte[] processedData = mfRgbTransAndScale(data, width, height);
            if (processedData != null) 
			{
				//硬编码
                mfHardwareEncode(processedData, pts);
            } 
			else 
			{
				//色度空间不支持硬编码时，转向软编码
                mCanHardwareEncode = false;
            }
        }
        else if (mCanSoftEncode == true)
        {
			//软编码
            mfSoftwareEncode(data, width, height, pts);
        }
		else
		{
			Log.w(TAG, String.format("hw and sw encode are not usable. encode failed"));
		}
    }

	//通过C层完成颜色空间转换以及缩放到编码分辨率
    private byte[] mfRgbTransAndScale(byte[] data, int width, int height) 
	{
        switch (mVideoColorFormat) 
		{
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                return RGBAToI420(data, width, height, true, 180);
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                return RGBAToNV12(data, width, height, true, 180);
            default:
                Log.w(TAG, String.format("hw encode input color format:%d is not support, change to sw encode.", mVideoColorFormat));
                return null;
        }
    }


    //获得支持的H264类型硬编码器信息，若指定了名字则要求名字匹配
    private MediaCodecInfo mfGetSupportHwVideoEncInfo(String name) 
	{
        int nbCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < nbCodecs; i++) 
		{
            MediaCodecInfo mci = MediaCodecList.getCodecInfoAt(i);
            if (!mci.isEncoder()) 
			{
                continue;
            }

            String[] types = mci.getSupportedTypes();
            for (int j = 0; j < types.length; j++) 
			{
                if (types[j].equalsIgnoreCase(VCODEC)) 
				{
                    Log.i(TAG, String.format("mVideoEncoder %s types: %s", mci.getName(), types[j]));
                    if (name == null) 
					{
                        return mci;
                    }

                    if (mci.getName().contains(name)) 
					{
                        return mci;
                    }
                }
            }
        }
        return null;
    }

    //判断编码器是否支持指定的色度空间
    private boolean mfIsHwVideoFormatSupport() 
	{
        boolean bSupportFormat = false;
        int matchedColorFormat = 0;
        MediaCodecInfo.CodecCapabilities cc = mMediaCodecInfo.getCapabilitiesForType(VCODEC);
        for (int i = 0; i < cc.colorFormats.length; i++) 
		{
            int cf = cc.colorFormats[i];
            Log.i(TAG, String.format("mVideoEncoder %s supports color fomart 0x%x(%d)", mMediaCodecInfo.getName(), cf, cf));

            if (cf >= cc.COLOR_FormatYUV420Planar && cf <= cc.COLOR_FormatYUV420SemiPlanar) 
			{
                if (cf > matchedColorFormat) 
				{
                    matchedColorFormat = cf;
                    bSupportFormat = true;
                }
            }
        }

        for (int i = 0; i < cc.profileLevels.length; i++) 
		{
            MediaCodecInfo.CodecProfileLevel pl = cc.profileLevels[i];
            Log.i(TAG, String.format("mVideoEncoder %s support profile %d, level %d", mMediaCodecInfo.getName(), pl.profile, pl.level));
        }
        mVideoColorFormat = matchedColorFormat;
        return bSupportFormat;
    }

    private native void setEncoderResolution(int outWidth, int outHeight, int use_cut_keep_ratio);
    private native void setEncoderFps(int fps);
    private native void setEncoderGop(int gop);
    private native void setEncoderBitrate(int bitrate);
    private native void setEncoderPreset(String preset);
    private native byte[] RGBAToI420(byte[] frame, int width, int height, boolean flip, int rotate);
    private native byte[] RGBAToNV12(byte[] frame, int width, int height, boolean flip, int rotate);
    private native int RGBASoftEncode(byte[] frame, int width, int height, boolean flip, int rotate, long pts);
    private native boolean openSoftEncoder();
    private native void closeSoftEncoder();

    static {
        System.loadLibrary("TerminalSdkEnc");
    }
}
