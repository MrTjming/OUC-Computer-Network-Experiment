package com.ouc.RDTbyUDP.test;
import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Receiver implements Runnable {
    class ClientAddr{
        InetAddress addr;
        int port;

        public ClientAddr(InetAddress addr, int port) {
            this.addr = addr;
            this.port = port;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ClientAddr that = (ClientAddr) o;
            return port == that.port &&
                    Objects.equals(addr, that.addr);
        }

        @Override
        public int hashCode() {
            return Objects.hash(addr, port);
        }
    }
    int listen_port;
    InetAddress host_addr;
    int backlog;
    Map<ClientAddr,ReceiveWindow> client_map;

    private static DatagramSocket socket = null;

    public Receiver(int listen_port, InetAddress host_addr) throws SocketException {
        this.listen_port = listen_port;
        this.host_addr = host_addr;
        backlog=1;
        client_map=new HashMap<>();
        socket = new DatagramSocket(listen_port);// �����˿�
    }

    public static void main(String[] args) throws UnknownHostException, SocketException {
        Receiver receiver=new Receiver(8081,InetAddress.getByName("localhost"));
        new Thread(receiver).start();
    }

    @Override
    public void run() {
        byte[] data_arriving = new byte[2880];
        DatagramPacket data_packet = new DatagramPacket(data_arriving, data_arriving.length);
        while (true){
            try {
                socket.receive(data_packet);
                InetAddress dest = data_packet.getAddress();
                int targetPort=data_packet.getPort();
                ClientAddr addr=new ClientAddr(dest,targetPort);
                ReceiveWindow window;

                
                // �ж��Ƿ������ӳ���
                if(client_map.containsKey(addr)){
                    // �ÿͻ��˵����Ӵ���,ʹ��ԭ�д���
                    window=client_map.get(addr);
                }else{
                    // �ÿͻ��˵����Ӳ�����,�½�һ������
                    window=new ReceiveWindow();
                    client_map.put(addr,window);
                }

                Packet recvPacket = (Packet) new ObjectInputStream(new ByteArrayInputStream(data_packet.getData())).readObject();
                if(recvPacket.getCheckSum()==recvPacket.calcCheckSum()){
                    int seq=window.addRecvPacket(recvPacket);
                    // todo:����ack
                    Packet packet=new Packet(null,seq);
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    ObjectOutputStream os;
                    os = new ObjectOutputStream(outputStream);
                    os.writeObject(packet);
                    byte[] data = outputStream.toByteArray();
                    DatagramPacket sendPacket = new DatagramPacket(data, data.length, dest, targetPort);
                    socket.send(sendPacket);

                }else{
                    System.out.println("У��Ͳ�ƥ��,���䷢�ʹ���");
                }

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }


    }
}
