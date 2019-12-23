/***************************2.1: ACK/NACK*****************/
/***** Feng Hong; 2015-12-09******************************/
package com.ouc.tcp.RDT4_0;

import com.ouc.tcp.client.TCP_Receiver_ADT;
import com.ouc.tcp.message.TCP_PACKET;

public class TCP_Receiver extends TCP_Receiver_ADT {
	
	private TCP_PACKET ackPack;	//�ظ���ACK���Ķ�
	int sequence = 1;//���ڼ�¼��ǰ�����յİ���ţ�ע�����Ų���ȫ��
	int count = 0;
	ReceiveWindow window;
		
	/*���캯��*/
	public TCP_Receiver() {
		super();	//���ó��๹�캯��
		super.initTCP_Receiver(this);	//��ʼ��TCP���ն�
		window=new ReceiveWindow(client);
	}

	@Override
	//���յ����ݱ������У��ͣ����ûظ���ACK���Ķ�
	public void rdt_recv(TCP_PACKET recvPack) {
				
		//���У���룬����ACK		
		if(CheckSum.computeChkSum(recvPack) == recvPack.getTcpH().getTh_sum()) {
			//����ACK���ĶΣ�����ȷ�Ϻţ�
			tcpH.setTh_ack(recvPack.getTcpH().getTh_seq());
			ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
			tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
			ackPack.setTcpH(tcpH);
			//�ظ�ACK���Ķ�
			reply(ackPack);

			// �����ݼӵ�������
			window.addRecvPacket(recvPack);

		}else{
			System.out.println("У��Ͳ�ƥ��,�������");
		}

		//�������ݣ�ÿ20�����ݽ���һ�Σ�
//		if(dataQueue.size() == 20)
//			deliver_data();
	}

	@Override
	//�������ݣ�������д���ļ���������Ҫ�޸�
	public void deliver_data() {
//		//���dataQueue��������д���ļ�
//		File fw = new File("recvData.txt");
//		BufferedWriter writer;
//
//
//		try {
//			writer = new BufferedWriter(new FileWriter(fw, true));
//
//			//ѭ�����data�������Ƿ����½�������
//			while(!dataQueue.isEmpty()) {
//				int[] data = dataQueue.poll();
//
//				if (count == 0 ){
//					SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//�������ڸ�ʽ
//					String date = df.format(new Date());// new Date()Ϊ��ȡ��ǰϵͳʱ�䣬Ҳ��ʹ�õ�ǰʱ���
//					writer.write("start: "+date+"\n");
//
//				}
//
//				//������д���ļ�
//				for(int i = 0; i < data.length; i++) {
//					writer.write(data[i] + "\n");
//				}
//				count = count + data.length;
//
//				if (count==100000){//100000
//					SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//�������ڸ�ʽ
//					String date = df.format(new Date());// new Date()Ϊ��ȡ��ǰϵͳʱ�䣬Ҳ��ʹ�õ�ǰʱ���
//					writer.write("end: "+date+"\n");
//
//				}
//
//
//				writer.flush();		//����������
//			}
//			writer.close();
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}

	@Override
	//�ظ�ACK���Ķ�,����Ҫ�޸�
	public void reply(TCP_PACKET replyPack) {
		//���ô�����Ʊ�־
		tcpH.setTh_eflag((byte)7);	//eFlag=0���ŵ��޴���
				
		//�������ݱ�
		client.send(replyPack);
	}
	
	
	
}
