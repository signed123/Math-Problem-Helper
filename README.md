# Math Problem Helper · 数学引导助手

给高中学生使用的个人安卓学习工具。通过拍题确认、表达思路、分层提示、检查作答、复述总结、变式练习和历史复习，帮助学生自己完成数学题。

本工程使用 Kotlin + Jetpack Compose，最低 Android 8.0。学习记录和照片保存在手机，App 直接调用阿里云百炼，无自建后端。参考设计与第三方组件说明见 [参考来源](docs/REFERENCES.md)。

**当前状态：0.1.0 个人试用版。** 已完成构建与自动化检查；真机和真实百炼调用尚待验证，详见 [验收记录](docs/VALIDATION.md)。演示模式不代表真实识题或教学效果。

## 安装与开始使用

**[直接下载 Android 安装包 v0.1.0](https://github.com/signed123/Math-Problem-Helper/releases/download/v0.1.0/math-tutor-0.1.0.apk)** · [发布说明与全部版本](https://github.com/signed123/Math-Problem-Helper/releases) · [SHA-256 校验文件](https://github.com/signed123/Math-Problem-Helper/releases/download/v0.1.0/SHA256SUMS.txt)

适用于 **Android 8.0 及以上**。在手机浏览器下载 APK，打开后按系统提示授予浏览器或文件管理器安装权限即可，无需下载源码或自行编译。当前发布为个人签名的 **Release 试用版**，真实手机及真实百炼效果仍待验证。

安装包放在 GitHub Releases，签名密钥不上传。需要开发或测试时，也可按下方步骤构建，或在 [GitHub Actions](https://github.com/signed123/Math-Problem-Helper/actions/workflows/android.yml) 成功运行后获取 Debug 构建产物。Debug 包与 Releases 安装包使用不同签名和包名。

1. 初次打开默认是**演示模式**，使用固定样例，不会识别、转写或判断真实照片与录音。可先查看界面和学习记录流程。
2. 在“我的”页面填写**北京地域百炼 API Key**，关闭演示模式并保存，点击“测试已保存的连接”。密钥只在手机通过 Android Keystore 加密保存。
3. 首页选择拍照或相册，裁剪无关信息，确认后上传识题。选择一道题并核对题干、公式、图形条件，必要时修改或补拍。
4. 输入自己的思路，或录音、结束转写、编辑后再发送。根据需要使用“给一点提示”“再具体一点”“这一步没听懂”“补补基础”。
5. 在纸上作答，拍照提交部分或完整过程。只有模型判断整题完成且过程正确时，才开启复述总结。部分步骤正确不会直接记为整题掌握。
6. 用自己的话说出突破口，生成方法卡，再选择相似题练习。变式题生成后另行核对，核对失败或不确定时不展示。
7. 在“复习”选择开始和结束日期，查找期间交流过的题目，选择全部或部分生成回顾。旧题续聊也会纳入；总结不会混入结束日期之后的记录。

语音可在做题页通过喇叭按钮立即关闭，也可在“我的”调整语速。已有题目和方法卡离线可查看；识题、辅导、云端语音及新复习总结需要联网。

## 功能与数据

- 首页：拍题、最近学习、到期复习任务。
- 做题：可纠正识题、补充条件图、语音草稿、分层引导、作答检查、判断纠正入口、方法卡修订、变式练习。
- 复习：日期筛选、逐题个人卡点、原始记录依据、知识点归纳、部分失败重试、独立重做、知识点和掌握状态搜索。
- 本地历史：题目与带时间的学习事件关联，保留题目、消息、作答、状态及方法卡版本。
- 复习安排：整题检查正确后，安排完成日期之后第 1、3、7 天上午 9 点的任务。开始重做不直接清除任务，检查完成后才清除已到期项。第一版在首页提示，不发送系统推送。
- 删除：单题页可删除该题、关联变式、照片及学习记录；受影响的复习提纲会失效，避免继续保留已删除内容。

“已独立验证”表示在该次会话中没有使用提示或历史参考，并提交了被模型判为完整正确的作答；它仍依赖模型判断，不能替代老师核验。数学识别、几何条件及证明审阅的真实准确性，需要在实际题目上持续验证。

## 服务配置

默认北京入口：`https://dashscope.aliyuncs.com`。

| 能力 | 模型 | 协议 |
|---|---|---|
| 识题、引导、检查、总结、变式和复习 | `qwen3-vl-plus` | `/compatible-mode/v1/chat/completions`，JSON Object，关闭思考输出 |
| 录音转写 | `qwen3-asr-flash` | 同步提交 Base64 WAV 音频 |
| 中文语音合成 | `qwen3-tts-flash` | DashScope HTTP；Cherry 音色；按小段合成 |

模型名称可在设置中调整，但替换模型需要支持相应协议。API 调用可能计费，账单以百炼控制台为准。无效密钥、额度、模型权限和网络失败均会显示提示，不把失败当成已成功学习。

实际发送到百炼的是用户确认提交的图片、文字、录音及完成当前任务所需的相关历史。没有把整个数据库上传至云端知识库。请根据服务商的规则管理云服务数据。

## 备份与恢复

“我的 → 导出学习备份”生成 ZIP，包含 `study.json` 和被记录引用的图片。**不包含 API Key、签名密钥或原始录音**。照片和对话以普通文件保存在备份中，请自行保管。

恢复采用按稳定 ID 合并：已有相同记录保留，同名内容冲突则停止。先校验路径、格式、关联与图片，再写入数据库。完成写入前失败会回滚新增记录。恢复不导入服务密钥。

Android 自动备份关闭。更换手机或卸载前请主动导出；在新手机恢复后重新填写 API Key。

## 开发与检查

工具链：JDK 17、Gradle Wrapper 8.9、AGP 8.7.3、Kotlin 2.0.21、Android SDK 35。Android Studio 打开项目后创建 `local.properties` 指定 SDK 路径。

```bash
git clone https://github.com/signed123/Math-Problem-Helper.git
cd Math-Problem-Helper
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Debug APK 输出位置为 `app/build/outputs/apk/debug/app-debug.apk`。安装到测试手机后，在 App 的“我的”页面配置服务；不要将 API Key 填入源码或提交到仓库。

连接模拟器或测试手机后，运行设备级测试：

```bash
./gradlew :app:connectedDebugAndroidTest
```

构建检查包含 JVM、数据库迁移、备份、日期边界和模拟 HTTP 测试；设备级测试需要已启动模拟器或连接安卓设备。

JVM 测试还包含基于 Robolectric 的生产 Activity / Compose / ViewModel / Room 联合检查；这类检查不替代真机相机、麦克风和真实云模型验证。

可选的本地数学排版脚本检查（仅供开发，不是 App 运行依赖）：

```bash
npm install --prefix .render-check --no-audit --no-fund --ignore-scripts jsdom@24.1.3
NODE_PATH="$PWD/.render-check/node_modules" node scripts/check-render.cjs
```

真实教学验收场景见 [evaluation-cases.json](docs/evaluation-cases.json)。其中场景需要真实模型或真实照片，不能以演示回复作为通过依据。

托管工作区可使用 `scripts/check.sh`，自动识别本次任务安装的 JDK 以及环境代理。普通电脑直接使用 Gradle 命令即可。

生成个人安装包：

```bash
python3 scripts/create-signing-key.py
./gradlew :app:assembleRelease
```

签名脚本把密钥保存在工程同级的 `.signing/` 私有目录，将路径写入不入库的 `signing.properties`。请单独备份签名密钥和口令文件；后续覆盖安装同一应用需要相同签名。仓库不附带密钥或口令。

源码结构和状态说明见 [架构说明](docs/ARCHITECTURE.md)、[验收记录](docs/VALIDATION.md)、[参考来源](docs/REFERENCES.md)。

## Git 版本管理

主分支为 `main`，远程仓库为 [signed123/Math-Problem-Helper](https://github.com/signed123/Math-Problem-Helper)。源码、测试、构建配置和文档纳入版本管理；密钥、私有配置、缓存及 `dist/` 生成文件已排除。日常提交与查看历史的方法见 [Git 使用说明](docs/GIT_WORKFLOW.md)。

## 许可证

项目采用 [MIT License](LICENSE)。随包的第三方组件保留各自的许可证，见 [第三方组件说明](docs/REFERENCES.md)。
