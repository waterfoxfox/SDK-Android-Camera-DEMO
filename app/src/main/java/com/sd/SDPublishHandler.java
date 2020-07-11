package com.sd;

import java.nio.ByteBuffer;


public class SDPublishHandler{


    public SendListener mListener = null;

    public SDPublishHandler (SendListener listener) {
        mListener = listener;
    }


    public void notifySendVideoStreaming(byte[] buffer, int size) {
        mListener.onSendVideoStreaming(buffer, size);
    }


    /**
     *  Add ADTS header at the beginning of each and every AAC packet.
     *  This is needed as MediaCodec encoder generates a packet of raw
     *  AAC data.
     *
     *  Note the packetLen must count in the ADTS header itself.
     **/
    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2;  //AAC LC
        //39=MediaCodecInfo.CodecProfileLevel.AACObjectELD;
        int freqIdx = SDEncoder.ASAMPLERATE_INDEX_ADTS;
        int chanCfg = SDEncoder.ACHANNEL_INDEX_ADTS;

        // fill in ADTS data
        packet[0] = (byte)0xFF;
        packet[1] = (byte)0xF9;
        packet[2] = (byte)(((profile-1)<<6) + (freqIdx<<2) +(chanCfg>>2));
        packet[3] = (byte)(((chanCfg&3)<<6) + (packetLen>>11));
        packet[4] = (byte)((packetLen&0x7FF) >> 3);
        packet[5] = (byte)(((packetLen&7)<<5) + 0x1F);
        packet[6] = (byte)0xFC;
    }

    public void notifySendAudioStreaming(ByteBuffer byteBuffer, int size) {
        //MediaCodec编码得到的AAC音频流为RAM格式，底层要求输入为ADTS格式
        //在此进行ADTS封装，加入7字节ADTS头
        byte[] buffer = new byte[size + 7];
        addADTStoPacket(buffer, size + 7);
        byteBuffer.get(buffer, 7 , size);
        mListener.onSendAudioStreaming(buffer, size + 7);
    }


    public interface SendListener {

        void onSendVideoStreaming(byte[] buffer, int size);

        void onSendAudioStreaming(byte[] buffer, int size);
    }

}


