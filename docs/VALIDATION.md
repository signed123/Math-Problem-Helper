# 0.1.0 交付与验收记录

检查日期：2026-10-29。本次交付是可构建、已签名的原生安卓首版；不代表已经完成真实云服务与真机验收。

## 已实现的流程

Kotlin + Jetpack Compose / Material 3 工程，最低 Android 8.0（API 26），目标 API 35。已实现拍照与相册导入、裁剪、候选题确认与纠正、文字分层引导、录音转写草稿、公式显示与朗读、拍照检查、复述方法卡、独立验证变式、按时间范围复习、1/3/7 天任务、本地版本记录、删除、手动备份恢复、加密密钥及服务设置。

默认演示模式使用固定样例，不识别实际照片、不转写实际录音、不把样例判断当成真实学习成果。真实服务需填写自己的北京地域百炼 API Key，再关闭演示模式。

## 本次已通过的检查

最终执行命令：

```bash
scripts/check.sh :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

结果：`BUILD SUCCESSFUL`。Debug、Release、设备测试 APK 均构建成功。

| 检查 | 结果 | 覆盖范围 |
|---|---|---|
| CoreTest | 8/8 通过 | 日期首尾、时区、超长历史拆分、引用、引导输出与区域 |
| RepositoryTest | 7/7 通过 | 旧题续聊、截止时间、部分正确、会话提示隔离、重做与到期任务、修订、关联删除 |
| BackupTest | 3/3 通过 | 图片和历史往返、重复导入、非法路径及关联校验 |
| MigrationTest | 1/1 通过 | Room 1→2 迁移保留数据 |
| BailianClientTest | 7/7 通过 | 模拟 HTTP 协议、结构化输出、引导复核、虚构引用、失败、取消、ASR 与显示/朗读分离 |
| UiRuntimeTest | 2/2 通过 | Robolectric 中运行生产 Activity、Compose、ViewModel 和 Room，验证导航、保存对话及 Activity 重建 |
| Android Lint | 0 错误、18 警告 | 警告为依赖存在较新版本的提示，当前固定版本构建通过 |
| 数学排版脚本 | 2/2 通过 | 用 JSDOM 执行生产 HTML 模板及随包脚本，验证行内/独立公式、括号语法和不可信 HTML 清理 |
| APK 签名 | 通过 | apksigner 验证 APK Signature Scheme v2，RSA 3072 |

共 **28 项 JVM / Robolectric 测试通过，0 失败、0 跳过**。上述 HTTP 检查使用 MockWebServer，不能据此推断真实模型准确率或云端接口已实际跑通。

数学脚本复查命令（JSDOM 需要另行安装，见 README）：

```bash
NODE_PATH=/path/to/node_modules node scripts/check-render.cjs
```

签名证书 SHA-256：

```text
df0a21d39ea0bd4ea47253a072634dcda5186c46c6602520060699f0d0904d29
```

## 尚未通过的验收

- **设备级自动化未执行完成。** 3 项 Android instrumentation 测试已编译；本环境的软件模拟器出现 Android 系统网络栈崩溃（`Lost network stack`），包管理服务随后不可用（`Can't find service: package`），在安装测试包前失败。没有将其记为通过，也没有将系统故障归因于应用。
- **没有进行实际手机验证。** 相机、麦克风、音频播放、键盘、屏幕适配、权限拒绝及真实设备生命周期仍需检查。JVM 和 JSDOM 检查不能替代这些检查。
- **没有配置真实 API Key。** 识题、教学行为、作答检查、变式题准确性和中文语音效果需要真实调用。实现了连接测试入口，但本次不声称完成一次真实请求。
- [evaluation-cases.json](evaluation-cases.json) 的 12 个教学与服务场景是待执行清单，不计入已通过的 28 项测试。

建议真机顺序：安装 APK → 演示模式查看导航 → 保存自己的 Key 并测试连接 → 拍摄一道清晰的高中数学题 → 核对题干 → 文字辅导 → 语音草稿与朗读开关 → 部分/完整作答检查 → 复述卡片和变式 → 日期复习 → 导出并在测试设备恢复。另检查断网、无效密钥及相机/麦克风权限拒绝。

## 交付文件及凭证

- `dist/math-tutor-0.1.0.apk`：个人签名 Release 安装包。
- `dist/math-tutor-android-source.zip`：源码、Gradle Wrapper、Room schema 与迁移、测试、组件许可证及说明；不包含构建缓存或私有配置。
- `dist/SHA256SUMS`：安装包及源码包校验值。
- `dist/validation.json`：本次检查结果的机器可读摘要。

源码包和 APK 不附带 API Key、原始录音或私有签名密钥。此次构建的签名密钥及口令位于工作区 `/workspace/.signing/`，需由项目持有人单独妥善保存；后续覆盖安装必须沿用相同签名。不要把该目录加入源码仓库或学习记录备份。

六个参考仓库保持原样，固定提交和授权声明见 [reference-versions.json](reference-versions.json)。
