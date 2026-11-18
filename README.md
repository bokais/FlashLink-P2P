# 闪链 P2P — 远程直传，文件不过服务器

**两种直连模式：B1（UDP打洞 P2P）+ B4（IPv6直连）**
不依赖云托管、热点、蓝牙等方案。

## 下载

- **下载 APK**：https://github.com/bokais/FlashLink-P2P/releases/latest/download/FlashLink-P2P.apk

- 要求：Android 6.0+（API 23+），大小约 29 KB，下载即装，无额外依赖
- 想自己编译？见下方[构建](#构建)章节

## 核心特点

- **文件永远不经任何服务器**，P2P 直连
- STUN 只用来查询自己的公网地址（不传文件）
- M3 界面，本地构建，零第三方依赖
- APK 已真机安装验证，UI 完整显示

## 双模式

### 模式一：UDP 打洞 P2P（`STUN + UDP hole punching`）

```
发送端(A) ──STUN查询──> Google STUN（只告知地址，不传文件）
接收端(B) ──STUN查询──> Google STUN
A ←──手动互换地址码──→ B  (微信/短信发一下 IP:端口)
A ────── UDP 直连传文件 ──────> B
```

**实测**：成功获取公网 UDP 映射（真实公网，非 CGNAT；具体地址不公开）。

使用步骤：
1. 双方各自点"获取我的地址码"
2. 双方互换地址码（微信/短信，就像互换电话号码）
3. 接收方点"接收文件"待命
4. 发送方选文件，填入对方地址码，点"发送文件"
5. 文件流式 UDP 直传，有进度显示

限制：对称型 NAT 下打洞可能失败；需双方同时在线。

### 模式二：IPv6 直连

```
发送端(A) 具备全球 IPv6（形如 2001:db8:...）
A 选文件 → 启动服务 → 生成 http://[本机IPv6]:8088/d
接收端(B) 具备 IPv6 + 浏览器 → 打开链接直接下载
```

IPv6 无 NAT，不需要打洞、不需要服务器。

使用步骤：
1. 双方都有全球 IPv6（移动网络开 VoLTE 一般有）
2. 发送方选文件，点"启动服务"
3. 复制链接发给对方
4. 对方浏览器打开即可下载

限制：运营商必须放行 IPv6 入站（部分移动网络会挡）；对方必须有 IPv6。

## 构建

```bash
cd FlashLinkP2P
bash fetch-tools.sh     # 首次：下载D8编译器
bash build.sh           # 编译
```

输出 `FlashLink-P2P.apk`（约 29 KB）

前置依赖：`JDK 17 + aapt + apksigner + zipalign + android-sdk-build-tools + android-sdk-platform-23`
（Ubuntu/Debian: `apt install -y aapt apksigner zipalign android-sdk-build-tools android-sdk-platform-23`）

## 为什么需要手动互换地址码

数据不经过中继服务器，就没有节点能自动交换双方地址。STUN 服务器只知道请求方的地址，不知道对方的地址。因此采用手动互换 `IP:端口` 的方式——这是无服务器 P2P 的固有设计。

## 域名接口（IPv6 AAAA + 模式一DDNS）

### IPv6 域名（模式二）

模式二提供域名输入框：填入一个域名（前提：该域名有 **AAAA 记录**指向本机 IPv6），生成的下载链接使用域名：

```
http://file.example.com:8088/d      ← 域名（好记，无方括号）
替代 http://[2001:db8::1]:8088/d     ← IPv6方括号（难记，RFC 3849 文档示例）
```

域名与 IPv6 地址的格式区别：
- IPv6 链接必须带方括号 `http://[IPv6]:端口`
- 域名链接**不能**带方括号 `http://域名:端口`
- 程序自动区分：填域名用无方括号格式，留空用 IPv6 带方括号格式
- 状态栏提示：`运行中（域名链接，AAAA需指向本机IPv6）`

### 模式一 DDNS 域名

发送方可填 DDNS 域名，地址码用 `域名:端口` 代替裸 IP。

### 接收路径选择

接收文件默认保存到 `Download`，界面上点"切换"按钮可循环选择常用目录：
`Download → Download/FlashLink → Documents → Pictures → Movies → DCIM/Camera`

接收方地址码输入框支持的格式（均已实测解析正确）：

- `example.com:12345` → 域名解析为 IP
- `192.0.2.1:12345` → 裸 IPv4（RFC 5737 文档示例地址）
- `http://example.com:8088/path` → 自动剥离协议和路径
- `udp://a.ddns.net:9000` → 自动剥离协议
- `[2001:db8::1]:8088` → IPv6 方括号格式（RFC 3849 文档示例地址）
- `abc:example.com:12345` → 忽略多余段
- `localhost:9999` → 本地域名

**地址解析说明**：域名解析出的 IP 可能是 CDN、私有地址、IPv6 等，解析器统一交给 `InetAddress.getByName()` 处理，并在状态栏显示解析结果（`解析 xxx -> IP:端口`），便于确认是否正确。

**注意**：DDNS 域名需自行注册（如 no-ip、duckdns 等），并将域名指向动态公网 IP。由于 STUN 拿到的是**端口映射**，域名只能解决 IP 变化问题，端口仍会随每次打洞变化——因此每次互换地址码时端口都是新的，属正常现象。

## 隐私

- 文件内容：全程 P2P 直连，不经过任何服务器
- STUN 服务器：只知道请求方的公网 IP:端口（Google 的 STUN，标准协议）
- 无日志、无账号、无上传