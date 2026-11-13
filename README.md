# dsh-go-root

Personal derivative of [ccch1mneyyy/dsh-Go](https://github.com/ccch1mneyyy/dsh-Go). This repository is a private fork for one person's device. It is **not** an official DSH Go build, and it is not affiliated with the upstream project.

Upstream tip imported onto `main`:

- Repository: https://github.com/ccch1mneyyy/dsh-Go
- Commit: [`efa70c152128dfdc03e17441f3b4848ed41fbffe`](https://github.com/ccch1mneyyy/dsh-Go/commit/efa70c152128dfdc03e17441f3b4848ed41fbffe)
- Subject: Merge pull request #1113 from ccch1mneyyy/fix/phone-background-connect-simplify-20261112
- Date: 2026-11-13

License: GNU Affero General Public License v3.0. `LICENSE` and `THIRD_PARTY_NOTICES.md` from upstream are kept. Copyright remains with the original authors. Modifications in this fork are also under AGPL-3.0.

This fork stays private. Do not publish release builds as official DSH Go, and do not reuse the upstream signing key. Release APKs are signed with a fork key kept outside git (`*.jks`, `key.properties`). Official in-app updates do not apply: the package id and the signing key both differ from ccch1mneyyy/dsh-Go.

## Install identity

`applicationId` is `io.github.thousandwinds.dshgo`. The Kotlin namespace stays `io.github.ccch1mneyyy.dsh_app`, so this build installs beside official DSH Go and does not replace it. Launcher shortcuts target the new application id. `dshgo://` links and `.dshgo` files keep the upstream scheme; if both apps are installed, Android may ask which one should open the link. File-provider authorities follow the new application id, so they do not collide with the official app. KernelSU sees this as a new app and needs a fresh grant.

## Android Shell: Root

Settings → Phone control → Android Shell. The Root backend is off until you choose **Root（KernelSU / su）**, read the high-risk notice, turn the switch on, and tap **Connect Root (su)**. That is the first time this app runs `su`. After connect it checks the real UID and refuses commands unless the UID is 0. Each command still asks for confirmation and shows the full command with the identity root, UID 0, until you turn on **始终免确认 / Always skip confirmation** on that same page. That switch is Root-only, off by default, and saved on the device. Screen-control waivers still do not apply. The `su` helper may stay running so KernelSU is not prompted on every command. After Root has been enabled, the app starts `su` again when the app process starts and when a command finds the host gone. If KernelSU is not running, the page shows that su is unavailable and does not stay on Connected. There is no fallback to ADB or Shizuku, and this does not restart KernelSU itself.

## Build note

Termux no longer publishes the upstream-pinned `proot` 5.1.107.98 packages. This fork builds the bundled runtime with Termux `proot` 5.1.107.102 (same project, newer package revision) and records that in `THIRD_PARTY_NOTICES.md`. libtalloc 2.5.0 and libandroid-shmem 0.7 are unchanged.
