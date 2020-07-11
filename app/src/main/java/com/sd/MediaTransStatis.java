/**
 * 从底层返回的统计信息类
 */
package com.sd;
import com.sd.Constant;


//JNI层使用，请勿修改本类
public class MediaTransStatis 
{
	public int nVideoUpLostRatio;
	public int nVideoDownLostRatio;
	public int nAudioUpLostRatio;
	public int nAudioDownLostRatio;

	public int nVideoUpRate;
	public int nVideoDownRate;
	public int nAudioUpRate;
	public int nAudioDownRate;

	public int nP2PStatus;

	public MediaTransStatis()
	{
		super();
		nVideoUpLostRatio = 0;
		nVideoDownLostRatio = 0;
		nAudioUpLostRatio = 0;
		nAudioDownLostRatio = 0;

		nVideoUpRate = 0;
		nVideoDownRate = 0;
		nAudioUpRate = 0;
		nAudioDownRate = 0;

		nP2PStatus = 0;
	}
}