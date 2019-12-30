/***************************2.1: ACK/NACK
**************************** Feng Hong; 2015-12-09*/

package com.ouc.tcp.RDT4_0;

import com.ouc.tcp.client.Client;
import com.ouc.tcp.client.TCP_Sender_ADT;
import com.ouc.tcp.client.UDT_Timer;
import com.ouc.tcp.message.TCP_PACKET;

import java.util.TimerTask;

public class TCP_Sender extends TCP_Sender_ADT {
	UDT_Timer timer;
	private TCP_PACKET tcpPack;	//�����͵�TCP���ݱ�
	SendWindow sendWindow;
		
	/*���캯��*/
	public TCP_Sender() {
		super();	//���ó��๹�캯��
		super.initTCP_Sender(this);		//��ʼ��TCP���Ͷ�
		sendWindow=new SendWindow(client);
	}

	class My_UDT_RetransTask extends TimerTask {
		private Client senderClient;
		private TCP_PACKET reTransPacket;

		public My_UDT_RetransTask(Client client, TCP_PACKET packet){
			this.senderClient = client;
			this.reTransPacket = packet;
		}

		@Override
		public void run() {
			System.out.println("��ʱ�ط���");
			this.senderClient.send(this.reTransPacket);
		}
	}
	
	@Override
	//�ɿ����ͣ�Ӧ�ò���ã�����װӦ�ò����ݣ�����TCP���ݱ�����Ҫ�޸�
	public void rdt_send(int dataIndex, int[] appData) {
		System.out.println("Ӧ�ò����rdt_send,����index:"+dataIndex);
		//����TCP���ݱ���������ź������ֶ�/У���),ע������˳��
		tcpH.setTh_seq(dataIndex * appData.length + 1);//���������Ϊ�ֽ����ţ�
		tcpS.setData(appData);
		try {
			tcpPack = new TCP_PACKET(tcpH.clone(), tcpS.clone(), destinAddr);
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
		}

		tcpH.setTh_sum(CheckSum.computeChkSum(tcpPack));
		tcpPack.setTcpH(tcpH);
		
		//����TCP���ݱ�
		try {
			sendWindow.RdtSend(tcpPack);
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
		}
		//udt_send(tcpPack);

		while (!sendWindow.continueSend()){
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	@Override
	//���ɿ����ͣ�������õ�TCP���ݱ�ͨ�����ɿ������ŵ����ͣ������޸Ĵ����־
	public void udt_send(TCP_PACKET stcpPack) {
		//���ô�����Ʊ�־
		//0.�ŵ��޲��
		//1.ֻ����
		//2.ֻ����
		//3.ֻ�ӳ�
		//4.���� / ����
		//5.���� / �ӳ�
		//6.���� / �ӳ�
		//7.���� / ���� / �ӳ�
		tcpH.setTh_eflag((byte)7);
		//System.out.println("to send: "+stcpPack.getTcpH().getTh_seq());				
		//�������ݱ�
		client.send(stcpPack);
	}
	
	@Override
	//��Ҫ�޸�
	public void waitACK() {
		//ѭ�����ackQueue
		//ѭ�����ȷ�ϺŶ������Ƿ������յ���ACK
//		while(true) {
//			if(!ackQueue.isEmpty()){
//				int currentAck=ackQueue.poll();
//				for (int i = 0; i < sendWindow.sendContent.size(); i++) {
//					SendWindow.Window tempWindow=sendWindow.sendContent.get(i);
//					if(tempWindow.packet.getTcpH().getTh_seq()==currentAck){
//						tempWindow.ack=true;
//						break;
//					}
//				}
//			}else{
//				break;
//			}
//		}
	}

	@Override
	//���յ�ACK���ģ����У��ͣ���ȷ�ϺŲ���ack����;NACK��ȷ�Ϻ�Ϊ��1��3.0�汾����Ҫ�޸�
	public void recv(TCP_PACKET recvPack) {
		if(CheckSum.computeChkSum(recvPack)==recvPack.getTcpH().getTh_sum()){
			sendWindow.recv(recvPack);
		}else{
			System.out.println("���յ���ack����");
		}
	}


	
}
