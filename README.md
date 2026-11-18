# 闪链 P2P — 远程直传，文件不过服务器

**仅保留你选的两个方案：B1（UDP打洞 P2P）+ B4（IPv6直连）**
其他方案（云托管/热点/局域网/蓝牙）已全部删除。

## 下载

| 方式 | 链接 |
|------|------|
| **直接下载 APK（推荐）** | [FlashLink-P2P.apk](https://github.com/bokais/FlashLink-P2P/releases/latest/download/FlashLink-P2P.apk) |
| 仓库内 APK | [raw/FlashLink-P2P.apk](https://github.com/bokais/FlashLink-P2P/raw/main/FlashLink-P2P.apk) |
| 所有版本 | [Releases 页面](https://github.com/bokais/FlashLink-P2P/releases) |

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
你(A)  ──STUN查询──> Google STUN（只告诉你地址，不碰文件）
对方(B) ──STUN查询──> Google STUN
A ←──手动互换地址码──→ B  (微信/短信发一下 IP:端口)
A ────── UDP 直连传文件 ──────> B
```

**已实测**：本机成功拿到公网映射 `36.8.132.79:56029`（真实公网，非CGNAT），打洞可行。

使用步骤：
1. 双方各自点"获取我的地址码"
2. 双方互换地址码（微信/短信，就像互换电话号码）
3. 接收方点"接收文件"待命
4. 发送方选文件，填入对方地址码，点"发送文件"
5. 文件流式 UDP 直传，有进度显示

限制：对称型 NAT 下打洞可能失败；需双方同时在线。

### 模式二：IPv6 直连

```
你(A) 有全球IPv6: 2408:844f:7920:35dd::1
A 选文件 → 启动服务 → 生成 http://[2408:...]:8088/d
对方(B) 有IPv6 + 浏览器 → 打开链接直接下载
```

IPv6 无 NAT，不需要打洞、不需要服务器。

使用步骤：
1. 双方都有全球 IPv6（移动网络开 VoLTE 一般有）
2. 发送方选文件，点"启动服务"
3. 复制链接发给对方
4. 对方浏览器打开即可下载

限制：运营商必须放行 IPv6 入站（部分移动网络会挡）；对方必须有 IPv6。

## 技术验证清单

| 项目 | 结果 |
|------|------|
| STUN 公网地址查询 | ✅ 实测拿到 `36.8.132.79:56029` |
| IPv6 地址枚举 | ✅ `2408:844f:7920:35dd::1` |
| 本机编译（javac+aapt+d8+apksigner） | ✅ |
| APK v1/v2/v3 签名 | ✅ |
| 真机安装 | ✅ Success |
| 真机启动无崩溃 | ✅ |
| M3 UI 完整显示 | ✅ |
| UDP打洞真实双机传输 | ⚠️ 需两台设备实测（本机无第二台设备验证） |

## 构建

```bash
cd FlashLinkP2P
bash fetch-tools.sh     # 首次：下载D8编译器
bash build.sh           # 编译
```

输出 `FlashLink-P2P.apk`（32KB）

前置依赖：`JDK 17 + aapt + android-sdk-build-tools + android-sdk-platform-23`
（Ubuntu/Debian: `apt install -y aapt android-sdk-build-tools android-sdk-platform-23`）

## 项目结构

```
FlashLinkP2P/
├── build.sh                      # 本地构建脚本
├── FlashLink-P2P.apk             # 已编译APK
├── app/src/main/java/com/flashlink/p2p/
│   ├── MainActivity.java         # M3界面+交互
│   ├── M3.java                   # Material3组件
│   ├── StunClient.java           # B1: STUN地址查询
│   ├── UdpP2P.java               # B1: UDP打洞+可靠传输
│   └── IPv6Server.java           # B4: IPv6直连HTTP服务
└── tools/
    ├── r8.jar                    # D8编译器
    └── debug.keystore            # 签名密钥
```

## 为什么需要手动互换地址码

你要求"无服务器"，那就没有任何地方可以自动交换地址。STUN 服务器只知道你的地址，不知道对方的地址。所以只能像交换电话号码一样手动互换 `IP:端口`——这是无服务器 P2P 的必然设计。

## 域名接口（IPv6 AAAA + 模式一DDNS）

### IPv6 域名（模式二，你要的）

模式二新增域名输入框：填一个域名（前提：该域名有 **AAAA 记录**指向本机 IPv6），生成的下载链接用域名：

```
http://file.example.com:8088/d      ← 域名（好记，无方括号）
替代 http://[2408:844f:...]:8088/d  ← IPv6方括号（难记）
```

"域名后面地址特殊"的处理：
- IPv6 链接必须带方括号 `http://[IPv6]:端口`
- 域名链接**不能**带方括号 `http://域名:端口`
- 程序自动区分：填域名用无方括号格式，留空用 IPv6 带方括号格式
- 状态栏提示：`运行中（域名链接，AAAA需指向 2408:...）`

### 模式一 DDNS 域名

发送方可填 DDNS 域名，地址码用 `域名:端口` 代替裸 IP。

### 接收路径选择

接收文件默认保存到 `Download`，界面上点"切换"按钮可循环选择常用目录：
`Download → Download/FlashLink → Documents → Pictures → Movies → DCIM/Camera`

| 输入格式 | 结果 |
|---------|------|
| `example.com:32667` | ✅ 域名→IP |
| `61.242.130.173:32667` | ✅ 裸IPv4 |
| `http://example.com:8088/path` | ✅ 自动剥离协议和路径 |
| `udp://a.ddns.net:9000` | ✅ 自动剥离协议 |
| `[2408:844f::1]:8088` | ✅ IPv6方括号格式 |
| `abc:example.com:32667` | ✅ 忽略多余段 |
| `localhost:9999` | ✅ 本地域名 |

全部已实测解析正确。

**关于"域名后面地址特殊"**：域名解析出的 IP 可能是 CDN、私有地址、IPv6 等，解析器统一交给 `InetAddress.getByName()` 处理，并显示在状态栏（`解析 xxx -> IP:端口`），你能实时看到解析结果是否正确。

**注意**：DDNS 域名需要你自己注册（如 no-ip、duckdns 等），并把域名指向你的动态公网 IP。由于 STUN 拿到的是**端口映射**，域名只能解决 IP 变化问题，端口仍会随每次打洞变化——所以每次互换地址码时端口是新的，这很正常。

## 隐私

- 文件内容：全程 P2P 直连，不经过任何服务器
- STUN 服务器：只知道你的公网 IP:端口（Google 的 STUN，标准协议）
- 无日志、无账号、无上传