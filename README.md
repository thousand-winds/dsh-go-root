# dsh-go-root

这是 [ccch1mneyyy/dsh-Go](https://github.com/ccch1mneyyy/dsh-Go) 的个人衍生项目，不是官方 DSH Go 构建，与上游项目没有隶属关系。

## 上游

当前同步到上游 `main` 的这一版：

- 仓库：https://github.com/ccch1mneyyy/dsh-Go
- 提交：[`3b9fbbbb5953ad1afc383ddec16bd63bc02c3442`](https://github.com/ccch1mneyyy/dsh-Go/commit/3b9fbbbb5953ad1afc383ddec16bd63bc02c3442)
- 说明：release: 1.4.1
- 日期：2026-11-27

最新稳定发布是 [v1.4.1](https://github.com/ccch1mneyyy/dsh-Go/releases/tag/v1.4.1)，对应 [`3b9fbbbb5953ad1afc383ddec16bd63bc02c3442`](https://github.com/ccch1mneyyy/dsh-Go/commit/3b9fbbbb5953ad1afc383ddec16bd63bc02c3442)（2026-11-27）。官方 `main` 与这次发布是同一提交。这次同步是上次同步点 [`89095085bc6243cbd10f557bf6b12f15997aed93`](https://github.com/ccch1mneyyy/dsh-Go/commit/89095085bc6243cbd10f557bf6b12f15997aed93) 之后的 1 个官方 main 提交，也就是官方 1.4.1 的版本号与更新日志。本仓库应用版本名跟随上游，现在是 1.4.1。

此前导入过的 [`efa70c152128dfdc03e17441f3b4848ed41fbffe`](https://github.com/ccch1mneyyy/dsh-Go/commit/efa70c152128dfdc03e17441f3b4848ed41fbffe) 已不在上游当前 `main` 历史上。这次同步以现在的上游 `main` 为准。

版权仍归原作者。本仓库保留上游的 [`LICENSE`](LICENSE) 与 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。本仓库的修改同样采用 GNU Affero General Public License v3.0（AGPL-3.0）。

## 安装包

安装包发布在本仓库的 [GitHub Releases](https://github.com/thousand-winds/dsh-go-root/releases)。已有的构建包括 [v1.0.18 arm64](https://github.com/thousand-winds/dsh-go-root/releases/tag/v1.0.18)。

`applicationId` 是 `io.github.thousandwinds.dshgo`。Kotlin 命名空间仍是 `io.github.ccch1mneyyy.dsh_app`，因此可以和官方 DSH Go 同时安装，不会覆盖官方应用。桌面快捷方式指向这个新的 application id。`dshgo://` 链接和 `.dshgo` 文件仍用上游的 scheme；两套应用都装上时，Android 可能会询问用哪一个打开。FileProvider 的 authority 跟随新的 application id，不会和官方应用冲突。

本仓库的 APK 用自己的签名，签名材料不进入 git。包名和签名都与 [ccch1mneyyy/dsh-Go](https://github.com/ccch1mneyyy/dsh-Go) 不同，官方应用内更新不会应用到这个安装包。同一把签名、更高的 versionCode 可以覆盖安装本仓库自己的旧包，不需要先卸载。不要把这里的发布包当作官方 DSH Go，也不要使用上游的签名密钥。KernelSU 会把这个包当成新应用，需要重新授权。

## Android Shell：Root

入口是：设置 → 手机操控 → Android Shell。Root 后端默认关闭。需要先选择 **Root（KernelSU / su）**，阅读高风险说明，打开开关，再点 **连接 Root（su）**。这一步才是本应用第一次调用 `su`。连上之后会核对真实 UID，不是 UID 0 就拒绝执行命令。

每条命令默认仍会确认，并显示完整命令和身份 root、UID 0。上游现在的 Android Shell 卡片还有「允许一次」和「本轮允许」。同一页的 **始终免确认** 只作用于 Root，默认关闭，保存在本机。屏幕操控的免确认不适用。ADB 和 Shizuku 仍走原来的确认卡片。`su` 辅助进程在连接后可以保持运行。

已经打开 Android Shell 且后端选的是 Root 时，应用进程启动、或某条命令发现 su 进程不在，会再连一次并核对 UID 0。KernelSU 本身没在运行时，页面显示 su 不可用，不会停在「已连接」。不会自动改走 ADB 或 Shizuku，也不会在这里持久化临时 root，或替你重新拉起 KernelSU。

## 构建说明

运行时里的 `proot` 与上游当前 `main` 一样，是 Termux `proot` 5.1.107.102。libtalloc 2.5.0 与 libandroid-shmem 0.7 未改。

## English

This repository is a personal derivative of [ccch1mneyyy/dsh-Go](https://github.com/ccch1mneyyy/dsh-Go). It is not an official DSH Go build and is not affiliated with the upstream project.

Synced upstream `main`: [`3b9fbbbb5953ad1afc383ddec16bd63bc02c3442`](https://github.com/ccch1mneyyy/dsh-Go/commit/3b9fbbbb5953ad1afc383ddec16bd63bc02c3442) (release: 1.4.1, 2026-11-27). Official's latest GitHub Release is now [v1.4.1](https://github.com/ccch1mneyyy/dsh-Go/releases/tag/v1.4.1) at [`3b9fbbbb5953ad1afc383ddec16bd63bc02c3442`](https://github.com/ccch1mneyyy/dsh-Go/commit/3b9fbbbb5953ad1afc383ddec16bd63bc02c3442) (2026-11-27); main is that release commit. This sync is the 1 official main commit after [`89095085bc6243cbd10f557bf6b12f15997aed93`](https://github.com/ccch1mneyyy/dsh-Go/commit/89095085bc6243cbd10f557bf6b12f15997aed93), the official 1.4.1 release bump. The app version name here tracks upstream and is now 1.4.1. `LICENSE` and `THIRD_PARTY_NOTICES.md` from upstream are kept. Modifications here are also under AGPL-3.0.

`applicationId` is `io.github.thousandwinds.dshgo`. The Kotlin namespace stays `io.github.ccch1mneyyy.dsh_app`. APKs are fork-signed with the same key as this repository’s earlier releases, so a newer build can replace an older install of this package. Official in-app updates do not apply. Download APKs from this repository’s [GitHub Releases](https://github.com/thousand-winds/dsh-go-root/releases). KernelSU Root Android Shell is opt-in, requires UID 0, has a Root-only “始终免确认” switch, and reconnects only after Root was already enabled. It does not persist a temporary root.
