/**
 * 客户端底层接口
 */
package com.sd;
import com.sd.Constant;
import com.sd.MediaTransStatis;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class SDInterface {

	static {
		try {
			System.loadLibrary("TerminalSdk");
		} catch (Exception e) {
			Log.e("SDMedia", "Can not load library libTerminalSdk.so");
			e.printStackTrace();
		}
	}

	private static final String TAG = "SDMedia";
	private Handler eventHandler = null;

	public SDInterface(Handler eventHandler) {
		super();
		this.eventHandler = eventHandler;
	}


	//***************************JNI方式主动调用的接口************************************//

	//初始化资源，整个系统中只需调用一次即可，成功返回0，失败返回负数
	public native int SDsysinit(String strServerIp, String strLogFileDir, byte byLogFileLevel);

	//资源的释放
	public native void SDsysexit();

	//登录接口，成功返回0，失败返回负数
	public native int SDOnlineUser(int nRoomId, int nUserId, byte byUserType, int nDomainId);

	//下线接口
	public native void SDOfflineUser();

	//请求上传AV到指定位置
	public native int SDOnPosition(byte byPosition);

	//请求从指定位置下来
	public native void SDOffPosition();

	//发送已编码的一帧视频码流，内部自带拆分功能，【需传入带起始码的码流】
	public native void SDSendVideoStreamData(byte[] byBuf, int nlen, int nPts);

	//发送已编码的一帧音频码流【需传入ADTS流】
	public native void SDSendAudioStreamData(byte[] byBuf, int nlen, int nPts);

	//设置传输参数，包括冗余度、Group大小、是否启用本端NACK以及本地接收缓存时间（毫秒）
	//当设置冗余度nRedunRatio为0时表示使用动态冗余度【请于Online接口前调用】
	public native void SDSetTransParams(int nRedunRatio, int nGroupSize, int nEnableNack, int nJitterBufTime);

	//使用掩码方式设置本客户端接收哪几路音视频【可Online接口之前或者之后调用】
	public native void SDSetAvDownMasks(int nAudioDownMask, int nVideoDownMask);

	//获取当前媒体传输状态
	public native MediaTransStatis SDGetMediaTransStatis(MediaTransStatis transStatis);

    
	//告知底层当前视频帧率信息，便于底层进行一些Smooth平滑发送处理【可Online接口之前或者之后调用】
	public native void SDSetVideoFrameRateForSmoother(int nFrameRate);
	
    //是否开启周期性获取房间在线用户列表,nCallbackIntervalSec为0表示不下发，非0表示请求的下发间隔秒数【请于Online接口前调用】
	public native void SDEnableUserListCallback(int nCallbackIntervalSec);
	

	//***************************JNI方式被动接口************************************//
	// 【注意事项】
	//	1、通知型回调函数中应尽快的退出，不进行耗时操作，不调用主动API接口。
	//  2、数据型回调函数中允许进行解码处理
	
	//被动接口：来自底层的状态变更反馈（比如启动重连、重连成功、账号被顶下去等）
	public void onSysStatus(int nUid, int nType) {

		Log.i(TAG, "onSysStatus() with type=" + nType);
		if (eventHandler != null) {
			Message statusMsg = eventHandler.obtainMessage();
			statusMsg.what = nType;
			statusMsg.arg1 = nUid;
			eventHandler.sendMessage(statusMsg);
		}
	}

	//被动接口：房间内其他客户端发布或停止发布通知
	public void onPositionStatus(int nUid, int nPosition, int nType) {
		
		Log.i(TAG, "onPositionStatus() with type=" + nType + " user=" + nUid + " position=" + nPosition);
		if(eventHandler != null){
			Message statusMsg = eventHandler.obtainMessage();
			statusMsg.what = nType;
			statusMsg.arg1 = nUid;
            statusMsg.arg2 = nPosition;
			eventHandler.sendMessage(statusMsg);			
		}
	}	
	
	//被动接口：收到服务器发来的当前房间内处于在线状态的用户ID列表
	public void onUserList(int nOnlineUserNum, int nOnlineUserIds[]) {
		Log.i(TAG, "user online:");
		for (int i = 0; i < nOnlineUserNum; i++) {
			long uid = (long)nOnlineUserIds[i];
			Log.i(TAG, "UID: " + uid );
		}

	}
	
	//被动接口：收到服务器发来的当前房间内处于位置上的用户ID列表、音视频状态
	public void onRoomInfo(int nOnpositionUserNum, int nOnpositionUserIds[], int nOnpositions[], int nAudioStatus[], int nVideoStatus[]) {
		Log.i(TAG, "************user onposition********:");
		for (int i = 0; i < nOnpositionUserNum; i++) {
            long uid = (long)nOnpositionUserIds[i];
            {
                Log.i(TAG, "UID: " + uid + "  Pos:" + nOnpositions[i] + "  Audio:" + nAudioStatus[i] + "  Video:" + nVideoStatus[i]);
            }
		}
	}
}
