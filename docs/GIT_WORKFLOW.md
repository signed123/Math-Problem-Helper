# Git 版本管理

主分支为 `main`，远程仓库为 [signed123/Math-Problem-Helper](https://github.com/signed123/Math-Problem-Helper)。本次工作区的仓库根目录是 `/workspace/math-tutor-android`；其他电脑可通过 `git clone` 获取。六个参考项目不属于本仓库。

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

`origin` 指向 `https://github.com/signed123/Math-Problem-Helper.git`。首次同步保留了远程初始提交、MIT 许可证和本地实现提交的历史。

后续提交后，可按分支推送：

```bash
git remote -v
git push -u origin 当前分支名称
```

修改源码前先检查 `git status`，拉取远程更新时优先使用 `git pull --ff-only`；若提示分支已分叉，先检查提交差异，再选择合并方式。不要为处理常规同步问题直接强制推送。

首次提交保存当前实现状态。真实百炼调用与设备验收的完成情况以 [验收记录](VALIDATION.md) 为准。
