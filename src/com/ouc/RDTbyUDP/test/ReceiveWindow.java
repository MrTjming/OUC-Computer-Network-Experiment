package com.ouc.RDTbyUDP.test;
import java.io.*;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;


public class ReceiveWindow {
	Logger logger;
	SortedSet<Packet> recvBuffer;
	LinkedList<Packet> contentList;
	int recvBase=-1;
	int lastLength=0;
	boolean completed=false;
	Map<Integer,Integer> indexMap;
	long lastConnectTime;
	public static final long MAX_WAIT_TIME=30_000;
	public ReceiveWindow() {
		initLogger();
		lastConnectTime=System.currentTimeMillis();
		indexMap=new HashMap<>();
		contentList=new LinkedList<>();

		recvBuffer =new TreeSet<Packet>(new Comparator<Packet>() {
			@Override
			public int compare(Packet o1, Packet o2) {
				return o1.getSequenceNumber()-o2.getSequenceNumber();
			}
		});


		new Thread(new Runnable() {
			@Override
			public void run() {
				while(!getFile("myfile")){
					try {
						Thread.sleep(2000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}).start();

	}

	// ��ʼ����־
	private void initLogger(){
		logger= Logger.getLogger("RDTReceiver");
		logger.setUseParentHandlers(false);
		FileHandler fh = null;
		try {
			fh = new FileHandler("RDTReceiver.log",false);
			fh.setFormatter(new SimpleFormatter());//�����ʽ

		} catch (IOException e) {
			e.printStackTrace();
		}
		logger.addHandler(fh);
	}

	// �����ϴ�����ʱ��
	public void updateLastConnectTime(){
		lastConnectTime=System.currentTimeMillis();
	}

	// ����Ƿ�ʱ
	public boolean checkIsOverTime(){
		return (System.currentTimeMillis()-this.lastConnectTime>=MAX_WAIT_TIME);
	}


	public int addRecvPacket(Packet packet){
		updateLastConnectTime();
		int seq=packet.getSequenceNumber();
		if(seq==recvBase+lastLength||recvBase==-1){
			recvBase=seq;
			lastLength=packet.getData().length;
			contentList.add(packet);
			System.out.println(completed+"�յ���"+contentList.size()+"����:"+seq+" "+packet.isEnd());
			// ���´�������
			updateRcvBase();
			logger.info("�������seq:"+seq/Packet.MAX_PACKET_LENGTH+",����ack:"+recvBase/Packet.MAX_PACKET_LENGTH);
		}else if(seq>recvBase){
			recvBuffer.add(packet);
			System.out.printf("seq:%d recv:%d length:%d\n",seq,recvBase,packet.getData().length);
			logger.info("ʧ�����,����seq:"+seq/Packet.MAX_PACKET_LENGTH+"���б�,����ack:"+recvBase/Packet.MAX_PACKET_LENGTH);
		}else{
			logger.info("��Ч����,�յ���seq:"+seq/Packet.MAX_PACKET_LENGTH+",����ack:"+recvBase/Packet.MAX_PACKET_LENGTH);
		}
		completed=packet.isEnd()||completed;
		return recvBase;
	}


	public int updateRcvBase(){
		int lastSeq=contentList.getLast().sequenceNumber;
		if(!this.recvBuffer.isEmpty()){
			Iterator<Packet> it=this.recvBuffer.iterator();
			Packet packet;
			int nowSeq;
			while (it.hasNext()){
				packet=it.next();
				nowSeq=packet.getSequenceNumber();
				if(nowSeq==lastSeq+lastLength){
					lastLength=packet.getData().length;
					lastSeq=nowSeq;
					contentList.add(packet);
					it.remove();
				}else{
					break;
				}

			}

		}
		recvBase=lastSeq;
		System.out.println("���½��մ�������Ϊ"+recvBase+" ���յ���:"+contentList.size());
		return lastSeq;


	}

	public boolean getFile(String filePath){
		if(completed){
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			System.out.println("�յ�������:"+contentList.size());
			try {
				FileOutputStream fileOutputStream = new FileOutputStream(filePath,true);
				byte[] data;
				Iterator<Packet> it= contentList.iterator();
				while (it.hasNext()){
					data=it.next().getData();
					fileOutputStream.write(data);
					fileOutputStream.flush();
				}
				fileOutputStream.close();

			} catch (IOException e) {
				e.printStackTrace();
			}
			System.out.println("�ɹ������ļ�");
			return true;
		}else{
			System.out.println("�����ļ�ʧ��,��δ�������");
			return false;
		}
	}

}
