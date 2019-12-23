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
		// 判断是否有序
		int seq=packet.getTcpH().getTh_seq();
		if((seq<=lastSaveSeq+100)&&(seq>lastSaveSeq)||lastSaveSeq==-1){
			lastSaveSeq=seq;
			waitWrite(packet);
		}else if(seq>lastSaveSeq){
			System.out.println("缓存seq:"+seq+"到列表,last is:"+lastSaveSeq);
			recvContent.add(new Window(packet));
		}
	}

	public void waitWrite(TCP_PACKET packet){
		int seq=packet.getTcpH().getTh_seq();

		File fw = new File("recvData.txt");
		BufferedWriter writer;
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式
		try {
			writer = new BufferedWriter(new FileWriter(fw, true));
			Window window;
			int[] data=packet.getTcpS().getData();
			for(int i = 0; i < data.length; i++) {
				writer.write(data[i] + "\n");
			}
			writer.flush();		//清空输出缓存
			Iterator<Window> it=recvContent.iterator();
			while (it.hasNext()){
				window=it.next();
				seq=window.packet.getTcpH().getTh_seq();
				data=window.packet.getTcpS().getData();
				if((seq<=lastSaveSeq+100)&&(seq>lastSaveSeq)){// 判断是否有序
					lastSaveSeq=seq;
					for(int i = 0; i < data.length; i++) {
						writer.write(data[i] + "\n");
					}
					writer.flush();		//清空输出缓存
					it.remove();
				}
				else{
//					System.out.println("退出循环,当前seq为:"+seq+" last:"+lastSaveSeq);
					break;
				}
			}
			writer.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
