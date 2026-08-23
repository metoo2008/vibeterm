# 安全说明 / Security Notes

## 报告漏洞 / Reporting

发现安全问题请通过 GitHub Issue 反馈(或私下联系维护者)。本项目管理 SSH 凭据与终端通道,任何相关问题都会认真对待。

## 安全设计要点

- **密码**:Android Keystore AES-256-GCM 加密,密钥材料不出安全硬件;密文存私有目录,已排除备份/设备迁移。
- **主机公钥**:首连与变更均需用户核对 SHA-256 指纹后确认(TOFU 固定),保存失败即拒连。
- **SSH 库**:ConnectBot sshlib 2.2.48(含 Terrapin/CVE-2023-48795 修复)。
- **无遥测**:仅连接用户自行配置的服务器。
- **锁屏批准**:默认要求设备认证(API 31+);低版本无法强制认证时不提供直连按键动作。
- **OSC 52**(远端写剪贴板):默认关闭。

## 供应链现状与已知取舍

已启用的完整性控制:

- Gradle Wrapper 固定 `distributionSha256Sum`;
- GitHub Actions 全部 action 固定到 commit SHA;
- 依赖全部锁定**精确版本**(无动态版本区间);
- 仓库仅走 HTTPS。

**已知取舍(暂时接受的风险)**:出于中国大陆网络可达性,Maven 依赖默认优先经阿里云镜像解析(见 `settings.gradle.kts`,官方 `mavenCentral()`/`google()` 作为兜底)。若该镜像对相同坐标返回被篡改的构件,仅靠版本号 + HTTPS + wrapper 校验**无法检测**。

完整的 Gradle dependency verification(`gradle/verification-metadata.xml`,对每个构件做 SHA-256 校验)曾经引入:自动生成的校验基线来自镜像环境,而 GitHub Linux CI 在异环境解析时会命中基线未记录的构件,导致构建在严格校验下持续失败(strict 模式对任何未列出的构件都会硬失败)。因此当前**暂缓**该文件。

**整改计划**:在 CI(Linux)环境内、优先使用官方仓库生成校验基线并提交,使基线与 CI 解析环境一致,再启用严格校验。此为发布后跟进项。参考:<https://docs.gradle.org/current/userguide/dependency_verification.html>

## 审计历史

本项目经过多轮独立安全审计,高风险问题已全部修复。截至最新版本的已知中风险修复情况见发布说明与 `docs/DESIGN.md`;供应链完整校验为上述已记录的跟进项。
