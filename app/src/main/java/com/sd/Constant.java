package com.sd;
//和C层一致的常量定义
public class Constant {

	// 用户类型
	public interface UserType {
		//USER_TYPE_OTHER						0  //其他类型
		//USER_TYPE_AV_SEND_RECV				1  //音视频收发类型
		//USER_TYPE_AV_RECV_ONLY				2  //仅接收音视频类型
		//USER_TYPE_AV_SEND_ONLY				3  //仅发送音视频类型
		final byte USER_TYPE_OTHER = 0;
		final byte USER_TYPE_AV_SEND_RECV = 1;
		final byte USER_TYPE_AV_RECV_ONLY = 2;
		final byte USER_TYPE_AV_SEND_ONLY = 3;		
	}
	
	// 底层状况通知
	public interface SystemStatusType {	
		final byte SYS_NOTIFY_EXIT_NORMAL = 0;			// 正常退出
		final byte SYS_NOTIFY_EXIT_ABNORMAL = 1;		// 异常退出 未知原因
		final byte SYS_NOTIFY_EXIT_LOSTCONNECT = 2;		// 底层网络原因与服务器断开
		final byte SYS_NOTIFY_EXIT_KICKED = 3;			// 用户被KICKED		
		final byte SYS_NOTIFY_ONLINE_SUCCESS = 4;		// 登录成功
		final byte SYS_NOTIFY_ONLINE_FAILED = 5;		// 登录失败
		final byte SYS_NOTIFY_RECON_START = 6;			// 客户端掉线，内部开始自动重连
		final byte SYS_NOTIFY_RECON_SUCCESS = 7;		// 内部自动重连成功
		final byte SYS_NOTIFY_ONPOSITION = 8;			// 某客户端开始发布	
		final byte SYS_NOTIFY_OFFPOSITION = 9;			// 某客户端停止发布			
	}	
	
	// 输出到日志文件的级别
	public interface LogLevel {
		final byte LOG_LEVEL_DEBUG = 1;
		final byte LOG_LEVEL_INFO = 2;
		final byte LOG_LEVEL_WARN = 3;
		final byte LOG_LEVEL_ERROR = 4;
		final byte LOG_LEVEL_ALARM = 5;
		final byte LOG_LEVEL_FATAL = 6;
		final byte LOG_LEVEL_NONE = 7;
	}
}
