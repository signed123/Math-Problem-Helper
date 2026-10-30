# 本地 Git 管理

项目仓库根目录是 `/workspace/math-tutor-android`，主分支为 `main`。六个参考项目分别保留在同级 `reference-projects/`，不属于本仓库。

## 管理范围

- 纳入：应用源码、测试、Gradle 构建文件和 Wrapper、Room schema、随包组件及许可证、说明文档、检查脚本和 CI 配置。
- 排除：`local.properties`、`signing.properties`、签名密钥、构建缓存、日志、`dist/` 内的安装包与源码包等生成文件。
- 签名材料位于工程外的 `/workspace/.signing/`，需要另外妥善备份，不能提交到仓库。

## 日常操作

在项目目录执行：

```bash
git status
git diff
git switch -c feature/功能名称
```

完成一项改动并运行相关检查后，选择需要保存的文件提交：

```bash
git add app/src docs
git diff --cached
git commit -m "feat: 描述本次功能"
```

查看历史或某次提交：

```bash
git log --oneline --decorate -10
git show 提交编号
```

需要撤销已提交的改动时，可以用 `git revert 提交编号` 创建撤销提交，保留原有历史。

## 远程备份

当前没有配置远程地址，也没有推送到 GitHub。Git 本地提交可以保存版本历史，但不能替代异机备份。以后确定目标仓库后再配置远程并推送。

首次提交保存当前实现状态。真实百炼调用与设备验收的完成情况以 [验收记录](VALIDATION.md) 为准。
