package com.ouc.tcp.test;

import com.ouc.tcp.client.Client;
import com.ouc.tcp.message.TCP_PACKET;

import java.io.IOException;
import java.util.*;
import java.util.logging.*;

public class SendWindow {
	class Window {
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

	public static long TIMEOUTTIME = 5000;// 超时时间
	Logger logger;
	List<Window> sendContent;
	int startWindowIndex; // 滑动窗口头
	int ackWindowIndex; // 已发送的最大位置
	int endWindosIndex; // 滑动窗口尾
	int cwnd =1; //滑动窗口大小
	HashMap<Integer,Integer> indexMap;
	Client client;

	int ssthresh = 16; // 慢开始的门限
	float betaValue = 0.5F;
	boolean canSend=true;
	final private static int MAX_Duplicate_NUM = 3;
	final private static int MAX_Window_Size = 100;

	public boolean continueSend(){
		return ackWindowIndex<this.endWindosIndex;
	}

	public SendWindow(Client client) {
		initLogger();
		updateWindowSize(0);
		this.client=client;
		sendContent=new ArrayList<Window>();
		ackWindowIndex=-1;
		indexMap=new HashMap<Integer, Integer>();
		dealWithOvertime();

	}
	private void updateWindowSize(int start){
		startWindowIndex=start;
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

	public Window addPacket(TCP_PACKET packet){
		Window window=new Window(packet);
		sendContent.add(window);
		ackWindowIndex++;

		indexMap.put(packet.getTcpH().getTh_seq(),ackWindowIndex);
		return window;
	}

	public void RdtSend(TCP_PACKET packet)  {
		Window window= null;
		try {
			window = addPacket(packet.clone());
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
		}
		sendWindow(window);
	}


	private void sendWindow(Window window){
		//发送数据报
		sendWindow(window,true);
	}

	private void sendWindow(Window window,boolean isFirst){
		//发送数据报
		window.startSendTime=System.currentTimeMillis();
		send(window.packet,isFirst);
	}

	private void send(TCP_PACKET stcpPack,boolean isFirst){
		//发送数据报

		client.send(stcpPack);
		if(isFirst){
			logger.info("首次发送包:"+stcpPack.getTcpH().getTh_seq());
		}else{
			logger.warning("重新发送包:"+stcpPack.getTcpH().getTh_seq());
		}
	}

	private String getWindowInfo(){
		return String.format("[%d %d %d]",startWindowIndex,ackWindowIndex,endWindosIndex);
	}

	void dealWithOvertime() {
		TimerTask dealOverTime = new TimerTask() {
			@Override
			public void run() {
				int index = startWindowIndex;
				Window window;
				while (index <= ackWindowIndex) {
					// 如果第index个包超时了
					window = sendContent.get(index);
					if (TIMEOUTTIME < (System.currentTimeMillis() - window.getStartSendTime())) {
						//  它没有收到ack,则尝试重发
						if (!window.isAck()) {
							logger.warning(getWindowInfo()+"超时重发包:");
							sendWindow(sendContent.get(index),false);
							break;
						}else{
							startWindowIndex=index+1;
						}
					}
					index++;
				}
			}
		};
		new Timer().schedule(dealOverTime, 0, 1000);
	}


	public void recv(TCP_PACKET recvPack){
		boolean isBadNet = false;

		int ackNum=recvPack.getTcpH().getTh_ack();
		logger.info("接收到ack:"+ackNum);

		int ackIndex=indexMap.get(ackNum);
		if(ackIndex<startWindowIndex){
			logger.warning("收到延迟ack包,ackIndex值:"+ackIndex);
			return;
		}

		startWindowIndex=Math.max(ackIndex,startWindowIndex);

		Window window = null;
		int tempSeq;
		int index=startWindowIndex;

		// 当滑动窗口还有空间
		for (; index <=ackWindowIndex ; index++) {
			window=sendContent.get(index);
			tempSeq=window.packet.getTcpH().getTh_seq();

			// 包里的ack 大于滑动窗口里Index下标对应包的窗口的话,说明前面的也收到了
			if (ackIndex >= indexMap.get(tempSeq)) {
				logger.info(getWindowInfo()+"接收到ackNum:"+tempSeq+" (大于当前)index为:"+index+"的窗口块已经ack");
				window.setAck(true);
//			} else if ((ack == tempSeq)) {
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

		startWindowIndex = ackIndex;
		endWindosIndex = startWindowIndex + cwnd;

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

				logger.warning(String.format(getWindowInfo()+"网络拥挤,设置新门限:%d, 当前窗口范围(%d,%d),acknum=%d\n", ssthresh,startWindowIndex,endWindosIndex,ackWindowIndex));
			}

			// 快速重传
			if (cwnd != -1) {
				updateWindowSize(ackIndex);
				logger.warning("快速重传:");
				window.setDuplicateAckNum(0);
				sendWindow(window,false);
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
				updateWindowSize(ackIndex);
				logger.info(String.format(getWindowInfo()+"网络良好,设置阻塞窗口大小:%d, 当前窗口范围(%d,%d),acknum=%d\n", cwnd,startWindowIndex,endWindosIndex,ackWindowIndex));
			}
		}

	}


}
