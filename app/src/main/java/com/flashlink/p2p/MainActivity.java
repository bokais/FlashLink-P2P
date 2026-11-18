package com.flashlink.p2p;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.DatagramSocket;
import java.util.List;

/** 闪链 P2P —— B1 UDP打洞 + B4 IPv6直连，M3界面，零依赖 */
public class MainActivity extends Activity {

    private static final int REQ_PICK = 1001;

    private Uri selectedUri;
    private String selectedName;
    private long selectedSize;
    private TextView fileInfoText;

    private DatagramSocket p2pSocket;
    private TextView b1LocalCode;
    private EditText b1DomainInput;
    private EditText b1PeerCode;
    private TextView b1SaveDir;
    private String[] saveDirOptions = {"Download", "Download/FlashLink", "Documents", "Pictures", "Movies", "DCIM/Camera"};
    private int saveDirIndex = 0;
    private TextView b1Status;
    private TextView b1Progress;

    private IPv6Server ipv6Server;
    private TextView b4IpList;
    private EditText b4DomainInput;
    private TextView b4Url;
    private TextView b4Status;
    private Button b4StartBtn;
    private Button b4StopBtn;
    private Button b4CopyBtn;
    private String b4CurrentUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("闪链 P2P");
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(M3.SURFACE);
            getWindow().setNavigationBarColor(M3.SURFACE);
        }
        ipv6Server = new IPv6Server(this);
        ipv6Server.setCallback(new IPv6Server.Callback() {
            @Override public void onServed(final long bytes, final String name) {
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        Toast.makeText(MainActivity.this,
                                "已发送 " + name + " (" + formatSize(bytes) + ")",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override public void onError(final String msg) {
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
        buildUi();
        refreshIPv6();
        requestPerms();
    }

    private void buildUi() {
        int p = M3.dp(this, 20);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(M3.SURFACE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p, p, p, p);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(M3.rounded(M3.PRIMARY_CONTAINER, 24, this));
        header.setPadding(M3.dp(this, 20), M3.dp(this, 20), M3.dp(this, 20), M3.dp(this, 20));
        TextView title = M3.headline(this, "闪链 P2P");
        title.setTextColor(M3.ON_PRIMARY_CONTAINER);
        header.addView(title);
        TextView sub = M3.body(this, "远程直传 · 文件不过服务器 · Material 3");
        sub.setTextColor(M3.ON_PRIMARY_CONTAINER);
        header.addView(sub);
        root.addView(header);
        root.addView(M3.space(this, 16));

        LinearLayout fileCard = M3.card(this);
        fileCard.addView(M3.label(this, "传输文件"));
        fileInfoText = M3.body(this, "未选择文件");
        fileCard.addView(fileInfoText);
        Button pick = M3.outlinedButton(this, "选择文件");
        pick.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("*/*");
                i.addCategory(Intent.CATEGORY_OPENABLE);
                try { startActivityForResult(Intent.createChooser(i, "选择文件"), REQ_PICK); }
                catch (Exception e) { Toast.makeText(MainActivity.this, "无文件管理器", Toast.LENGTH_SHORT).show(); }
            }
        });
        fileCard.addView(pick);
        root.addView(fileCard);
        root.addView(M3.space(this, 16));

        root.addView(M3.label(this, "模式一 · UDP打洞 P2P（跨网络）"));
        root.addView(M3.space(this, 6));
        LinearLayout b1 = M3.card(this);
        b1.addView(M3.body(this, "① 点下方按钮获取自己的地址码，双方互换（微信/短信均可）"));
        Button getCode = M3.filledButton(this, "获取我的地址码");
        getCode.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { getMyCode(); }
        });
        b1.addView(getCode);
        b1LocalCode = M3.body(this, "我的地址码：未获取");
        b1LocalCode.setTextIsSelectable(true);
        b1LocalCode.setTextColor(M3.PRIMARY);
        b1.addView(b1LocalCode);
        b1.addView(M3.space(this, 6));
        b1.addView(M3.body(this, "可选：填你的DDNS域名，地址码会用域名代替IP"));
        b1DomainInput = M3.textField(this, "DDNS域名 例如 myname.ddns.net（留空用IP）");
        b1.addView(b1DomainInput);
        b1.addView(M3.space(this, 8));
        b1.addView(M3.body(this, "② 填入对方地址码，一方点接收待命，另一方点发送"));
        b1PeerCode = M3.textField(this, "对方地址码  例如 192.0.2.1:12345");
        b1.addView(b1PeerCode);
b1.addView(M3.body(this, "接收文件保存位置（默认 Download，点击切换）："));
        LinearLayout dirRow = new LinearLayout(this);
        dirRow.setOrientation(LinearLayout.HORIZONTAL);
        dirRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        b1SaveDir = M3.body(this, saveDirOptions[saveDirIndex]);
        b1SaveDir.setTextColor(M3.PRIMARY);
        b1SaveDir.setTextSize(15);
        b1SaveDir.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        dirRow.addView(b1SaveDir);
        Button switchDir = M3.outlinedButton(this, "切换");
        switchDir.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                saveDirIndex = (saveDirIndex + 1) % saveDirOptions.length;
                b1SaveDir.setText(saveDirOptions[saveDirIndex]);
            }
        });
        dirRow.addView(switchDir);
        b1.addView(dirRow);
        b1.addView(M3.space(this, 8));
        b1.addView(M3.space(this, 8));
        LinearLayout b1row = new LinearLayout(this);
        b1row.setOrientation(LinearLayout.HORIZONTAL);
        Button send = M3.filledButton(this, "发送文件");
        send.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        send.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { p2pSend(); }
        });
        Button recv = M3.outlinedButton(this, "接收文件");
        recv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        recv.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { p2pReceive(); }
        });
        b1row.addView(send);
        b1row.addView(recv);
        b1.addView(b1row);
        b1Status = M3.body(this, "状态：就绪");
        b1.addView(b1Status);
        b1Progress = M3.body(this, "");
        b1.addView(b1Progress);
        root.addView(b1);
        root.addView(M3.space(this, 16));

        root.addView(M3.label(this, "模式二 · IPv6直连（浏览器下载）"));
        root.addView(M3.space(this, 6));
        LinearLayout b4 = M3.card(this);
        b4.addView(M3.body(this, "本机全球IPv6地址："));
        b4IpList = M3.body(this, "检测中…");
        b4IpList.setTextIsSelectable(true);
        b4.addView(b4IpList);
        b4.addView(M3.space(this, 6));
        b4.addView(M3.body(this, "可选：填你的域名（AAAA记录指向上面IPv6），链接用域名代替方括号IP"));
        b4DomainInput = M3.textField(this, "域名 例如 file.example.com（留空用IPv6）");
        b4.addView(b4DomainInput);
        b4.addView(M3.space(this, 8));
        LinearLayout b4row = new LinearLayout(this);
        b4row.setOrientation(LinearLayout.HORIZONTAL);
        b4StartBtn = M3.filledButton(this, "启动服务");
        b4StartBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        b4StartBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { ipv6Start(); }
        });
        b4StopBtn = M3.outlinedButton(this, "停止");
        b4StopBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        b4StopBtn.setEnabled(false);
        b4StopBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { ipv6Stop(); }
        });
        b4row.addView(b4StartBtn);
        b4row.addView(b4StopBtn);
        b4.addView(b4row);
        b4Status = M3.body(this, "状态：未启动");
        b4.addView(b4Status);
        b4Url = M3.body(this, "下载链接：启动后生成");
        b4Url.setTextIsSelectable(true);
        b4Url.setTextColor(M3.PRIMARY);
        b4.addView(b4Url);
        b4CopyBtn = M3.outlinedButton(this, "复制链接");
        b4CopyBtn.setEnabled(false);
        b4CopyBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { copy(b4CurrentUrl); }
        });
        b4.addView(b4CopyBtn);
        root.addView(b4);
        root.addView(M3.space(this, 16));

        TextView tip = M3.body(this,
                "说明：\n"
                + "• 模式一：STUN仅交换地址，文件走UDP直连，不经任何服务器；双方需互换地址码；对称NAT可能失败。\n"
                + "• 模式二：IPv6无NAT，对方有IPv6且运营商放行入站时，浏览器打开链接即可下载。\n"
                + "• 传输期间请保持App在前台。");
        root.addView(tip);

        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PICK && res == RESULT_OK && data != null && data.getData() != null) {
            selectedUri = data.getData();
            String[] inf = fileInfo(selectedUri);
            selectedName = inf[0];
            selectedSize = Long.parseLong(inf[1]);
            fileInfoText.setText("已选：" + selectedName + "\n大小：" + formatSize(selectedSize));
        }
    }

    private String[] fileInfo(Uri uri) {
        String name = "download";
        long size = 0;
        Cursor c = getContentResolver().query(uri, null, null, null, null);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    int ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    int si = c.getColumnIndex(OpenableColumns.SIZE);
                    if (ni >= 0 && c.getString(ni) != null) name = c.getString(ni);
                    if (si >= 0) size = c.getLong(si);
                }
            } finally { c.close(); }
        }
        return new String[]{name, String.valueOf(size)};
    }

    private File copyToCache() throws Exception {
        if (selectedUri == null) throw new Exception("请先选择文件");
        File out = new File(getCacheDir(), selectedName);
        InputStream is = getContentResolver().openInputStream(selectedUri);
        FileOutputStream fos = new FileOutputStream(out);
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
        fos.close();
        is.close();
        return out;
    }

    private void getMyCode() {
        b1Status.setText("状态：查询公网地址中…");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    ensureP2pSocket();
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            b1Status.setText("状态：地址码已获取，请发给对方并互换");
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            b1Status.setText("状态：获取失败 - " + e.getMessage());
                        }
                    });
                }
            }
        }, "GetCode").start();
    }

    private void ensureP2pSocket() throws Exception {
        if (p2pSocket == null || p2pSocket.isClosed()) {
            p2pSocket = new DatagramSocket(0);
            final StunClient.Result r = StunClient.query(p2pSocket, StunClient.GOOGLE_STUN, 19302, 8000);
            // 若填了DDNS域名，地址码用域名:端口；否则用IP:端口
            final String domain = b1DomainInput != null ? b1DomainInput.getText().toString().trim() : "";
            final String host = domain.isEmpty() ? r.publicIp.getHostAddress() : domain;
            final String code = host + ":" + r.publicPort;
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    b1LocalCode.setText("我的地址码：" + code + "\n（发给对方，保持App前台）");
                }
            });
        }
    }

    private UdpP2P.Callback p2pCb = new UdpP2P.Callback() {
        @Override public void onStatus(final String s) {
            runOnUiThread(new Runnable() {
                @Override public void run() { b1Status.setText("状态：" + s); }
            });
        }
        @Override public void onProgress(final long done, final long total) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    b1Progress.setText("进度：" + formatSize(done) + " / " + formatSize(total));
                }
            });
        }
        @Override public void onDone(final boolean ok, final String msg) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    b1Status.setText((ok ? "完成：" : "失败：") + msg);
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            });
        }
    };

    private void p2pSend() {
        final String peer = b1PeerCode.getText().toString().trim();
        if (peer.isEmpty()) {
            Toast.makeText(this, "请先填入对方地址码", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedUri == null) {
            Toast.makeText(this, "请先选择文件", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    ensureP2pSocket();
                    File f = copyToCache();
                    final AddressParser.Parsed target = AddressParser.parse(peer);
                    final String ip = target.address.getHostAddress();
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            b1Status.setText("状态：解析 " + target.host + " -> " + ip + ":" + target.port);
                        }
                    });
                    UdpP2P.send(p2pSocket, target.address, target.port, f, p2pCb);
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() { b1Status.setText("状态：错误 - " + e.getMessage()); }
                    });
                }
            }
        }, "P2PSend").start();
    }

    private void p2pReceive() {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    ensureP2pSocket();
                    String selected = saveDirOptions[saveDirIndex];
                    File dir = new File(Environment.getExternalStorageDirectory(), selected);
                    if (!dir.exists()) dir.mkdirs();
                    UdpP2P.receive(p2pSocket, dir, p2pCb);
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() { b1Status.setText("状态：错误 - " + e.getMessage()); }
                    });
                }
            }
        }, "P2PRecv").start();
    }

    private void refreshIPv6() {
        List<String> ips = IPv6Server.getGlobalIPv6();
        StringBuilder sb = new StringBuilder();
        if (ips.isEmpty()) {
            sb.append("未检测到全球IPv6地址\n（本模式不可用，请用模式一）");
        } else {
            for (String ip : ips) sb.append("• ").append(ip).append("\n");
        }
        b4IpList.setText(sb.toString().trim());
    }

    private void ipv6Start() {
        if (selectedUri == null) {
            Toast.makeText(this, "请先选择文件", Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> ips = IPv6Server.getGlobalIPv6();
        if (ips.isEmpty()) {
            Toast.makeText(this, "无全球IPv6地址，请用模式一", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            ipv6Server.setFile(selectedUri, selectedName, selectedSize);
            // 服务始终绑定本机IPv6
            ipv6Server.start(ips.get(0));
            // 生成链接：填了域名用域名（无方括号），否则用IPv6（带方括号）
            String domain = b4DomainInput.getText().toString().trim();
            if (!domain.isEmpty()) {
                domain = domain.replace("http://", "").replace("https://", "");
                int slash = domain.indexOf('/');
                if (slash > 0) domain = domain.substring(0, slash);
                b4CurrentUrl = "http://" + domain + ":" + IPv6Server.PORT + "/d";
                b4Status.setText("状态：运行中（域名链接，AAAA需指向 " + ips.get(0) + "）");
            } else {
                b4CurrentUrl = IPv6Server.makeUrl(ips.get(0));
                b4Status.setText("状态：服务运行中");
            }
            b4Url.setText("下载链接：" + b4CurrentUrl);
            b4StartBtn.setEnabled(false);
            b4StopBtn.setEnabled(true);
            b4CopyBtn.setEnabled(true);
        } catch (Exception e) {
            b4Status.setText("状态：启动失败 - " + e.getMessage());
        }
    }

    private void ipv6Stop() {
        ipv6Server.stop();
        b4Status.setText("状态：已停止");
        b4StartBtn.setEnabled(true);
        b4StopBtn.setEnabled(false);
        b4CopyBtn.setEnabled(false);
    }

    private void copy(String text) {
        if (text == null) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("链接", text));
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
    }

    private void requestPerms() {
        String[] perms;
        if (Build.VERSION.SDK_INT >= 33) {
            perms = new String[]{"android.permission.READ_MEDIA_IMAGES",
                    "android.permission.READ_MEDIA_VIDEO",
                    "android.permission.READ_MEDIA_AUDIO"};
        } else {
            perms = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        }
        boolean need = false;
        for (String p : perms) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) { need = true; break; }
        }
        if (need) requestPermissions(perms, 200);
    }

    @Override protected void onResume() { super.onResume(); refreshIPv6(); }

    @Override protected void onDestroy() {
        super.onDestroy();
        ipv6Server.stop();
        if (p2pSocket != null) p2pSocket.close();
    }

    private static String formatSize(long bytes) {
        if (bytes <= 0) return "未知";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        if (kb < 1024 * 1024) return String.format("%.1f MB", kb / 1024);
        return String.format("%.2f GB", kb / 1024 / 1024);
    }
}