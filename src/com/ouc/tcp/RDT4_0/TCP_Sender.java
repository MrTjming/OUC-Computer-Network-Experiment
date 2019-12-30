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
	private TCP_PACKET tcpPack;	//待发送的TCP数据报
	SendWindow sendWindow;
		
	/*构造函数*/
	public TCP_Sender() {
		super();	//调用超类构造函数
		super.initTCP_Sender(this);		//初始化TCP发送端
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
			System.out.println("超时重发包");
			this.senderClient.send(this.reTransPacket);
		}
	}
	
	@Override
	//可靠发送（应用层调用）：封装应用层数据，产生TCP数据报；需要修改
	public void rdt_send(int dataIndex, int[] appData) {
		System.out.println("应用层调用rdt_send,发送index:"+dataIndex);
		//生成TCP数据报（设置序号和数据字段/校验和),注意打包的顺序
		tcpH.setTh_seq(dataIndex * appData.length + 1);//包序号设置为字节流号：
		tcpS.setData(appData);
		try {
			tcpPack = new TCP_PACKET(tcpH.clone(), tcpS.clone(), destinAddr);
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
		}

		tcpH.setTh_sum(CheckSum.computeChkSum(tcpPack));
		tcpPack.setTcpH(tcpH);
		
		//发送TCP数据报
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
	//不可靠发送：将打包好的TCP数据报通过不可靠传输信道发送；仅需修改错误标志
	public void udt_send(TCP_PACKET stcpPack) {
		//设置错误控制标志
		//0.信道无差错
		//1.只出错
		//2.只丢包
		//3.只延迟
		//4.出错 / 丢包
		//5.出错 / 延迟
		//6.丢包 / 延迟
		//7.出错 / 丢包 / 延迟
		tcpH.setTh_eflag((byte)7);
		//System.out.println("to send: "+stcpPack.getTcpH().getTh_seq());				
		//发送数据报
		client.send(stcpPack);
	}
	
	@Override
	//需要修改
	public void waitACK() {
		//循环检查ackQueue
		//循环检查确认号对列中是否有新收到的ACK
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
	//接收到ACK报文：检查校验和，将确认号插入ack队列;NACK的确认号为－1；3.0版本不需要修改
	public void recv(TCP_PACKET recvPack) {
		if(CheckSum.computeChkSum(recvPack)==recvPack.getTcpH().getTh_sum()){
			sendWindow.recv(recvPack);
		}else{
			System.out.println("接收到的ack出错");
		}
	}


	
}
