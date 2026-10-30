# 参考来源与第三方组件

六个参考项目的来源与固定提交记录见 [reference-versions.json](reference-versions.json)，工作区另保留原始 `reference-projects/manifest.json`。本工程没有复制这些项目的业务代码，也没有修改其源码。

| 参考项目 | 本工程借鉴的实现机制 |
|---|---|
| RikkaHub | 安卓消息与语音职责划分、拍照裁剪、播放生命周期、学习提示规则 |
| Kelivo | 语音先进入可编辑草稿、消息活动时间索引、移动端公式显示 |
| Math_Tutor_RAG | 结构化题目分析、校验不确定状态、复习记录与历史持久化的职责区分 |
| Socratic Tutor | 分层提示、局部步骤后交还思考权、教学行为评测场景 |
| Symly.ai | 作答反馈关联图片步骤、OCR 不确定时请求补充 |
| MathVoice | 数学显示文本与自然语言朗读文本分别表达 |

RikkaHub / Kelivo 的顶层许可证为 AGPL-3.0；Math_Tutor_RAG / Socratic Tutor / Symly.ai 为 MIT；MathVoice 的 README 声明 MIT。本文件不改变任何上游授权。

## APK 内直接包含的网页排版组件

| 组件 | 固定版本 | 上游 | 授权文件 |
|---|---|---|---|
| KaTeX | 0.16.11 | https://github.com/KaTeX/KaTeX | `app/src/main/assets/math/katex-LICENSE.txt` |
| Marked | 12.0.2 | https://github.com/markedjs/marked | `app/src/main/assets/math/marked-LICENSE.txt` |
| DOMPurify | 3.2.6 | https://github.com/cure53/DOMPurify | `app/src/main/assets/math/dompurify-LICENSE.txt` |

组件从对应 npm 发布包取得，保留原始许可证。Gradle Wrapper 来自 Gradle 8.9 wrapper 任务，发行包 SHA-256 在配置中固定。其他安卓依赖通过 Gradle 引入，版本见构建文件。

## 百炼接口依据

- 视觉兼容接口：https://help.aliyun.com/zh/model-studio/qwen-vl-compatible-with-openai
- 结构化输出：https://help.aliyun.com/zh/model-studio/qwen-structured-output
- Qwen-ASR：https://help.aliyun.com/zh/model-studio/qwen-asr-api-reference
- Qwen-TTS：https://help.aliyun.com/zh/model-studio/qwen-tts-api

真实服务结果需配置本人有效的北京地域 API Key 后测试。本工程没有内置他人的服务凭证。
