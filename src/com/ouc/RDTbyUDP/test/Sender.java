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
        byte[] data= Files.readAllBytes(Paths.get("E:\\极客时间\\极客时间：趣谈网络协议（完结）.pdf"));
//        byte[] data= Files.readAllBytes(Paths.get("D:\\Users\\Tjm\\Desktop\\高英展示整合.pptx"));
        System.out.println("data length is:"+data.length);
        sender.addData(data);
        System.out.println("list size is:"+sender.send_list.size());
        new Thread(sender).start();
    }

    @Override
    public void run() {
        Scanner sc=new Scanner(System.in);
        // 1.建立滑动窗口
        window=new SendWindow(socket,target_addr,targetPort);

        // 2.发送首个包
        sendIndex =1;
        window.RdtSend(send_list.get(0));

        // 3.开启线程自动发送剩余的包
        sendData();
//        window.autoSendPacket();

        // 4.处理滑动窗口中的超时包
        window.dealWithOvertime();

        // 5.开启线程处理ACK
        autoRecvPacket();
//        window.autoRecvPacket();

    }



    public boolean addData(byte[] data){
        if(send_data==null){
            send_data=data;
            dealWithData();
            return true;
        }
        System.out.println("加入数据失败");
        return false;
    }


    private boolean dealWithData() {
        // 如果之前还有内容的话,清空最后一个窗口的end标记
        int index = 0;
        int dataLength = send_data.length;
        while (index<send_data.length) {
            // 计算该包的大小
            int packetSize = Math.min(send_data.length - index , Packet.MAX_PACKET_LENGTH);
            byte[] output_byte = new byte[packetSize];
            System.arraycopy(send_data, index, output_byte, 0, packetSize);

            // 生成一个窗口块并加入到列表

            send_list.add(new Packet(output_byte,sequenceNumber));
            sequenceNumber+=packetSize;
            index += packetSize;
        }

        // 给最后一个包设置end标记
        send_list.get(send_list.size() - 1).setEnd(true);
        for (int i = 0; i <send_list.size() ; i++) {
            System.out.println("第"+i+"个是"+send_list.get(i).isEnd()+" "+send_list.get(i).getSequenceNumber());
        }
        return true;
    }

//    // 发送剩余的包
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
                    System.out.printf("发送第%d个包\n",sendIndex+1);
                    window.RdtSend(send_list.get(sendIndex++));
                }
                System.out.println("发送完成");
            }
        }).start();

    }
//
//    // 处理滑动窗口中的超时包
//    public void dealWithOvertime(){
//        TimerTask dealOverTime = new TimerTask() {
//            @Override
//            public void run() {
//                int index = startWindowIndex;
//                Window window;
//                while (index <= ackWindowIndex) {
//                    // 如果第index个包超时了
//                    window = sendContent.get(index);
//                    if (TIMEOUTTIME < (System.currentTimeMillis() - window.getStartSendTime())) {
//                        //  它没有收到ack,则尝试重发
//                        if (!window.isAck()) {
//                            logger.warning(getWindowInfo()+"超时重发包:");
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
                        System.out.println("收到延迟包seq:"+recvPacket.getSequenceNumber()+" "+recvPacket.getSequenceNumber()/Packet.MAX_PACKET_LENGTH);
                        continue;
                    }
                    lastSeq=Math.max(recvPacket.getSequenceNumber(),lastSeq);
                    System.out.println("收到第"+window.indexMap.get(lastSeq)+"个包的ack");
                    window.recv(recvPacket);
                }else{
                    System.out.println("校验码不匹配,传输出现错误");
                }

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        System.out.println("所有包已被ack");
    }



}
