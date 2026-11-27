# dsh-go-root

这是 [ccch1mneyyy/dsh-Go](https://github.com/ccch1mneyyy/dsh-Go) 的个人衍生项目，不是官方 DSH Go 构建，与上游项目没有隶属关系。

## 上游

当前同步到上游 `main` 的这一版：

- 仓库：https://github.com/ccch1mneyyy/dsh-Go
- 提交：[`89095085bc6243cbd10f557bf6b12f15997aed93`](https://github.com/ccch1mneyyy/dsh-Go/commit/89095085bc6243cbd10f557bf6b12f15997aed93)
- 说明：Merge pull request #2102 from ccch1mneyyy/fix/tawcroot-compat
- 日期：2026-11-27

最新稳定发布仍是 [v1.4.0](https://github.com/ccch1mneyyy/dsh-Go/releases/tag/v1.4.0)，对应 [`5bb72e26e745327db20b52aa6c0cbd87f9c6b536`](https://github.com/ccch1mneyyy/dsh-Go/commit/5bb72e26e745327db20b52aa6c0cbd87f9c6b536)（2026-11-25）。官方 `main` 已经走到上面的提交，比这个发布超前 38 个提交。这次同步是上次同步点 [`5ebbfd71272eeac564678bbf745177ea1c330c2d`](https://github.com/ccch1mneyyy/dsh-Go/commit/5ebbfd71272eeac564678bbf745177ea1c330c2d) 之后的 32 个官方 main 提交，也都在官方 v1.4.0 之后。本仓库应用版本名跟随上游，仍是 1.4.0。

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

Synced upstream `main`: [`89095085bc6243cbd10f557bf6b12f15997aed93`](https://github.com/ccch1mneyyy/dsh-Go/commit/89095085bc6243cbd10f557bf6b12f15997aed93) (Merge pull request #2102, 2026-11-27). Official's latest GitHub Release is still [v1.4.0](https://github.com/ccch1mneyyy/dsh-Go/releases/tag/v1.4.0) at [`5bb72e26e745327db20b52aa6c0cbd87f9c6b536`](https://github.com/ccch1mneyyy/dsh-Go/commit/5bb72e26e745327db20b52aa6c0cbd87f9c6b536) (2026-11-25); main is 38 commits past that release. This sync is the 32 official main commits after [`5ebbfd71272eeac564678bbf745177ea1c330c2d`](https://github.com/ccch1mneyyy/dsh-Go/commit/5ebbfd71272eeac564678bbf745177ea1c330c2d), all of them after official v1.4.0. The app version name here tracks upstream and remains 1.4.0. `LICENSE` and `THIRD_PARTY_NOTICES.md` from upstream are kept. Modifications here are also under AGPL-3.0.

`applicationId` is `io.github.thousandwinds.dshgo`. The Kotlin namespace stays `io.github.ccch1mneyyy.dsh_app`. APKs are fork-signed with the same key as this repository’s earlier releases, so a newer build can replace an older install of this package. Official in-app updates do not apply. Download APKs from this repository’s [GitHub Releases](https://github.com/thousand-winds/dsh-go-root/releases). KernelSU Root Android Shell is opt-in, requires UID 0, has a Root-only “始终免确认” switch, and reconnects only after Root was already enabled. It does not persist a temporary root.
