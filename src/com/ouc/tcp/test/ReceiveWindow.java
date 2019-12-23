package com.ouc.tcp.test;

import com.ouc.tcp.client.Client;
import com.ouc.tcp.message.TCP_PACKET;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
	SortedSet<Window> recvContent;
	int recvBase=0;
	Client client;
	int lastSaveSeq=-1;
	public ReceiveWindow(Client client) {
		initLogger();
		this.client=client;

		recvContent=new TreeSet<Window>(new Comparator<Window>() {
			@Override
			public int compare(Window o1, Window o2) {
				return o1.packet.getTcpH().getTh_seq()-o2.packet.getTcpH().getTh_seq();
			}
		});
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
		// 计算接收窗口左沿
		int rcvBase=calcRcvBase();
		if(seq==rcvBase+100 || lastSaveSeq==-1){
			lastSaveSeq=seq;
			recvContent.add(new Window(packet));

			waitWrite(packet);
			rcvBase=lastSaveSeq;
			logger.info("有序接收,缓存seq:"+seq+"到列表,返回ack:"+rcvBase);
		}else if(seq>rcvBase){
			recvContent.add(new Window(packet));
			logger.info("失序接收,缓存seq:"+seq+"到列表,返回ack:"+rcvBase);
		}

		//recvBase=rcvBase;


		return rcvBase;

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
			it.next();
			it.remove();
			while (it.hasNext()){
				window=it.next();
				seq=window.packet.getTcpH().getTh_seq();
				if((seq<=lastSaveSeq+100)&&(seq>lastSaveSeq)){// 判断是否有序
					data=window.packet.getTcpS().getData();
					lastSaveSeq=seq;
					logger.info("连续写入,seq:"+seq+"\n");
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
