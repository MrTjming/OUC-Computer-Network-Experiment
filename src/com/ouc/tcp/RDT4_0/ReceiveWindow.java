package com.ouc.tcp.RDT4_0;

import com.ouc.tcp.client.Client;
import com.ouc.tcp.message.TCP_PACKET;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;


public class ReceiveWindow {
	class Window{
		boolean ack;
		long startSendTime;
		int duplicateAckNum;
		TCP_PACKET packet;

		Window(TCP_PACKET packet) {
			this.packet = packet;
		}

		boolean isAck() {
			return ack;
		}

		long getStartSendTime() {
			return startSendTime;
		}

		int getDuplicateAckNum() {
			return duplicateAckNum;
		}

		TCP_PACKET getPacket() {
			return packet;
		}

		void setAck(boolean ack) {
			this.ack = ack;
		}

		void setStartSendTime(long startSendTime) {
			this.startSendTime = startSendTime;
		}

		void setDuplicateAckNum(int duplicateAckNum) {
			this.duplicateAckNum = duplicateAckNum;
		}

		public void setPacket(TCP_PACKET packet) {
			this.packet = packet;
		}

	}


	SortedSet<Window> recvContent;
	Client client;
	int lastSaveSeq=-1;
	int lastLength=0;
	public ReceiveWindow(Client client) {
		this.client=client;

		recvContent=new TreeSet<Window>(new Comparator<Window>() {
			@Override
			public int compare(Window o1, Window o2) {
				return o1.packet.getTcpH().getTh_seq()-o2.packet.getTcpH().getTh_seq();
			}
		});
	}

	public void addRecvPacket(TCP_PACKET packet){
		// �ж��Ƿ�����
		int seq=packet.getTcpH().getTh_seq();
		if((seq==lastSaveSeq+lastLength)||lastSaveSeq==-1){
			lastLength=packet.getTcpS().getData().length;
			lastSaveSeq=seq;
			waitWrite(packet);
		}else if(seq>lastSaveSeq){
			System.out.println("����seq:"+seq+"���б�,last is:"+lastSaveSeq);
			recvContent.add(new Window(packet));
		}
	}

	public void waitWrite(TCP_PACKET packet){
		int seq;

		File fw = new File("recvData.txt");
		BufferedWriter writer;
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//�������ڸ�ʽ
		try {
			writer = new BufferedWriter(new FileWriter(fw, true));
			Window window;
			int[] data=packet.getTcpS().getData();
			for(int i = 0; i < data.length; i++) {
				writer.write(data[i] + "\n");
			}
			writer.flush();		//����������
			Iterator<Window> it=recvContent.iterator();
			// �ڻ�������￴�Ƿ�������İ�,һ�����ϵݽ�
			while (it.hasNext()){
				window=it.next();
				seq=window.packet.getTcpH().getTh_seq();
				data=window.packet.getTcpS().getData();
				if(seq==lastSaveSeq+lastLength){// �ж��Ƿ�����
					lastLength=packet.getTcpS().getData().length;
					lastSaveSeq=seq;
					for(int i = 0; i < data.length; i++) {
						writer.write(data[i] + "\n");
					}
					writer.flush();		//����������
					it.remove();
				}
				else{
//					System.out.println("�˳�ѭ��,��ǰseqΪ:"+seq+" last:"+lastSaveSeq);
					break;
				}
			}
			writer.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
