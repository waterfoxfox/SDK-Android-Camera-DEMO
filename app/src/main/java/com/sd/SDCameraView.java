package com.sd;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.AttributeSet;
import android.util.Log;

import com.seu.magicfilter.base.gpuimage.GPUImageFilter;
import com.seu.magicfilter.utils.MagicFilterFactory;
import com.seu.magicfilter.utils.MagicFilterType;
import com.seu.magicfilter.utils.OpenGLUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


public class SDCameraView extends GLSurfaceView implements GLSurfaceView.Renderer {
    private static final String TAG = "SDMedia";
    private GPUImageFilter magicFilter;
    private SurfaceTexture surfaceTexture;
    private int mOESTextureId = OpenGLUtils.NO_TEXTURE;
    private int mSurfaceWidth;
    private int mSurfaceHeight;
    private int mPreviewWidth;
    private int mPreviewHeight;
    private boolean mIsEncoding;
    private boolean mIsTorchOn = false;
    private float mInputAspectRatio;
    private float mOutputAspectRatio;
    private float[] mProjectionMatrix = new float[16];
    private float[] mSurfaceMatrix = new float[16];
    private float[] mTransformMatrix = new float[16];

    private Camera mCamera;
    private ByteBuffer mGLPreviewBuffer;
    private int mCamId = -1;
    private int mPreviewRotation = 90;
    private int mPreviewOrientation = Configuration.ORIENTATION_PORTRAIT;
    private boolean mFrameDropMode = false;
    private int mFrameInterval = 1;
    private long mFrameCount = 0;
    private Thread mEncodeThr;
    private final Object writeLock = new Object();
    private ConcurrentLinkedQueue<IntBuffer> mGLIntBufferCache = new ConcurrentLinkedQueue<>();
    private PreviewCallback mPrevCb = null;

    public SDCameraView(Context context) 
	{
        this(context, null);
    }

    public SDCameraView(Context context, AttributeSet attrs) 
	{
        super(context, attrs);

        setEGLContextClientVersion(2);
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    //*****************GLSurfaceView.Renderer 绘制控制回调函数**********************
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) 
	{
        GLES20.glDisable(GL10.GL_DITHER);
        GLES20.glClearColor(0, 0, 0, 0);

        magicFilter = new GPUImageFilter(MagicFilterType.NONE);
        magicFilter.init(getContext().getApplicationContext());
        magicFilter.onInputSizeChanged(mPreviewWidth, mPreviewHeight);

        mOESTextureId = OpenGLUtils.getExternalOESTextureID();
        surfaceTexture = new SurfaceTexture(mOESTextureId);
        surfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                requestRender();
            }
        });

        // For camera preview on activity creation
        if (mCamera != null) {
            try {
                mCamera.setPreviewTexture(surfaceTexture);
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) 
	{
        GLES20.glViewport(0, 0, width, height);
        mSurfaceWidth = width;
        mSurfaceHeight = height;
        magicFilter.onDisplaySizeChanged(width, height);

        mOutputAspectRatio = width > height ? (float) width / height : (float) height / width;
        float aspectRatio = mOutputAspectRatio / mInputAspectRatio;
        if (width > height) 
		{
            Matrix.orthoM(mProjectionMatrix, 0, -1.0f, 1.0f, -aspectRatio, aspectRatio, -1.0f, 1.0f);
        } 
		else 
		{
            Matrix.orthoM(mProjectionMatrix, 0, -aspectRatio, aspectRatio, -1.0f, 1.0f, -1.0f, 1.0f);
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) 
	{
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        surfaceTexture.updateTexImage();

        surfaceTexture.getTransformMatrix(mSurfaceMatrix);
        Matrix.multiplyMM(mTransformMatrix, 0, mSurfaceMatrix, 0, mProjectionMatrix, 0);
        magicFilter.setTextureTransformMatrix(mTransformMatrix);
        magicFilter.onDrawFrame(mOESTextureId);

        if (mIsEncoding) 
		{
            mGLIntBufferCache.add(magicFilter.getGLFboBuffer());
            synchronized (writeLock) 
			{
                writeLock.notifyAll();
            }
        }
    }
	
    private void deleteTextures() {
        if (mOESTextureId != OpenGLUtils.NO_TEXTURE) {
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    GLES20.glDeleteTextures(1, new int[]{ mOESTextureId }, 0);
                    mOESTextureId = OpenGLUtils.NO_TEXTURE;
                }
            });
        }
    }
	
	
    //**************************对外接口***********************************
    //指定采集回调函数
    public void setPreviewCallback(PreviewCallback cb) 
	{
        mPrevCb = cb;
    }

    //请求按指定宽高采集，实际宽高可能不等于请求宽高（返回值）
    public int[] setPreviewResolution(int width, int height) 
	{
        getHolder().setFixedSize(width, height);

        mCamera = mfOpenCamera();
		
        mPreviewWidth = width;
        mPreviewHeight = height;
		
		//若摄像头支持指定的宽高，则使用指定宽高，否则使用宽高比最为接近的分辨率
        Camera.Size rs = mfAdaptPreviewResolution(mCamera.new Size(width, height));
        if (rs != null) 
		{
            mPreviewWidth = rs.width;
            mPreviewHeight = rs.height;
        }

        Log.i(TAG, "Camera setPreviewResolution with w:" + width + " h:" + height + " Finally use w:" + mPreviewWidth + " h:" + mPreviewHeight);

        mCamera.getParameters().setPreviewSize(mPreviewWidth, mPreviewHeight);
		
        //用于存放采集的一帧RGBA数据的临时区域
        mGLPreviewBuffer = ByteBuffer.allocateDirect(mPreviewWidth * mPreviewHeight * 4);
        mInputAspectRatio = mPreviewWidth > mPreviewHeight ?
							(float) mPreviewWidth / mPreviewHeight : (float) mPreviewHeight / mPreviewWidth;

        return new int[] { mPreviewWidth, mPreviewHeight };
    }

    //切换滤镜
    public boolean setFilter(final MagicFilterType type) 
	{
        if (mCamera == null) 
		{
            Log.e(TAG, "Camera setFilter failed. create camera first");
            return false;
        }

        queueEvent(new Runnable() {
            @Override
            public void run() 
			{
                if (magicFilter != null) 
				{
                    magicFilter.destroy();
                }
                magicFilter = MagicFilterFactory.initFilters(type);
                if (magicFilter != null) 
				{
                    magicFilter.init(getContext().getApplicationContext());
                    magicFilter.onInputSizeChanged(mPreviewWidth, mPreviewHeight);
                    magicFilter.onDisplaySizeChanged(mSurfaceWidth, mSurfaceHeight);
                }
            }
        });
        requestRender();
        return true;
    }



    //切换摄像头时用于指定新的摄像头ID
    public void setCameraId(int id) 
	{
        mCamId = id;
        setPreviewOrientation(mPreviewOrientation);
    }
	
	//获取当前摄像头ID
    public int getCameraId() 
	{
        return mCamId;
    }	

    //根据横竖屏设置视频旋转角度
    public void setPreviewOrientation(int orientation) 
	{
        mPreviewOrientation = orientation;
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(mCamId, info);
        if (orientation == Configuration.ORIENTATION_PORTRAIT) 
		{
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) 
			{
                mPreviewRotation = info.orientation % 360;
                mPreviewRotation = (360 - mPreviewRotation) % 360;  // compensate the mirror
                Log.i(TAG, "setPreviewOrientation with front Camera and ORIENTATION_PORTRAIT.");
            } 
			else 
			{
                mPreviewRotation = (info.orientation + 360) % 360;
                Log.i(TAG, "setPreviewOrientation with back Camera and ORIENTATION_PORTRAIT.");
            }
        } 
		else if (orientation == Configuration.ORIENTATION_LANDSCAPE) 
		{
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) 
			{
                mPreviewRotation = (info.orientation + 90) % 360;
                mPreviewRotation = (360 - mPreviewRotation) % 360;  // compensate the mirror
                Log.i(TAG, "setPreviewOrientation with front Camera and ORIENTATION_LANDSCAPE.");
            } 
			else 
			{
                mPreviewRotation = (info.orientation + 270) % 360;
                Log.i(TAG, "setPreviewOrientation with back Camera and ORIENTATION_LANDSCAPE.");
            }
        }
    }



    //启动编码线程
    public void enableEncoding() 
	{
        mFrameCount = 0;
        mEncodeThr = new Thread(new Runnable() {
            @Override
            public void run() 
			{
                while (!Thread.interrupted()) 
				{
                    while (!mGLIntBufferCache.isEmpty()) 
					{
                        IntBuffer picture = mGLIntBufferCache.poll();
                        if (picture != null) 
						{
                            mGLPreviewBuffer.asIntBuffer().put(picture.array());
                            if (mPrevCb != null)
							{
                                mFrameCount++;
                                //丢帧处理
                                if (mFrameDropMode == false) 
								{
                                    if ((mFrameCount % mFrameInterval) == 0) 
									{
                                        mPrevCb.onGetRgbaFrame(mGLPreviewBuffer.array(), mPreviewWidth, mPreviewHeight);
                                    }
                                } 
								else 
								{
                                    if ((mFrameCount % mFrameInterval) != 0) 
									{
                                        mPrevCb.onGetRgbaFrame(mGLPreviewBuffer.array(), mPreviewWidth, mPreviewHeight);
                                    }
                                }

                            }
                        }
                    }
                    // Waiting for next frame
                    synchronized (writeLock) 
					{
                        try 
						{
                            // isEmpty() may take some time, so we set timeout to detect next frame
                            writeLock.wait(500);
                        } 
						catch (InterruptedException ie) 
						{
                            mEncodeThr.interrupt();
                        }
                    }
                }
            }
        });
		
        mEncodeThr.start();
        mIsEncoding = true;
        Log.i(TAG, "Camera enableEncoding success.");
    }

    //停止编码线程
    public void disableEncoding() 
	{
        mIsEncoding = false;

        if (mEncodeThr != null) 
		{
            mEncodeThr.interrupt();
            try 
			{
                mEncodeThr.join();
            } 
			catch (InterruptedException e) 
			{
                e.printStackTrace();
                mEncodeThr.interrupt();
            }
            mEncodeThr = null;
        }
        Log.i(TAG, "Camera disableEncoding success.");
		
		mGLIntBufferCache.clear();
    }

	//启动采集
    public boolean startCamera() 
	{
        if (mCamera == null) 
		{
            mCamera = mfOpenCamera();
            if (mCamera == null) 
			{
                Log.e(TAG, "startCamera failed. open camera failed");
                return false;
            }
        }

        Camera.Parameters params = mCamera.getParameters();
        params.setPictureSize(mPreviewWidth, mPreviewHeight);
        params.setPreviewSize(mPreviewWidth, mPreviewHeight);
		
        //寻找支持的最接近外层编码要求的帧率
        int[] range = mfAdaptFpsRange(SDEncoder.getEncFrameRate(), params.getSupportedPreviewFpsRange());
        params.setPreviewFpsRange(range[0], range[1]);
        Log.i(TAG, "Camera request fps:" + SDEncoder.getEncFrameRate() + " Finally use fps:" + range[0] + "~" + range[1]);
		
        //若实际帧率超出期望帧率一定程度，则需要进行丢帧处理
        mfCalcFrameDropInterval(SDEncoder.getEncFrameRate(), range[0] / 1000);
        mFrameCount = 0;

        params.setPreviewFormat(ImageFormat.NV21);
        params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        params.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
        params.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);

        try 
		{
            //某些特定设备上设置自动对焦可能出现异常
            List<String> supportedFocusModes = params.getSupportedFocusModes();
            if (supportedFocusModes != null && !supportedFocusModes.isEmpty()) 
			{
                if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) 
				{
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                } 
				else if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) 
				{
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                    mCamera.autoFocus(null);
                } 
				else 
				{
                    params.setFocusMode(supportedFocusModes.get(0));
                }
            }
            Log.i(TAG, "Camera setFocusMode success.");
        } 
		catch (Exception e) 
		{
            //仅当做警告，不影响采集
            Log.w(TAG, "Camera setFocusMode failed.");
        }

        List<String> supportedFlashModes = params.getSupportedFlashModes();
        if (supportedFlashModes != null && !supportedFlashModes.isEmpty()) 
		{
            if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) 
			{
                if (mIsTorchOn) 
				{
                    params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                }
            } 
			else 
			{
                params.setFlashMode(supportedFlashModes.get(0));
            }
        }

        try 
		{
            mCamera.setParameters(params);
        } 
		catch (Exception e) 
		{
            Log.e(TAG, "Camera setParameters failed. may not support the request w:" + mPreviewWidth + " h:" + mPreviewHeight);
            e.printStackTrace();
            return false;
        }

        mCamera.setDisplayOrientation(mPreviewRotation);

        try 
		{
            mCamera.setPreviewTexture(surfaceTexture);
        } 
		catch (IOException e) 
		{
            Log.e(TAG, "Camera setPreviewTexture failed.");
            e.printStackTrace();
            return false;
        }
        mCamera.startPreview();

        return true;
    }

    public void stopCamera() 
	{
		//停止编码线程
        disableEncoding();

		//停止采集
        if (mCamera != null) 
		{
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    private Camera mfOpenCamera() 
	{
        Camera camera;
        if (mCamId < 0) 
		{
            Camera.CameraInfo info = new Camera.CameraInfo();
            int numCameras = Camera.getNumberOfCameras();
            int frontCamId = -1;
            int backCamId = -1;
            for (int i = 0; i < numCameras; i++) 
			{
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) 
				{
                    backCamId = i;
                } 
				else if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) 
				{
                    frontCamId = i;
                    break;
                }
            }
			
            if (frontCamId != -1) 
			{
                mCamId = frontCamId;
            }
			else if (backCamId != -1) 
			{
                mCamId = backCamId;
            } 
			else 
			{
				Log.e(TAG, "Cannot found front and back Camera");
                mCamId = 0;
            }
        }
        camera = Camera.open(mCamId);
        return camera;
    }

    //若摄像头支持指定的宽高，则使用指定宽高，否则使用宽高比最为接近的分辨率
    private Camera.Size mfAdaptPreviewResolution(Camera.Size resolution) 
	{
        float diff = 100f;
        float xdy = (float) resolution.width / (float) resolution.height;
        Camera.Size best = null;
        for (Camera.Size size : mCamera.getParameters().getSupportedPreviewSizes()) 
		{
            if (size.equals(resolution)) 
			{
                return size;
            }
			
            float tmp = Math.abs(((float) size.width / (float) size.height) - xdy);
            if (tmp < diff) 
			{
                diff = tmp;
                best = size;
            }
        }
        return best;
    }

    //若帧率支持，则直接使用该帧率，否则返回默认帧率
    private int[] mfAdaptFpsRange(int expectedFps, List<int[]> fpsRanges) 
	{
        expectedFps *= 1000;
        int[] closestRange = fpsRanges.get(0);
        int measure = Math.abs(closestRange[0] - expectedFps) + Math.abs(closestRange[1] - expectedFps);
        for (int[] range : fpsRanges) 
		{
            if (range[0] <= expectedFps && range[1] >= expectedFps) 
			{
                int curMeasure = Math.abs(range[0] - expectedFps) + Math.abs(range[1] - expectedFps);
                if (curMeasure < measure) 
				{
                    closestRange = range;
                    measure = curMeasure;
                }
            }
        }
        return closestRange;
    }

    //视情况决定是否进行丢帧处理
    private void mfCalcFrameDropInterval(int targetFps, int realFps) 
	{
        if (targetFps >= realFps) 
		{
            mFrameDropMode = false;
            mFrameInterval = 1;
        } 
		else 
		{
            double div = realFps / (double)targetFps;
            if (div >= 2.0) 
			{
                //隔几帧取一帧模式
                mFrameDropMode = false;
                mFrameInterval = (int)(div + 0.5);
            } 
			else 
			{
                //隔几帧丢一帧模式
                mFrameDropMode = true;
                mFrameInterval = (int)(realFps / (double)(realFps - targetFps) + 0.5);
            }
        }
    }

    public interface PreviewCallback 
	{
        void onGetRgbaFrame(byte[] data, int width, int height);
    }
}
