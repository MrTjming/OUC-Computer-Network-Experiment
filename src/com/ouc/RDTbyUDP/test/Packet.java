package com.ouc.RDTbyUDP.test;

import java.io.Serializable;
import java.util.Arrays;
import java.util.zip.CRC32;

public class Packet implements Serializable {
    public static int MAX_PACKET_LENGTH=1300;//1440
    byte[] data;
    int sequenceNumber;
    boolean isEnd;
    int ACK;
    long checkSum;

    public Packet(byte[] data, int sequenceNumber) {
        this(data,sequenceNumber,false);
    }

    public Packet(byte[] data, int sequenceNumber, boolean isEnd) {
        this.data = data;
        this.sequenceNumber = sequenceNumber;
        this.isEnd = isEnd;
        this.checkSum = calcCheckSum();

    }

    public Packet(int sequenceNumber, int ACK) {
        this.sequenceNumber = sequenceNumber;
        this.ACK = ACK;
    }

    public byte[] getData() {
        return data;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public boolean isEnd() {
        return isEnd;
    }

    public int getACK() {
        return ACK;
    }

    public long getCheckSum() {
        return checkSum;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public void setEnd(boolean end) {
        isEnd = end;
    }

    public void setACK(int ACK) {
        this.ACK = ACK;
    }

    public void setCheckSum(long checkSum) {
        this.checkSum = checkSum;
    }

    public long calcCheckSum(){
        CRC32 crc=new CRC32();
        crc.update(sequenceNumber);
        if(data!=null){
            for (int i = 0; i <data.length ; i++) {
                crc.update(data[i]);
            }
        }

        return crc.getValue();
    }
}
