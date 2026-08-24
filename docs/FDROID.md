# 分发与同步:F-Droid + GitHub(双渠道,均免费开源)

本项目为 **GPL-3.0**(内嵌 Termux 终端引擎),只走 GPL 友好渠道,不上 Google Play。两个渠道**同一套源码、由 git tag 驱动同步**:

| 渠道 | 谁来构建 | 用谁的签名 | 更新方式 |
|---|---|---|---|
| **F-Droid** | F-Droid 服务器从源码构建 | F-Droid 的密钥 | 用户装 F-Droid 客户端自动更新 |
| **GitHub Releases** | 你本地(或 CI) | 你的 keystore | 用户手动下载 APK |

> ⚠️ 两个渠道的 APK 由**不同密钥签名**,互相不能覆盖升级(Android 校验签名)。用户择一渠道即可。
> 若想两边同一签名(可互相升级),需做「可复现构建」让 F-Droid 用你的密钥验证复现,属进阶项,见文末。

## 一、上架 F-Droid(一次性)

1. 确认依赖 100% 自由软件(本项目已核:sshlib=Apache、AndroidX=Apache、JetBrains Mono=OFL,无 Google 专有库)。
2. 构建元数据已备:仓库根 `metadata/dev.vibeterm.yml`(F-Droid 构建配方),商店文案/截图在 `fastlane/metadata/android/{en-US,zh-CN}/`。
3. Fork <https://gitlab.com/fdroid/fdroiddata>,把 `metadata/dev.vibeterm.yml` 放进其 `metadata/` 目录,发起 Merge Request。
4. F-Droid 维护者审核 → 合并后,其构建服务器按 tag 自动构建、签名、发布。
5. 之后每出一个新 tag,F-Droid 依 `UpdateCheckMode: Tags` 自动发现并构建,无需再提 MR。

（也可自建 F-Droid 仓库用 `fdroidserver`,用户添加你的仓库地址;适合想完全自控的场景,维护成本更高。)

## 二、每次发新版的同步流程(核心)

一条主线:**改代码 → 升版本号 → 写 changelog → 打 tag → 推送**。之后两个渠道各自跟进。

```
# 1. app/build.gradle.kts:versionCode +1、versionName 更新
# 2. 写变更日志(F-Droid 会读它):
#    fastlane/metadata/android/en-US/changelogs/<versionCode>.txt
#    fastlane/metadata/android/zh-CN/changelogs/<versionCode>.txt
# 3. 更新 metadata/dev.vibeterm.yml 里的 Builds 追加一条 + CurrentVersion/Code
# 4. 提交、打 tag、推送
git tag -a vX.Y.Z -m "..."
git push origin master
git push origin vX.Y.Z
```

- **F-Droid 侧**:检测到新 tag → 自动从该 tag 源码构建并发布。你只需保证该 tag 能干净构建(CI 已守护)。
- **GitHub 侧**:本地 `./gradlew :app:assembleRelease`(你的 keystore 签名)→ 在 GitHub 网页为该 tag 建 Release,附上 APK。
  - 提示:APK 非逐字节可复现,Release 说明里的 SHA-256 以你实际上传的那个文件为准。

> `versionCode` 每次必须递增且唯一——F-Droid 和 Android 都靠它判断"新版本"。

## 三、关键约束

- **依赖必须保持全自由软件**:以后新增依赖前先确认许可(不能引入 Google Play Services、闭源 SDK 等),否则 F-Droid 会拒。
- **构建默认用官方仓库**:`settings.gradle.kts` 默认 google()/mavenCentral(),保证 F-Droid 构建环境干净;本地要国内加速设 `VIBETERM_CN_MIRROR=true`。
- **不要在源码里塞入任何非自由的二进制/资源**;字体等已确认为自由许可。

## 四、进阶(可选):可复现构建 → 两渠道同一签名

若希望 F-Droid 与 GitHub 的 APK 同一签名、可互相升级:让构建**可复现**(去除时间戳等不确定因素),在 F-Droid 元数据加 `Binaries:` 指向你的 GitHub Release APK,F-Droid 会复现构建并与你的二进制比对,一致则用**你的**签名发布。这需要额外调校,首发可先不做,后续再迭代。

参考:<https://f-droid.org/docs/Reproducible_Builds/> · <https://f-droid.org/docs/Build_Metadata_Reference/>
