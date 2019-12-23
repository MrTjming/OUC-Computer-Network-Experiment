package com.ouc.tcp.RDT2_0;

import java.util.Arrays;
import java.util.zip.CRC32;

import com.ouc.tcp.message.TCP_PACKET;

public class CheckSum {
	
	/*����TCP���Ķ�У��ͣ�ֻ��У��TCP�ײ��е�seq��ack��sum���Լ�TCP�����ֶ�*/
	public static short computeChkSum(TCP_PACKET tcpPack) {
		short checkSum = 0;
		CRC32 crc=new CRC32();
		crc.update(tcpPack.getTcpH().getTh_seq());
		crc.update(tcpPack.getTcpH().getTh_ack());
		int[] data=tcpPack.getTcpS().getData();
		for (int i = 0; i <data.length ; i++) {
			crc.update(data[i]);
		}
		crc.update(Arrays.toString(tcpPack.getTcpS().getData()).getBytes());

		//����У���

		checkSum= (short)crc.getValue();
		//System.out.println(checkSum);
		
		return checkSum;
	}
	
}
