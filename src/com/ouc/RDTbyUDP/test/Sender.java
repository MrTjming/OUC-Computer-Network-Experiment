package com.ouc.RDTbyUDP.test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class Sender implements Runnable {
    String targetIp="47.103.26.159";
    InetAddress target_addr;
    int targetPort;
    int sourcePort;
    SendWindow window;
    byte[] send_data=null;
    List<Packet> send_list;
    int sequenceNumber;
    int sendIndex;
    DatagramSocket socket;

    public Sender(InetAddress target_addr, int targetPort,int sourcePort) throws SocketException {
        this.target_addr = target_addr;
        this.targetPort = targetPort;
        this.sourcePort= sourcePort;
        socket=new DatagramSocket(sourcePort);
        send_list=new ArrayList<>();
    }

    public static void main(String[] args) throws IOException {
        Sender sender=new Sender(InetAddress.getByName("47.103.26.159"),8081,8080);
        byte[] data= Files.readAllBytes(Paths.get("E:\\����ʱ��\\����ʱ�䣺Ȥ̸����Э�飨��ᣩ.pdf"));
//        byte[] data= Files.readAllBytes(Paths.get("D:\\Users\\Tjm\\Desktop\\��Ӣչʾ����.pptx"));
        System.out.println("data length is:"+data.length);
        sender.addData(data);
        System.out.println("list size is:"+sender.send_list.size());
        new Thread(sender).start();
    }

    @Override
    public void run() {
        Scanner sc=new Scanner(System.in);
        // 1.������������
        window=new SendWindow(socket,target_addr,targetPort);

        // 2.�����׸���
        sendIndex =1;
        window.RdtSend(send_list.get(0));

        // 3.�����߳��Զ�����ʣ��İ�
        sendData();
//        window.autoSendPacket();

        // 4.�����������еĳ�ʱ��
        window.dealWithOvertime();

        // 5.�����̴߳���ACK
        autoRecvPacket();
//        window.autoRecvPacket();

    }



    public boolean addData(byte[] data){
        if(send_data==null){
            send_data=data;
            dealWithData();
            return true;
        }
        System.out.println("��������ʧ��");
        return false;
    }


    private boolean dealWithData() {
        // ���֮ǰ�������ݵĻ�,������һ�����ڵ�end���
        int index = 0;
        int dataLength = send_data.length;
        while (index<send_data.length) {
            // ����ð��Ĵ�С
            int packetSize = Math.min(send_data.length - index , Packet.MAX_PACKET_LENGTH);
            byte[] output_byte = new byte[packetSize];
            System.arraycopy(send_data, index, output_byte, 0, packetSize);

            // ����һ�����ڿ鲢���뵽�б�

            send_list.add(new Packet(output_byte,sequenceNumber));
            sequenceNumber+=packetSize;
            index += packetSize;
        }

        // �����һ��������end���
        send_list.get(send_list.size() - 1).setEnd(true);
        for (int i = 0; i <send_list.size() ; i++) {
            System.out.println("��"+i+"����"+send_list.get(i).isEnd()+" "+send_list.get(i).getSequenceNumber());
        }
        return true;
    }

//    // ����ʣ��İ�
    public void sendData(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (sendIndex<send_list.size()){
                    while (!window.continueSend()){
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    System.out.printf("���͵�%d����\n",sendIndex+1);
                    window.RdtSend(send_list.get(sendIndex++));
                }
                System.out.println("�������");
            }
        }).start();

    }
//
//    // �����������еĳ�ʱ��
//    public void dealWithOvertime(){
//        TimerTask dealOverTime = new TimerTask() {
//            @Override
//            public void run() {
//                int index = startWindowIndex;
//                Window window;
//                while (index <= ackWindowIndex) {
//                    // �����index������ʱ��
//                    window = sendContent.get(index);
//                    if (TIMEOUTTIME < (System.currentTimeMillis() - window.getStartSendTime())) {
//                        //  ��û���յ�ack,�����ط�
//                        if (!window.isAck()) {
//                            logger.warning(getWindowInfo()+"��ʱ�ط���:");
//                            sendWindow(sendContent.get(index),false);
//                            break;
//                        }else{
//                            startWindowIndex=index+1;
//                        }
//                    }
//                    index++;
//                }
//            }
//        };
//        new Timer().schedule(dealOverTime, 0, 1000);
//    }

    public void autoRecvPacket(){
        int RECV_DATA_SIZE = 2880;
        DatagramPacket dataPacket = new DatagramPacket(new byte[RECV_DATA_SIZE], RECV_DATA_SIZE);
        int lastSeq=-1;
        while (lastSeq<sequenceNumber){
            try {
                socket.receive(dataPacket);
                byte[] data = dataPacket.getData();
                ObjectInputStream msg = new ObjectInputStream(new ByteArrayInputStream(data));
                Packet recvPacket = (Packet) msg.readObject();
                if(recvPacket.getCheckSum()==recvPacket.calcCheckSum()){
                    if(recvPacket.getSequenceNumber()<lastSeq){
                        System.out.println("�յ��ӳٰ�seq:"+recvPacket.getSequenceNumber()+" "+recvPacket.getSequenceNumber()/Packet.MAX_PACKET_LENGTH);
                        continue;
                    }
                    lastSeq=Math.max(recvPacket.getSequenceNumber(),lastSeq);
                    System.out.println("�յ���"+window.indexMap.get(lastSeq)+"������ack");
                    window.recv(recvPacket);
                }else{
                    System.out.println("У���벻ƥ��,������ִ���");
                }

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        System.out.println("���а��ѱ�ack");
    }



}
