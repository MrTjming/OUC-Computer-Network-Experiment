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

	public static long TIMEOUTTIME = 5000;// ��ʱʱ��
	Logger logger;
	List<Window> sendContent;
	int startWindowIndex; // ��������ͷ
	int ackWindowIndex; // �ѷ��͵����λ��
	int endWindosIndex; // ��������β
	int cwnd =1; //�������ڴ�С
	HashMap<Integer,Integer> indexMap;
	Client client;

	int ssthresh = 16; // ����ʼ������
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
			fh.setFormatter(new SimpleFormatter());//�����ʽ

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
		//�������ݱ�
		sendWindow(window,0);
	}

	private void sendWindow(Window window,int type){
		//�������ݱ�
		window.startSendTime=System.currentTimeMillis();
		send(window.packet,type);
	}

	private void send(TCP_PACKET stcpPack,int type){
		//�������ݱ�
		client.send(stcpPack);
		switch (type){
			case 0:
				logger.info("[�״�]���Ͱ�,seq:"+stcpPack.getTcpH().getTh_seq()+" index"+indexMap.get(stcpPack.getTcpH().getTh_seq()));
				break;
			case 1:
				logger.warning("[��ʱ]���Ͱ�,seq::"+stcpPack.getTcpH().getTh_seq()+" index"+indexMap.get(stcpPack.getTcpH().getTh_seq()));
				break;
			case 2:
				logger.warning("[����]���Ͱ�,seq::"+stcpPack.getTcpH().getTh_seq()+" index"+indexMap.get(stcpPack.getTcpH().getTh_seq()));
				break;
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
					// �����index������ʱ��
					window = sendContent.get(index);
					if (TIMEOUTTIME < (System.currentTimeMillis() - window.getStartSendTime())) {
						//  ��û���յ�ack,�����ط�
						if (!window.isAck()) {
							sendWindow(sendContent.get(index),1);
							break;
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
		Window window = null;
		int ackNum=recvPack.getTcpH().getTh_ack();
		logger.info("���յ�ack:"+ackNum);

		int ackIndex=indexMap.get(ackNum);
		if(ackIndex>=startWindowIndex){
			// ����յ��Ĳ����ӳٵ���İ�,����
			int tempSeq;
			int index=startWindowIndex;

			// ���������ڻ��пռ�
			for (; index <=ackWindowIndex ; index++) {
				window=sendContent.get(index);
				tempSeq=window.packet.getTcpH().getTh_seq();

				// �����ack ���ڻ���������Index�±��Ӧ���Ĵ��ڵĻ�,˵��ǰ���Ҳ�յ���
				if (ackIndex >= indexMap.get(tempSeq)) {
					logger.info(getWindowInfo()+"���յ�ackNum:"+tempSeq+" (���ڵ�ǰ)indexΪ:"+index+"�Ĵ��ڿ��Ѿ�ack");
					window.setAck(true);
				} else {
					// �ô��ڵ�ack����+1
					window.setDuplicateAckNum(window.getDuplicateAckNum() + 1);

					// ����ð��յ�3��ackʱ,˵������ӵ��
					if ((window.getDuplicateAckNum() >= MAX_Duplicate_NUM)&&(!window.isAck())) {
						isBadNet = true;
					}
					break;
				}
			}
			updateWindowSize(ackIndex);
		}else{
			logger.warning("�յ��ӳ�ack��,ackIndexֵ:"+ackIndex);
		}

		if (isBadNet) {
			// ����а����ظ��յ�MAX_Duplicate_NUM������,˵�����粻զ��,��С����
			if (ssthresh > 0 && cwnd > 0) {
				int oldSsthresh=ssthresh;
				ssthresh = (cwnd / 2);
				if (ssthresh < 2) {
					ssthresh = 2;
				}
				// TCP Tahoe��ʽ
				// cwnd = 1;

				// TCP Reno��ʽ
				cwnd=oldSsthresh+1;

				logger.warning(String.format(getWindowInfo()+"����ӵ��,����������:%d, ��ǰ���ڷ�Χ(%d,%d),acknum=%d\n", ssthresh,startWindowIndex,endWindosIndex,ackWindowIndex));
			}

			// �����ش�
			if (cwnd != -1) {
				updateWindowSize(ackIndex);
				window.setDuplicateAckNum(0);
				sendWindow(window,2);
			}
		}else {
			// ����״������,���󻬶�����
			if ((ssthresh > 0) && (cwnd > 0)) {
				if (cwnd <= ssthresh) {
					cwnd *= 2;
				} else {
					// �ӷ�����
					cwnd += 1;
				}
				if(cwnd>MAX_Window_Size){
					cwnd=MAX_Window_Size;
				}
				updateWindowSize(ackIndex);
				logger.info(String.format(getWindowInfo()+"��������,�����������ڴ�С:%d, ��ǰ���ڷ�Χ(%d,%d),acknum=%d\n", cwnd,startWindowIndex,endWindosIndex,ackWindowIndex));
			}
		}

	}


}
