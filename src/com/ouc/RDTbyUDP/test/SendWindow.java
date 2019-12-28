package com.ouc.RDTbyUDP.test;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class SendWindow {
	public static long TIMEOUTTIME = 3000;// 超时时间
	DatagramSocket socket;
	Logger logger;
	List<Window> sendContent;
	InetAddress target_addr;
	int targetPort;

	int startWindowIndex; // 滑动窗口头
	int lastSendIndex; // 已发送的最大位置
	int endWindosIndex; // 滑动窗口尾
	int cwnd =1; //滑动窗口大小
	HashMap<Integer,Integer> indexMap;

	int ssthresh = 16; // 慢开始的门限
	float betaValue = 0.5F;
	boolean canSend=true;
	final private static int MAX_Duplicate_NUM = 3;
	final private static int MAX_Window_Size = 80;

	public boolean continueSend(){
		return lastSendIndex <this.endWindosIndex;
	}

	public SendWindow(DatagramSocket socket,InetAddress target_addr,int targetPort) {
		initLogger();
		updateWindowSize(0);
		this.socket=socket;
		this.target_addr=target_addr;
		this.targetPort=targetPort;

		sendContent=new ArrayList<Window>();
		lastSendIndex =-1;
		indexMap=new HashMap<Integer, Integer>();
		dealWithOvertime();

	}
	private void updateWindowSize(int newStart){
		startWindowIndex=Math.max(newStart,startWindowIndex);
		endWindosIndex=startWindowIndex+cwnd;
	}

	private void initLogger(){
		logger= Logger.getLogger("RDTSender");

		logger.setUseParentHandlers(false);
		FileHandler fh = null;
		try {
			fh = new FileHandler("RDTSender.log",false);
			fh.setFormatter(new SimpleFormatter());//输出格式

		} catch (IOException e) {
			e.printStackTrace();
		}
//		fh.setFormatter();
		logger.addHandler(fh);
	}

	public Window addPacket(Packet packet){
		Window window=new Window(packet);
		sendContent.add(window);
		indexMap.put(packet.getSequenceNumber(), ++lastSendIndex);
		return window;
	}

	public void RdtSend(Packet packet)  {
		Window window = addPacket(packet);
		sendWindow(window);
	}


	public void sendWindow(Window window){
		//发送数据报
		sendWindow(window,0);
	}

	public void sendWindow(Window window,int type){
		//发送数据报
		window.startSendTime=System.currentTimeMillis();
		rdtSend(window.packet,type);
	}


	// 处理ack
	public void autoRecvPacket(){
		int RECV_DATA_SIZE = 2880;
		DatagramPacket dataPacket = new DatagramPacket(new byte[RECV_DATA_SIZE], RECV_DATA_SIZE);
		// todo:修改结束条件为收到最后一个ack包
		int lastSeq=0;
		while (true){
			try {
				socket.receive(dataPacket);
				byte[] data = dataPacket.getData();
				ObjectInputStream msg = new ObjectInputStream(new ByteArrayInputStream(data));
				Packet recvPacket = (Packet) msg.readObject();
				if(recvPacket.getCheckSum()==recvPacket.calcCheckSum()){
					lastSeq=recvPacket.getSequenceNumber();
					recv(recvPacket);
				}else{
					System.out.println("校验码不匹配,传输出现错误");
				}

			} catch (IOException | ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
	}


	/**
	 * @param packet
	 * @param isFirst 0 首次发送,1 超时重发, 2快速重传
	 */
	public void rdtSend(Packet packet, int isFirst){
		switch (isFirst){
			case 0:
				logger.info("[首次]发送包,seq:"+packet.getSequenceNumber()+" index"+indexMap.get(packet.getSequenceNumber()));
				break;
			case 1:
				logger.warning("[超时]发送包,seq::"+packet.getSequenceNumber()+" index"+indexMap.get(packet.getSequenceNumber()));
				break;
			case 2:
				logger.warning("[快重]发送包,seq::"+packet.getSequenceNumber()+" index"+indexMap.get(packet.getSequenceNumber()));
				break;
		}

		// 使用UDP来发
		try {
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			ObjectOutputStream os;
			os = new ObjectOutputStream(outputStream);
			os.writeObject(packet);
			byte[] data = outputStream.toByteArray();
			DatagramPacket sendPacket = new DatagramPacket(data, data.length, target_addr, targetPort);
			socket.send(sendPacket);
			os.close();
			outputStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String getWindowInfo(){
		return String.format("[%d %d %d]",startWindowIndex, lastSendIndex,endWindosIndex);
	}

	void dealWithOvertime() {
		TimerTask dealOverTime = new TimerTask() {
			@Override
			public void run() {
				int index = startWindowIndex;
				Window window;
				while (index <= lastSendIndex) {
					window = sendContent.get(index);
					if (TIMEOUTTIME < (System.currentTimeMillis() - window.getStartSendTime()) && !window.isAck()) {
						//  // 如果第index个包超时了,则尝试重发
						System.out.println("超时重发包:"+index);
						sendWindow(sendContent.get(index),1);
					}
					index++;
				}
			}
		};
		new Timer().schedule(dealOverTime, 1000, 1000);
	}


	public void recv(Packet recvPack){
		boolean isBadNet = false;
		int ackNum=recvPack.getSequenceNumber();
		logger.info("接收到ack:"+ackNum/Packet.MAX_PACKET_LENGTH);
		if(!indexMap.containsKey(ackNum)){
			System.out.println(ackNum/Packet.MAX_PACKET_LENGTH+"不存在[发生错误],ack为"+ackNum);
			return;
		}
		int ackIndex=indexMap.get(ackNum);

		Window window = null;
		int index=startWindowIndex;
		if(ackIndex>=startWindowIndex){
			// 如果收到的不是延迟到达的包,则处理
			int tempSeq;
			// 更新窗口左沿
			for (; index <= lastSendIndex; index++) {
				window=sendContent.get(index);
				tempSeq=window.packet.getSequenceNumber();
				// 包里的ack 大于滑动窗口里Index下标对应包的窗口的话,说明前面的也收到了
				if(!indexMap.containsKey(tempSeq)){
					logger.severe("严重错误:seq:"+tempSeq+"不在hash表");
				}else if (ackIndex >= indexMap.get(tempSeq)) {
					if(!window.isAck()){
						logger.info(getWindowInfo()+"接收到ackNum:"+tempSeq/Packet.MAX_PACKET_LENGTH+" (大于当前)index为:"+index+"的窗口块已经ack");
						window.setAck(true);
					}
				} else {
					// 该窗口的ack数量+1
					window.setDuplicateAckNum(window.getDuplicateAckNum() + 1);

					// 如果该包收到3次ack时,说明网络拥塞
					if ((window.getDuplicateAckNum() >= MAX_Duplicate_NUM)&&(!window.isAck())) {
						isBadNet = true;
					}
					break;
				}
			}

			updateWindowSize(index);
		}else{
			logger.warning("收到延迟ack包,ackIndex值:"+ackIndex);
		}

		if (isBadNet) {
			// 如果有包被重复收到MAX_Duplicate_NUM次以上,说明网络不咋地,缩小窗口
			if (ssthresh > 0 && cwnd > 0) {
				int oldSsthresh=ssthresh;
				ssthresh = (cwnd / 2);
				if (ssthresh < 2) {
					ssthresh = 2;
				}
				// TCP Tahoe方式
				// cwnd = 1;

				// TCP Reno方式
				cwnd=oldSsthresh+1;

				logger.warning(String.format(getWindowInfo()+"网络拥挤,设置新门限:%d, 当前窗口范围(%d,%d),lastSendIndex=%d\n", ssthresh,startWindowIndex,endWindosIndex, lastSendIndex));
			}
			updateWindowSize(index);
			// 快速重传
			if (cwnd != -1) {
				window.setDuplicateAckNum(0);
				sendWindow(window,2);
			}
		}else {
			// 网络状况良好,增大滑动窗口
			if ((ssthresh > 0) && (cwnd > 0)) {
				if (cwnd <= ssthresh) {
					cwnd *= 2;
				} else {
					// 加法增大
					cwnd += 1;
				}
				if(cwnd>MAX_Window_Size){
					cwnd=MAX_Window_Size;
				}
				updateWindowSize(index);
				logger.info(String.format(getWindowInfo()+"网络良好,设置阻塞窗口大小:%d, 当前窗口范围(%d,%d),lastSendIndex=%d\n", cwnd,startWindowIndex,endWindosIndex, lastSendIndex));
			}
		}
	}


}
