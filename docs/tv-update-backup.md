# TV 自更新主备发布

客户端先请求构建配置 `API_BASE_URL` 下的 `update?platform=tv`。主请求或清单校验失败后，立即请求一次 GitHub 备用，不等待下次自动检查；主备均失败后结束本次检查。备用固定地址：

https://raw.githubusercontent.com/Elaine-Lynn/GenTV/update-manifest/config.json

主备 JSON 格式相同，均包含版本、包名、下载 URL、SHA-256、正式证书摘要、最低系统版本、文件大小和更新策略。备用的下载地址必须是本仓库的 GitHub Release 附件，不能指回主服务器。

支持 `gentv-tv-v<versionName>-code<versionCode>.apk`，兼容早期 `gentv-tv-<versionName>.apk`。路径必须与清单的版本一致；只接受本仓库 HTTPS 地址，以及 GitHub 官方附件存储域名的受控跳转。包名、版本、SHA-256 和签名验证均保留，不绕过 TLS。

主清单成功但主 APK 下载失败时，额外读取一次备用清单；只有 versionCode 和 SHA-256 与已选版本相同，才切换 GitHub 下载一次。失败后停止，不循环重试。“立即”指主请求判定失败后没有额外等待；连接、读取和系统 DNS 查询自身仍有耗时。首页自动检查频率保留，手动检查和自动检查使用同一主备流程。

## 正式发布

先修改正式版本号、提交并推送版本代码，再使用同一正式签名构建 `:app:assembleRelease`。发布前工作区必须干净。发布需要 Android SDK、私有签名配置和已认证的 GitHub CLI（或 `GH_TOKEN`，需本仓库 contents 写权限）。私钥和 Token 不上传。`v3.7.18` 使用仅匹配该 tag 的 GitHub Actions：服务器先暂存并校验正式 APK 与许可证包，推送 tag 后由工作流下载同一文件、核对摘要并创建 Release；确认公开附件后，再通过 SSH 更新 `update-manifest` 分支和主服务器清单。

```sh
# 仅生成文件，不上传。
python3 tools/publish_tv_release.py --prepare-only

# 默认同步 GitHub，再部署主服务器；API 地址来自部署配置。
GENTV_API_BASE_URL=https://your-api.example/tv/ \
  python3 tools/publish_tv_release.py --host admin@your-server
```

`work/tv-release-github/<versionName>/` 输出正式 APK、`config.json`、`SHA256SUMS.txt` 和含许可证的分发 ZIP。流程如下：

1. 验证正式 APK 和签名，用实际大小、摘要和版本生成独立 GitHub 配置。
2. 上传附件到对应 `v<versionName>` Release，新 Release 先建草稿再公开；拒绝覆盖内容不同的已发布附件。
3. 匿名下载核对 GitHub APK，成功后更新 `update-manifest` 分支根目录的 `config.json`。
4. 将同一 APK 上传主服务器，并原子替换主清单；任一步失败会报错，不能声称主备全部发布成功。

`--github-only` 只发布 GitHub；`--server-only` 用于明确的主服务器恢复操作。新发布不能用本地重编译的同版本 APK 替换已经发布的正式附件；下一版应递增版本号。重试发布时使用同一批产物。

早期的 `server/wallpaper/update-fallback.json` 仍保留给旧客户端，新客户端改用根目录 `config.json`。旧 APK 需要升级一次包含新主备逻辑的主包；修改服务器不能给旧 APK 添加备用代码。

## 验收

检查主请求成功时不访问备用；主请求 DNS、HTTP、JSON 或签名错误时，确认备用只调用一次。验证手动和自动检查、主下载失败后同版本备用下载。摘要不符、版本不符、跨站重定向和不同证书均应被拒绝。

GitHub 在部分网络也可能不可达，备用不保证每个网络都能使用。发布脚本在公开下载无法验证时不会更新备用清单。初次启用新路径可以引用现有公开正式附件，但必须核对其版本、大小、摘要和证书；不能指向本地测试包。
