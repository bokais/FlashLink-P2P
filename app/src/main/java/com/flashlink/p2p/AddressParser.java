package com.flashlink.p2p;

import java.net.*;
import java.util.regex.Pattern;

/**
 * 域名地址码解析器 —— 支持 域名/IPv4/IPv6 地址码，处理各种"特殊地址"。
 *
 * 支持的地址码格式：
 *   example.com:12345         域名 + 端口
 *   192.0.2.1:12345           IPv4 + 端口（RFC 5737 文档示例地址）
 *   example.com:abc:12345     域名 + 自定义标识 + 端口（取最后一段为端口）
 *   [2001:db8::1]:8088        IPv6 + 端口（方括号，RFC 3849 文档示例地址）
 *   http://example.com:8088   带协议前缀（自动剥离）
 *   example.com:8088/path     带路径（自动剥离）
 */
public final class AddressParser {

    public static class Parsed {
        public String host;      // 解析后的主机名或IP文本
        public String original;  // 原始输入
        public int port;
        public boolean isDomain;
        public InetAddress address; // DNS解析后的地址（可能为IPv4或IPv6）
    }

    private static final Pattern DOMAIN = Pattern.compile(
            "^(?=.{1,253}$)([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$");

    /**
     * 解析地址码。第2段若是端口前的"特殊标识"（非数字），忽略它。
     * 规则：取最后一段冒号后的数字作为端口；之前的部分拼回主机。
     */
    public static Parsed parse(String input) throws Exception {
        if (input == null || input.trim().isEmpty()) throw new Exception("地址码为空");
        String s = input.trim();

        // 1. 剥离协议前缀
        if (s.startsWith("http://")) s = s.substring(7);
        else if (s.startsWith("https://")) s = s.substring(8);
        else if (s.startsWith("udp://")) s = s.substring(6);

        // 2. 剥离路径 / 查询串
        int slash = s.indexOf('/');
        if (slash > 0) s = s.substring(0, slash);
        int q = s.indexOf('?');
        if (q > 0) s = s.substring(0, q);

        Parsed p = new Parsed();
        p.original = input.trim();

        // 3. IPv6 [addr]:port 格式
        if (s.startsWith("[")) {
            int close = s.indexOf(']');
            if (close < 0) throw new Exception("IPv6地址缺少 ]");
            String ip = s.substring(1, close);
            String rest = s.substring(close + 1);
            if (!rest.startsWith(":")) throw new Exception("IPv6端口格式错误");
            p.host = ip;
            p.port = parseInt(rest.substring(1), "端口");
            p.isDomain = false;
            p.address = InetAddress.getByName(ip);
            return p;
        }

        // 4. 普通：取最后一段作为端口
        int lastColon = s.lastIndexOf(':');
        if (lastColon < 0) throw new Exception("地址码缺少端口");

        String hostPart = s.substring(0, lastColon);
        String portPart = s.substring(lastColon + 1);
        p.port = parseInt(portPart, "端口");
        if (p.port < 1 || p.port > 65535) throw new Exception("端口超出范围");

        // 去掉 hostPart 里可能的多余"特殊段"：用冒号分割，取最后一段作为主机
        // 例如 abc:example.com:12345 -> host=example.com
        if (hostPart.contains(":")) {
            String[] segs = hostPart.split(":");
            hostPart = segs[segs.length - 1];
        }
        p.host = hostPart;

        // 5. 判断是否域名
        p.isDomain = DOMAIN.matcher(hostPart).matches()
                || (!isIPv4(hostPart) && !hostPart.matches("[0-9.]+"));

        // 6. DNS 解析（域名或 IP 文本都交给 InetAddress）
        try {
            p.address = InetAddress.getByName(hostPart);
        } catch (UnknownHostException e) {
            throw new Exception("域名解析失败：" + hostPart);
        }
        return p;
    }

    private static int parseInt(String s, String what) throws Exception {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            throw new Exception(what + "不是数字：" + s);
        }
    }

    private static boolean isIPv4(String s) {
        String[] p = s.split("\\.");
        if (p.length != 4) return false;
        for (String x : p) {
            try {
                int v = Integer.parseInt(x);
                if (v < 0 || v > 255) return false;
            } catch (Exception e) { return false; }
        }
        return true;
    }
}