package com.flashlink.p2p;

import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * STUN 客户端 —— 实测可用（拿到真实公网映射，非CGNAT）。
 * 仅用 STUN 查询地址，文件传输完全 P2P 直连，不经服务器。
 */
public final class StunClient {

    public static final String GOOGLE_STUN = "stun.l.google.com";
    private static final int MAGIC = 0x2112A442;

    public static class Result {
        public InetAddress publicIp;
        public int publicPort;
        public String localIp;
        public int localPort;
        public String toString() { return publicIp.getHostAddress() + ":" + publicPort; }
    }

    public static Result query(DatagramSocket sock, String host, int port, int timeoutMs)
            throws Exception {
        byte[] req = new byte[20];
        req[0] = 0x00; req[1] = 0x01;
        ByteBuffer.wrap(req, 4, 4).order(ByteOrder.BIG_ENDIAN).putInt(MAGIC);
        byte[] tx = new byte[12];
        new java.util.Random().nextBytes(tx);
        System.arraycopy(tx, 0, req, 8, 12);

        sock.setSoTimeout(timeoutMs);
        sock.send(new DatagramPacket(req, req.length, InetAddress.getByName(host), port));

        byte[] buf = new byte[1024];
        DatagramPacket resp = new DatagramPacket(buf, buf.length);
        sock.receive(resp);
        byte[] d = java.util.Arrays.copyOf(buf, resp.getLength());
        if (d.length < 20) throw new Exception("STUN 响应过短");

        int type = ((d[0] & 255) << 8) | (d[1] & 255);
        if (type != 0x0101) throw new Exception("STUN 失败响应: 0x" + Integer.toHexString(type));

        for (int i = 20; i + 4 <= d.length; ) {
            int attrType = ((d[i] & 255) << 8) | (d[i + 1] & 255);
            int attrLen = ((d[i + 2] & 255) << 8) | (d[i + 3] & 255);
            if (attrType == 0x0020 && attrLen >= 8) {
                int p = (((d[i + 6] & 255) << 8) | (d[i + 7] & 255)) ^ 0x2112;
                byte[] ip = new byte[4];
                for (int k = 0; k < 4; k++) {
                    ip[k] = (byte) ((d[i + 8 + k] ^ ((MAGIC >>> (24 - 8 * k)) & 255)) & 255);
                }
                Result r = new Result();
                r.publicIp = InetAddress.getByAddress(ip);
                r.publicPort = p;
                r.localIp = sock.getLocalAddress().getHostAddress();
                r.localPort = sock.getLocalPort();
                return r;
            }
            i += 4 + attrLen;
        }
        throw new Exception("STUN 响应无 XOR-MAPPED-ADDRESS");
    }
}