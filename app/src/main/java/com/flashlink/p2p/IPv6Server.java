package com.flashlink.p2p;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * IPv6 直连 HTTP 服务（B4）。
 * 绑定全球单播 IPv6 地址，生成 http://[2001:db8::1]:8088/d 链接（文档示例地址）。
 * IPv6 无 NAT，对方可直接访问（前提是运营商放行入站 + 对方也有 IPv6）。
 */
public class IPv6Server {

    private static final String TAG = "IPv6Server";
    public static final int PORT = 8088;

    private final Context context;
    private ServerSocket server;
    private ExecutorService pool;
    private volatile boolean running = false;

    private Uri fileUri;
    private String fileName;
    private long fileSize;

    public interface Callback {
        void onServed(long bytes, String name);
        void onError(String msg);
    }

    private Callback callback;

    public IPv6Server(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setCallback(Callback cb) { this.callback = cb; }

    public void setFile(Uri uri, String name, long size) {
        this.fileUri = uri;
        this.fileName = name;
        this.fileSize = size;
    }

    /** 枚举全球单播 IPv6 地址（240x/200x/2a0x 等，排除 fe80/::1） */
    public static List<String> getGlobalIPv6() {
        List<String> result = new ArrayList<String>();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces != null && ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet6Address) {
                        String h = a.getHostAddress();
                        // 去掉 %接口后缀
                        int pct = h.indexOf('%');
                        if (pct > 0) h = h.substring(0, pct);
                        String lower = h.toLowerCase();
                        if (lower.equals("::1") || lower.startsWith("fe80")) continue;
                        if (lower.startsWith("fc") || lower.startsWith("fd")) continue; // ULA
                        // 全球单播通常以 2 或 3 开头
                        if (lower.matches("^[23][0-9a-f].*")) result.add(h);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getGlobalIPv6", e);
        }
        return result;
    }

    /** 启动服务，绑定到指定 IPv6 地址；地址为空则绑定所有 */
    public String start(String bindIPv6) throws IOException {
        stop();
        if (bindIPv6 != null && !bindIPv6.isEmpty()) {
            server = new ServerSocket();
            server.bind(new InetSocketAddress(bindIPv6, PORT));
        } else {
            // 绑定 :: 同时接受 IPv4/IPv6
            server = new ServerSocket(PORT, 16, InetAddress.getByName("::"));
        }
        pool = Executors.newFixedThreadPool(6);
        running = true;

        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                while (running && !server.isClosed()) {
                    try {
                        final Socket c = server.accept();
                        pool.execute(new Runnable() {
                            @Override public void run() { handle(c); }
                        });
                    } catch (IOException e) {
                        if (running) Log.e(TAG, "accept", e);
                    }
                }
            }
        }, "IPv6-Accept");
        t.setDaemon(true);
        t.start();
        Log.i(TAG, "IPv6 server started on " + (bindIPv6 == null ? "::" : bindIPv6));
        return makeUrl(bindIPv6);
    }

    public static String makeUrl(String ipv6) {
        if (ipv6 == null || ipv6.isEmpty()) ipv6 = "::1";
        return "http://[" + ipv6 + "]:" + PORT + "/d";
    }

    public void stop() {
        running = false;
        try { if (server != null && !server.isClosed()) server.close(); } catch (IOException ignored) {}
        server = null;
        if (pool != null) { pool.shutdownNow(); pool = null; }
    }

    public boolean isRunning() { return running; }

    private void handle(Socket client) {
        try {
            client.setSoTimeout(30000);
            InputStream in = client.getInputStream();
            ByteArrayOutputStream req = new ByteArrayOutputStream();
            int b;
            while ((b = in.read()) != -1) {
                req.write(b);
                byte[] d = req.toByteArray();
                int len = d.length;
                if (len >= 4 && d[len-4]=='\r' && d[len-3]=='\n' && d[len-2]=='\r' && d[len-1]=='\n') break;
            }
            String line = req.toString("ISO-8859-1").split("\r\n")[0];
            String path = line.split(" ")[1];
            if (path.startsWith("/ping")) {
                sendText(client, "OK");
            } else if (path.startsWith("/d") || path.equals("/")) {
                serveFile(client);
            } else {
                sendText(client, "404");
            }
        } catch (Exception e) {
            Log.e(TAG, "handle", e);
        } finally {
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    private void serveFile(Socket client) throws IOException {
        if (fileUri == null) { sendText(client, "请先选择文件"); return; }
        String name = (fileName == null || fileName.isEmpty()) ? "download" : fileName;
        name = name.replace("\"", "").replace("\r", "").replace("\n", "");

        OutputStream os = new BufferedOutputStream(client.getOutputStream(), 64 * 1024);
        StringBuilder h = new StringBuilder();
        h.append("HTTP/1.1 200 OK\r\n");
        h.append("Content-Type: application/octet-stream\r\n");
        h.append("Content-Disposition: attachment; filename=\"").append(name).append("\"\r\n");
        h.append("Cache-Control: no-store\r\n");
        h.append("Connection: close\r\n");
        if (fileSize > 0) h.append("Content-Length: ").append(fileSize).append("\r\n");
        h.append("\r\n");
        os.write(h.toString().getBytes(StandardCharsets.US_ASCII));
        os.flush();

        InputStream is = context.getContentResolver().openInputStream(fileUri);
        if (is == null) { os.flush(); return; }
        byte[] buf = new byte[64 * 1024];
        long total = 0; int n;
        try {
            while ((n = is.read(buf)) != -1) { os.write(buf, 0, n); total += n; }
        } finally {
            try { is.close(); } catch (Exception ignored) {}
        }
        os.flush();
        if (callback != null) callback.onServed(total, name);
    }

    private void sendText(Socket client, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        String h = "HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
        OutputStream os = client.getOutputStream();
        os.write(h.getBytes(StandardCharsets.US_ASCII));
        os.write(body);
        os.flush();
    }
}