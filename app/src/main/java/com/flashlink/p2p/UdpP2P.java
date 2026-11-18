package com.flashlink.p2p;

import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * UDP 打洞 P2P 文件传输（B1）。
 * 双方各自向对方公网地址发包打洞，然后用可靠的停等式协议传文件。
 * 文件内容不经过任何服务器。
 */
public final class UdpP2P {

    private static final int MTU = 1200;
    private static final int SENDER = 1;
    private static final int RECEIVER = 2;

    public interface Callback {
        void onStatus(String s);
        void onProgress(long done, long total);
        void onDone(boolean ok, String msg);
    }

    /** 发送方：socket 已由外部创建并保持（映射存活），连对方地址后发文件 */
    public static void send(DatagramSocket sock, InetAddress peerIp, int peerPort,
                            File file, Callback cb) {
        try {
            cb.onStatus("打洞中…");
            // 打洞：连发若干握手包
            byte[] hello = ("FLP2P-HELLO").getBytes("UTF-8");
            for (int i = 0; i < 10; i++) {
                sock.send(new DatagramPacket(hello, hello.length, peerIp, peerPort));
                Thread.sleep(100);
            }

            sock.setSoTimeout(15000);
            // 等待接收方 READY
            byte[] buf = new byte[2048];
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);
            boolean ready = false;
            for (int i = 0; i < 30; i++) {
                try {
                    sock.receive(pkt);
                    if (new String(pkt.getData(), pkt.getOffset(), pkt.getLength(), "UTF-8").startsWith("FLP2P-READY")) {
                        ready = true; break;
                    }
                } catch (SocketTimeoutException ignored) {}
            }
            if (!ready) { cb.onDone(false, "打洞失败：对方未回应（对称NAT可能导致）"); return; }

            cb.onStatus("已连通，开始发送…");
            long total = file.length();
            byte[] readyAck = "FLP2P-GO".getBytes("UTF-8");
            sock.send(new DatagramPacket(readyAck, readyAck.length, peerIp, peerPort));

            // 发文件头：文件名长度+名称+大小
            String name = file.getName();
            byte[] nb = name.getBytes("UTF-8");
            ByteBuffer hdr = ByteBuffer.allocate(8 + nb.length).order(ByteOrder.BIG_ENDIAN);
            hdr.putInt(nb.length).put(nb).putLong(total);
            sendReliable(sock, peerIp, peerPort, hdr.array(), cb);

            // 流式发送，seq 从 1 开始
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[MTU - 8];
            long sent = 0;
            int seq = 1;
            int n;
            while ((n = fis.read(data)) != -1) {
                ByteBuffer p = ByteBuffer.allocate(8 + n).order(ByteOrder.BIG_ENDIAN);
                p.putInt(seq++).put(data, 0, n);
                sendReliable(sock, peerIp, peerPort, p.array(), cb);
                sent += n;
                cb.onProgress(sent, total);
            }
            fis.close();
            cb.onDone(true, "发送完成：" + name);
        } catch (Exception e) {
            cb.onDone(false, "发送失败：" + e.getMessage());
        }
    }

    /** 可靠发送一个包：发 ACK 等待确认，超时重传 */
    private static void sendReliable(DatagramSocket sock, InetAddress ip, int port,
                                     byte[] payload, Callback cb) throws Exception {
        int seq = ByteBuffer.wrap(payload, 0, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        byte[] ackExpect = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putInt(-seq).array();
        byte[] buf = new byte[64];
        for (int retry = 0; retry < 25; retry++) {
            sock.send(new DatagramPacket(payload, payload.length, ip, port));
            DatagramPacket ap = new DatagramPacket(buf, buf.length);
            try {
                sock.receive(ap);
                if (ap.getLength() >= 8) {
                    int a = ByteBuffer.wrap(ap.getData(), 0, 8).order(ByteOrder.BIG_ENDIAN).getInt();
                    if (a == -seq) return;
                }
            } catch (SocketTimeoutException ignored) {}
        }
        throw new Exception("重传超限，连接不稳定");
    }

    /** 接收方：socket 已绑定并保持映射，等待对方打洞并接收 */
    public static void receive(DatagramSocket sock, File saveDir, Callback cb) {
        try {
            cb.onStatus("等待发送方连接…");
            sock.setSoTimeout(60000);
            byte[] buf = new byte[MTU + 64];
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);
            InetAddress peer = null; int peerPort = 0;

            // 等待 HELLO
            while (true) {
                sock.receive(pkt);
                String s = new String(pkt.getData(), pkt.getOffset(), pkt.getLength(), "UTF-8");
                if (s.startsWith("FLP2P-HELLO")) {
                    peer = pkt.getAddress(); peerPort = pkt.getPort();
                    break;
                }
            }
            cb.onStatus("发现发送方，回应中…");
            byte[] ready = "FLP2P-READY".getBytes("UTF-8");
            // 持续发 READY，直到收到 GO
            sock.setSoTimeout(2000);
            byte[] rbuf = new byte[64];
            boolean go = false;
            for (int i = 0; i < 60; i++) {
                sock.send(new DatagramPacket(ready, ready.length, peer, peerPort));
                DatagramPacket gp = new DatagramPacket(rbuf, rbuf.length);
                try {
                    sock.receive(gp);
                    if (new String(gp.getData(), 0, gp.getLength(), "UTF-8").startsWith("FLP2P-GO")) {
                        go = true; break;
                    }
                } catch (SocketTimeoutException ignored) {}
            }
            if (!go) { cb.onDone(false, "未收到开始信号"); return; }

            cb.onStatus("接收中…");
            sock.setSoTimeout(15000);

            // 收文件头
            byte[] hdr = recvReliable(sock, 0, cb);
            ByteBuffer hb = ByteBuffer.wrap(hdr).order(ByteOrder.BIG_ENDIAN);
            int nameLen = hb.getInt();
            byte[] nb = new byte[nameLen]; hb.get(nb);
            long total = hb.getLong();
            String name = new String(nb, "UTF-8").replace("/", "_").replace("\\", "_");
            if (!saveDir.exists()) saveDir.mkdirs();
            File out = new File(saveDir, name);
            FileOutputStream fos = new FileOutputStream(out);

            long received = 0;
            int seq = 1;
            while (received < total) {
                byte[] data = recvReliable(sock, seq, cb);
                if (data.length > 8) {
                    fos.write(data, 8, data.length - 8);
                    received += data.length - 8;
                    cb.onProgress(received, total);
                }
                seq++;
            }
            fos.flush(); fos.close();
            cb.onDone(true, "已保存：" + out.getAbsolutePath());
        } catch (Exception e) {
            cb.onDone(false, "接收失败：" + e.getMessage());
        }
    }

    /** 可靠接收包：收到后回 ACK(-seq) */
    private static byte[] recvReliable(DatagramSocket sock, int expectSeq, Callback cb) throws Exception {
        byte[] buf = new byte[MTU + 64];
        while (true) {
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            sock.receive(p);
            byte[] data = Arrays.copyOfRange(p.getData(), p.getOffset(), p.getOffset() + p.getLength());
            if (data.length < 4) continue;
            int seq = ByteBuffer.wrap(data, 0, 4).order(ByteOrder.BIG_ENDIAN).getInt();
            // 头包 seq 可以是名称长度，用特殊标记：接收方对头用 expectSeq=0 表示接受任意第一个包
            if (expectSeq == 0 || seq == expectSeq) {
                byte[] ack = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putInt(-seq).array();
                sock.send(new DatagramPacket(ack, 8, p.getAddress(), p.getPort()));
                return data;
            } else {
                // 回上一个包的 ACK，促使其重发
                byte[] ack = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putInt(-(seq - 1)).array();
                sock.send(new DatagramPacket(ack, 8, p.getAddress(), p.getPort()));
            }
        }
    }
}