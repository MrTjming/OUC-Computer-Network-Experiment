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
	public static long TIMEOUTTIME = 3000;// ��ʱʱ��
	DatagramSocket socket;
	Logger logger;
	List<Window> sendContent;
	InetAddress target_addr;
	int targetPort;

	int startWindowIndex; // ��������ͷ
	int lastSendIndex; // �ѷ��͵����λ��
	int endWindosIndex; // ��������β
	int cwnd =1; //�������ڴ�С
	HashMap<Integer,Integer> indexMap;

	int ssthresh = 16; // ����ʼ������
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
			fh.setFormatter(new SimpleFormatter());//�����ʽ

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
		//�������ݱ�
		sendWindow(window,0);
	}

	public void sendWindow(Window window,int type){
		//�������ݱ�
		window.startSendTime=System.currentTimeMillis();
		rdtSend(window.packet,type);
	}


	// ����ack
	public void autoRecvPacket(){
		int RECV_DATA_SIZE = 2880;
		DatagramPacket dataPacket = new DatagramPacket(new byte[RECV_DATA_SIZE], RECV_DATA_SIZE);
		// todo:�޸Ľ�������Ϊ�յ����һ��ack��
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
					System.out.println("У���벻ƥ��,������ִ���");
				}

			} catch (IOException | ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
	}


	/**
	 * @param packet
	 * @param isFirst 0 �״η���,1 ��ʱ�ط�, 2�����ش�
	 */
	public void rdtSend(Packet packet, int isFirst){
		switch (isFirst){
			case 0:
				logger.info("[�״�]���Ͱ�,seq:"+packet.getSequenceNumber()+" index"+indexMap.get(packet.getSequenceNumber()));
				break;
			case 1:
				logger.warning("[��ʱ]���Ͱ�,seq::"+packet.getSequenceNumber()+" index"+indexMap.get(packet.getSequenceNumber()));
				break;
			case 2:
				logger.warning("[����]���Ͱ�,seq::"+packet.getSequenceNumber()+" index"+indexMap.get(packet.getSequenceNumber()));
				break;
		}

		// ʹ��UDP����
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
						//  // �����index������ʱ��,�����ط�
						System.out.println("��ʱ�ط���:"+index);
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
		logger.info("���յ�ack:"+ackNum/Packet.MAX_PACKET_LENGTH);
		if(!indexMap.containsKey(ackNum)){
			System.out.println(ackNum/Packet.MAX_PACKET_LENGTH+"������[��������],ackΪ"+ackNum);
			return;
		}
		int ackIndex=indexMap.get(ackNum);

		Window window = null;
		int index=startWindowIndex;
		if(ackIndex>=startWindowIndex){
			// ����յ��Ĳ����ӳٵ���İ�,����
			int tempSeq;
			// ���´�������
			for (; index <= lastSendIndex; index++) {
				window=sendContent.get(index);
				tempSeq=window.packet.getSequenceNumber();
				// �����ack ���ڻ���������Index�±��Ӧ���Ĵ��ڵĻ�,˵��ǰ���Ҳ�յ���
				if(!indexMap.containsKey(tempSeq)){
					logger.severe("���ش���:seq:"+tempSeq+"����hash��");
				}else if (ackIndex >= indexMap.get(tempSeq)) {
					if(!window.isAck()){
						logger.info(getWindowInfo()+"���յ�ackNum:"+tempSeq/Packet.MAX_PACKET_LENGTH+" (���ڵ�ǰ)indexΪ:"+index+"�Ĵ��ڿ��Ѿ�ack");
						window.setAck(true);
					}
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

			updateWindowSize(index);
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

				logger.warning(String.format(getWindowInfo()+"����ӵ��,����������:%d, ��ǰ���ڷ�Χ(%d,%d),lastSendIndex=%d\n", ssthresh,startWindowIndex,endWindosIndex, lastSendIndex));
			}
			updateWindowSize(index);
			// �����ش�
			if (cwnd != -1) {
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
				updateWindowSize(index);
				logger.info(String.format(getWindowInfo()+"��������,�����������ڴ�С:%d, ��ǰ���ڷ�Χ(%d,%d),lastSendIndex=%d\n", cwnd,startWindowIndex,endWindosIndex, lastSendIndex));
			}
		}
	}


}
