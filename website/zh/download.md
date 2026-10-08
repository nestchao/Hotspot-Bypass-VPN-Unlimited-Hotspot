# 下载

**最新版本：v6.0.0** — [更新说明](https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot/releases/tag/v6.0.0)

## Android App

获取最新版本的 Hotspot Bypass VPN Android 应用。

| 项目 | 链接 |
|------|------|
| APK 下载 | [Hotspot-Bypass-VPN.apk](https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot/releases/download/v6.0.0/Hotspot-Bypass-VPN.apk) |
| Google Play | *即将推出* |
| 源代码 | [GitHub 仓库](https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot) |

### 系统要求

- Android 7.0 (API 24) 或更高版本
- 无需 Root 权限
- 支持 Wi-Fi Direct 的设备（大部分现代 Android 手机）

## Windows 客户端

Windows 笔记本用户可使用专用桌面客户端连接手机代理。

| 项目 | 链接 |
|------|------|
| 安装版 | [HotspotBypassVPNSetup.exe](https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot/releases/download/v6.0.0/HotspotBypassVPNSetup.exe) |
| 免安装版 | [Hotspot_Bypass_VPN_Windows.zip](https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot/releases/download/v6.0.0/Hotspot_Bypass_VPN_Windows.zip) |
| 源代码 | [laptop_proxy/](https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot/tree/master/laptop_proxy) |

### 系统要求

- Windows 10 或 11（64 位）
- 管理员权限（TUN 虚拟网卡需要）

::: warning 杀毒软件误报
Windows Defender 可能把桌面客户端报为 `Trojan:Win32/Wacatac.H!ml`。这是**误报**——判定来自启发式（机器学习）检测，而不是已知病毒特征码。客户端首次运行会下载辅助程序、添加默认路由、修改网卡 DNS，这些行为组合起来与「VPN 劫持木马」相似，因此被模型标记。详见 [issue #11](https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot/issues/11)。

如果你希望完全避开打包后的可执行文件，可以直接从源码运行客户端——见下方[从源码构建](#从源码构建)。这种方式不会被杀毒软件拦截。
:::

### 校验下载文件

```bash
# Windows
certutil -hashfile Hotspot-Bypass-VPN.apk SHA256

# macOS / Linux
sha256sum Hotspot-Bypass-VPN.apk
```

| 文件 | SHA256 |
|------|--------|
| `Hotspot-Bypass-VPN.apk` | `8e9c1c9123e7f35cfe85c4cf4c2283c56a75c19f9fd89fd0c01f111569b63571` |
| `HotspotBypassVPNSetup.exe` | `b6c0401d978281a58e1cd71a8d43ccb9f5600da1dc81cd9e3dbc8f1bd01c5568` |
| `Hotspot_Bypass_VPN_Windows.zip` | `39ef7e3ecacd6ce92c074eed5b4fdbf238d5b5f953b253b045ff2126606ac6f6` |

> 免安装版 ZIP 必须先**完整解压**再运行，不要在压缩包内直接双击可执行文件。

### 升级注意

- APK 文件名已从 `Hotspot_Bypass_VPN.apk` 变更为 `Hotspot-Bypass-VPN.apk`。
- APK 为 debug 签名。如果设备拒绝覆盖安装旧版本，请先卸载旧版，然后重新填写主机设置。
- Windows 客户端现在提供安装版和免安装版两种形式，不再提供单个独立 `.exe`。

## 从源码构建

### Android App

```bash
git clone https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot.git
cd Hotspot-Bypass-VPN-Unlimited-Hotspot
./gradlew assembleDebug
```

### Windows 客户端

```bash
git clone https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot.git
cd Hotspot-Bypass-VPN-Unlimited-Hotspot/laptop_proxy
pip install -r requirements.txt
python main.py
```

构建独立可执行文件：

```bash
.\build_exe.bat
```

---

## 更新日志

### 版本 6.0.0（2026年10月）

- **修复** 多设备连接时第二台设备无网的问题（[#13](https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot/issues/13)）
- **修复** 多个客户端之间的 UDP 关联隔离问题
- **修复** 客户端断开时的连接计数泄漏
- **新增** 通过 Windows 移动热点或指定以太网卡共享给游戏主机
- **新增** 单客户端连接上限与拒绝统计
- **改进** Windows 客户端界面——紧凑的 Connect & Share 布局，支持明暗主题
- **改进** 优雅关闭与空闲连接清理

### 版本 5.0.0（2026年6月）

- Windows 客户端使用 TUN 虚拟网卡，捕获全部系统流量
- 崩溃自动恢复，每 15 秒一次代理健康检查
- 桌面客户端提供免安装 ZIP 和安装包两种分发形式

### 版本 4.0.0（2026年6月）

- 笔记本客户端界面迁移至 CustomTkinter
- 现代化 Windows 构建配置

### 版本 3.0.0（2026年6月）

- 增强了服务可靠性和持久性
- 优化了游戏场景的网络性能
- 改进了 Windows 客户端稳定性
- 新增 Windows Phone Proxy Manager
- 应用 Logo 和品牌更新

### 版本 2.0.0（2026年5月）

- 优化网络性能，降低游戏延迟
- 改进后台服务持久性和可靠性
- 增强 Windows 客户端稳定性和功能
- 添加应用 Logo 和品牌标识
