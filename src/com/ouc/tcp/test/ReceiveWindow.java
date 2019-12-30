package com.ouc.tcp.test;

import com.ouc.tcp.client.Client;
import com.ouc.tcp.message.TCP_PACKET;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;


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

	Logger logger;
	SortedSet<TCP_PACKET> recvBuffer;
	LinkedList<TCP_PACKET> contentList;
	int lastLength=0;
	Client client;
	int lastSaveSeq=-1;
	public ReceiveWindow(Client client) {
		initLogger();
		this.client=client;

		recvBuffer=new TreeSet<TCP_PACKET>(new Comparator<TCP_PACKET>() {
			@Override
			public int compare(TCP_PACKET o1, TCP_PACKET o2) {
				return o1.getTcpH().getTh_seq()-o2.getTcpH().getTh_seq();
			}
		});
		contentList =new LinkedList<TCP_PACKET>();
	}

	private void initLogger(){
		logger= Logger.getLogger("RDTReceiver");
		logger.setUseParentHandlers(false);
		FileHandler fh = null;
		try {
			fh = new FileHandler("RDTReceiver.log",false);
			fh.setFormatter(new SimpleFormatter());//输出格式

		} catch (IOException e) {
			e.printStackTrace();
		}
		logger.addHandler(fh);
	}

	public int addRecvPacket(TCP_PACKET packet){
		int seq=packet.getTcpH().getTh_seq();
		if(seq==lastSaveSeq+lastLength || lastSaveSeq==-1){
			lastLength=packet.getTcpS().getData().length;
			lastSaveSeq=seq;
			contentList.add(packet);
			waitWrite();
			logger.info("有序接收,缓存seq:"+seq+"到列表,返回ack:"+lastSaveSeq);
		}else if(seq>lastSaveSeq){
			recvBuffer.add(packet);
			logger.info("失序接收,缓存seq:"+seq+"到列表,返回ack:"+lastSaveSeq);
		}
		return lastSaveSeq;
	}

	public void waitWrite(){
		File fw = new File("recvData.txt");
		BufferedWriter writer;
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式
		try {
			if(!this.recvBuffer.isEmpty()){
				Iterator<TCP_PACKET> it=this.recvBuffer.iterator();
				TCP_PACKET packet;
				int nowSeq;
				while (it.hasNext()){
					packet=it.next();
					nowSeq=packet.getTcpH().getTh_seq();
					if(nowSeq==lastSaveSeq+lastLength){
						lastLength=packet.getTcpS().getData().length;
						lastSaveSeq=nowSeq;
						contentList.add(packet);
						it.remove();
					}else{
						break;
					}
				}
			}


			int[] data;
			Iterator<TCP_PACKET> it= contentList.iterator();
			writer = new BufferedWriter(new FileWriter(fw, true));
			while (it.hasNext()){
				data=it.next().getTcpS().getData();
				for(int i = 0; i < data.length; i++) {
					writer.write(data[i] + "\n");
				}
				it.remove();
				writer.flush();		//清空输出缓存
			}
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public int calcRcvBase(){
//		int lastSeq=-1;
//		if(!this.recvContent.isEmpty()){
//			Iterator<Window> it=this.recvContent.iterator();
//			Window window;
//			lastSeq=it.next().packet.getTcpH().getTh_seq();
//			int nowSeq;
//			while (it.hasNext()){
//				nowSeq=it.next().packet.getTcpH().getTh_seq();
//				if(nowSeq!=lastSeq+1){
//					break;
//				}
//				lastSeq=nowSeq;
//			}
//			return lastSeq;
//		}
//		return lastSeq;
		return lastSaveSeq;

	}

}
