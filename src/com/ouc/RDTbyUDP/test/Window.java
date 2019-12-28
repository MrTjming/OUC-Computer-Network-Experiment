package com.ouc.RDTbyUDP.test;


class Window{
    boolean ack;
    long startSendTime;
    int duplicateAckNum;
    Packet packet;

    Window(Packet packet) {
        this.packet = packet;
    }

    boolean isAck() {
        return ack;
    }

    long getStartSendTime() {
        return startSendTime;
    }

    int getDuplicateAckNum() {
        return duplicateAckNum;
    }

    Packet getPacket() {
        return packet;
    }

    void setAck(boolean ack) {
        this.ack = ack;
    }

    void setStartSendTime(long startSendTime) {
        this.startSendTime = startSendTime;
    }

    void setDuplicateAckNum(int duplicateAckNum) {
        this.duplicateAckNum = duplicateAckNum;
    }

    public void setPacket(Packet packet) {
        this.packet = packet;
    }

}