package com.ouc.tcp.RDT4_0;

import com.ouc.tcp.client.Client;
import com.ouc.tcp.message.TCP_PACKET;

import java.util.*;
import java.util.logging.Logger;

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

	public static long TIMEOUTTIME = 3000;// 超时时间
	Logger logger;
	List<Window> sendContent;
	int startWindowIndex; // 窗口头
	int endWindosIndex; // 窗口尾
	int windowSize=100;
	Client client;

	public boolean continueSend(){
		int num=this.startWindowIndex+this.windowSize-this.endWindosIndex;
		if(num<=0){
			return false;
		}
		System.out.println("可以继续");
		return true;
	}

	public SendWindow(Client client) {
		logger= Logger.getLogger("RDTSender");
		this.client=client;
		sendContent=new ArrayList<Window>();
		waitOvertime();
	}

	public Window addPacket(TCP_PACKET packet){
		Window window=new Window(packet);
		sendContent.add(window);
		endWindosIndex++;
		return window;
	}

	public void RdtSend(TCP_PACKET packet) throws CloneNotSupportedException {
		Window window=addPacket(packet.clone());
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
			//logger.info("首次发送包:"+stcpPack.getTcpH().getTh_seq());
		}else{
			logger.warning("重新发送包:"+stcpPack.getTcpH().getTh_seq());
		}
	}


	public void waitOvertime() {
		TimerTask dealOverTime = new TimerTask() {
			@Override
			public void run() {
				int index = startWindowIndex;
				boolean updateStart=true;
				Window window;
				while (index < endWindosIndex) {
					// 如果第index个包超时了
					window = sendContent.get(index);
					if(updateStart && window.ack){
						startWindowIndex=index+1;
						logger.info("更新start值:"+startWindowIndex);
					}else if(!window.ack){
						updateStart=false;
						if (TIMEOUTTIME < (System.currentTimeMillis() - window.getStartSendTime())) {
							//  它没有收到ack,则尝试重发
							sendWindow(window,false);
						}
					}
					index++;
				}
			}
		};
		new Timer().schedule(dealOverTime, 0, 200);
	}

	public void recv(TCP_PACKET recvPack){
		int ack=recvPack.getTcpH().getTh_ack();
		Window window;
		boolean canUpdate=true;
		int seq;
		for (int i = startWindowIndex; i <endWindosIndex ; i++) {
			window=sendContent.get(i);
			seq=window.packet.getTcpH().getTh_seq();
			if(seq<=ack){
				if(canUpdate && window.isAck()){
					startWindowIndex=i+1;
				}else{
					canUpdate=false;
				}

				if(seq==ack){
					if(!window.isAck()){
//						logger.info("接收到ack:"+ack+" index为:"+i+"的窗口块ack");
					}else{
						logger.info("重复接收到ack:"+ack+" index为:"+i+"的窗口块已经ack");
					}

					window.ack=true;
					break;
				}
			}else{
				break;
			}
		}
	}



}
