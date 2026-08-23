# Google Play 上架指南 / 提交清单

本文件给出 VibeTerm 上架 Google Play 所需的全部素材与逐项声明。构建产物 **AAB** 由本项目生成,其余为你在 Play Console 网页端填写的内容。

## 0. 前置:一次性准备

- **Google Play 开发者账号**:一次性 25 美元注册费,需实名。
- **上传 AAB**(不是 APK):`./gradlew :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`(已用你的 keystore 签名)。
- **Play App Signing**:首次上传时启用(推荐)。你的 keystore 成为「上传密钥」,Google 托管「应用签名密钥」。**务必备份 `D:\dev-tools\vibeterm-release.jks` 及其密码——弄丢将无法再更新应用。**

## 1. 应用商店信息(Store listing)

**应用名称 / Title**(≤30 字符):
```
VibeTerm
```

**简短说明 / Short description**(≤80 字符):
- 中文:`为 vibe coding 而生的 SSH 终端:中文输入、断线保活、多窗口`
- English:`SSH terminal for vibe coding: native CJK input, tmux keep-alive, multi-window`

**完整说明 / Full description**(≤4000 字符,中文版示例):
```
VibeTerm 是一个专为「vibe coding」打造的 Android SSH 终端——让你在手机和平板上舒服地运行 Claude Code、Codex 等命令行 AI 编码工具。

为什么不一样:
• 原生中文输入 —— 大多数终端 App 因输入法机制无法输入中文;VibeTerm 重写了输入链路,主流中文输入法开箱即用。
• tmux 断线保活 —— 关闭 App、切换网络、重启手机,重连后无缝回到原会话,长时间运行的任务不中断。
• 多窗口与平板分屏 —— 同时盯多个会话,宽屏左右分屏。
• 锁屏批准 AI 工具调用 —— 检测到确认提示时,通知直接带「确认/打断」按钮(默认需解锁认证)。
• 快捷命令面板、附加键条(Esc、Shift+Tab、Ctrl 等)、任务完成通知、冷启动自动恢复会话、网络切换秒重连。
• JetBrains Mono 字体 + 深色终端配色,已适配 Android 16 全面屏。

隐私:不收集任何数据,无广告无遥测;密码仅在本机 Keystore 加密存储;只连接你自己配置的服务器。开源(GPL-3.0)。

需要服务器安装 tmux(可选,用于断线保活)与 UTF-8 locale。
```
（英文版可据此翻译。）

## 2. 图形素材(需你提供 PNG,规格如下)

| 素材 | 规格 | 说明 |
|---|---|---|
| 应用图标 | 512×512 PNG(32 位,含 alpha) | **已生成**:`docs/store-assets/icon-512.png` |
| 特色图片 Feature graphic | 1024×500 PNG/JPG | **已生成**:`docs/store-assets/feature-1024x500.png` |
| 手机截图 | 至少 2 张,16:9 或 9:16 | 可用 `docs/screenshots/` 内现成图 |
| 平板截图（可选) | 7"/10" | 分屏截图很加分 |

> 图标与横幅已用品牌视觉(深底 + 绿色 `❯_` + JetBrains Mono)生成,见 `docs/store-assets/`;生成器为 `tools/StoreAssets.java`,想改文案/配色重新跑即可。

## 3. 数据安全表(Data safety)—— 本应用全部选「否」

- 是否收集或分享用户数据?**否**(No data collected, no data shared)。
- 数据是否加密传输?SSH 本身即加密;但因不收集数据,此项按「不收集」填写。
- 是否提供删除数据的方式?卸载即删除全部本地数据。
- 隐私政策 URL:填 `docs/PRIVACY.md` 的公开地址(见下方「隐私政策托管」)。

## 4. 特殊用途前台服务声明(必填,否则会被拒)

应用使用 `FOREGROUND_SERVICE_SPECIAL_USE`。Play Console 的「应用内容 → 前台服务」会要求说明用途,填写:
```
维持用户主动发起的交互式 SSH 终端会话在应用退到后台时不被系统中断,
以便长时间运行的命令(如构建、测试、AI 编码代理)持续运行并可在返回时继续交互。
不存在其他后台用途,无位置/媒体/数据同步等行为。
```
（英文:Keeps user-initiated interactive SSH terminal sessions alive while the app is backgrounded, so long-running commands continue and remain interactive when the user returns. No other background use.）

## 5. 权限声明

| 权限 | 用途 |
|---|---|
| INTERNET | 建立 SSH 连接 |
| POST_NOTIFICATIONS | 任务完成提醒、会话保活前台通知 |
| FOREGROUND_SERVICE / …SPECIAL_USE | 后台维持 SSH 会话(见第 4 节) |
| WAKE_LOCK | 连接活跃时避免 CPU 休眠中断 |
| ACCESS_NETWORK_STATE | 监听网络切换以快速重连 |

无危险权限(不涉及定位、通讯录、相机、麦克风、存储读写外部文件等)。

## 6. 内容分级 / 目标受众

- 内容分级问卷:工具类,无暴力/成人/赌博等内容 → 预期 **Everyone / 所有人**。
- 目标受众:成人/一般;**不面向 13 岁以下儿童**。

## 7. 隐私政策托管

Play 需要一个可公开访问的隐私政策 URL。任选其一:
- **GitHub Pages**:在仓库 Settings → Pages 开启,`docs/PRIVACY.md` 会有公开地址;或
- 直接用 GitHub 渲染页 URL:`https://github.com/metoo2008/vibeterm/blob/master/docs/PRIVACY.md`(Play 接受)。

## 8. 上传步骤(概览)

1. Play Console → 创建应用 → 填名称、语言、免费/付费(免费)、声明。
2. 完成「应用内容」:隐私政策、数据安全、前台服务声明、内容分级、目标受众、广告(无)。
3. 创建版本:选「正式版」轨道(或先用「内部测试」跑一遍更稳)。
4. 上传 `app-release.aab`,启用 Play App Signing。
5. 填写商店信息与图形素材,提交审核。

## 9. 后续可选优化

- **AAB 已是 Play 要求格式**,无需额外操作。
- 若想减小体积:可评估开启 R8 代码压缩(需为 sshlib/Compose 补 keep 规则并回归测试;当前默认关闭以求稳)。
- 每次更新须递增 `versionCode`(当前 9)。
