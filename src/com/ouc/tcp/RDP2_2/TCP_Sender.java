/***************************2.1: ACK/NACK
**************************** Feng Hong; 2015-12-09*/

package com.ouc.tcp.RDP2_2;

import com.ouc.tcp.client.TCP_Sender_ADT;
import com.ouc.tcp.message.TCP_PACKET;

public class TCP_Sender extends TCP_Sender_ADT {
	
	private TCP_PACKET tcpPack;	//�����͵�TCP���ݱ�
		
	/*���캯��*/
	public TCP_Sender() {
		super();	//���ó��๹�캯��
		super.initTCP_Sender(this);		//��ʼ��TCP���Ͷ�
	}
	
	@Override
	//�ɿ����ͣ�Ӧ�ò���ã�����װӦ�ò����ݣ�����TCP���ݱ�����Ҫ�޸�
	public void rdt_send(int dataIndex, int[] appData) {
				
		//����TCP���ݱ���������ź������ֶ�/У���),ע������˳��
		tcpH.setTh_seq(dataIndex * appData.length + 1);//���������Ϊ�ֽ����ţ�
		tcpS.setData(appData);
		tcpPack = new TCP_PACKET(tcpH, tcpS, destinAddr);		
				
		tcpH.setTh_sum(CheckSum.computeChkSum(tcpPack));
		tcpPack.setTcpH(tcpH);
		
		//����TCP���ݱ�
		udt_send(tcpPack);
		
//		//����3.0�汾�����ü�ʱ���ͳ�ʱ�ش�����
//		UDT_Timer timer = new UDT_Timer();
//		UDT_RetransTask reTrans = new UDT_RetransTask(client, tcpPack);
//
//		//ÿ��3��ִ���ش���ֱ���յ�ACK
//		timer.schedule(reTrans, 3000, 3000);
		
		//�ȴ�ACK����
		waitACK();
		
	}
	
	@Override
	//���ɿ����ͣ�������õ�TCP���ݱ�ͨ�����ɿ������ŵ����ͣ������޸Ĵ����־
	public void udt_send(TCP_PACKET stcpPack) {
		//���ô�����Ʊ�־
		tcpH.setTh_eflag((byte)1);		
		//System.out.println("to send: "+stcpPack.getTcpH().getTh_seq());				
		//�������ݱ�
		client.send(stcpPack);
	}
	
	@Override
	//��Ҫ�޸�
	public void waitACK() {
		//ѭ�����ackQueue
		//ѭ�����ȷ�ϺŶ������Ƿ������յ���ACK
		while(true) {
			if(!ackQueue.isEmpty()){
				int currentAck=ackQueue.poll();
				System.out.println("CurrentAck: "+currentAck);
				if  (currentAck == tcpPack.getTcpH().getTh_seq()){
					System.out.println("Clear: "+tcpPack.getTcpH().getTh_seq());
					//����3.0��
					//timer.cancel();
					break;
				}else{
					System.out.println("Retransmit: "+tcpPack.getTcpH().getTh_seq());
					udt_send(tcpPack);
				}
			}
		}
	}

	@Override
	//���յ�ACK���ģ����У��ͣ���ȷ�ϺŲ���ack����;NACK��ȷ�Ϻ�Ϊ��1��3.0�汾����Ҫ�޸�
	public void recv(TCP_PACKET recvPack) {
		if(CheckSum.computeChkSum(recvPack)==recvPack.getTcpH().getTh_sum()){
			System.out.println("Receive ACK Number�� "+ recvPack.getTcpH().getTh_ack());
			ackQueue.add(recvPack.getTcpH().getTh_ack());
			System.out.println();
		}else{
			System.out.println("Receive Wrong ACK Number�� ");
			ackQueue.add(-1);
			System.out.println();
		}
	}

	public void timer(){

	}
	
}
