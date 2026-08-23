# 生产数据备份与恢复

这些脚本只操作显式指定的 Compose 项目和服务，不会从当前目录猜测生产目标。
先创建仅备份目录可读的备份：

```bash
scripts/backup.sh \
  --destination /srv/blog-backups \
  --project xiaom-blog \
  --db-service db \
  --database blog \
  --media-service api \
  --media-path /app/data/media
```

恢复必须在交互终端执行。脚本会严格解析备份 manifest（只接受固定七个键），验证
`SHA256SUMS`、数据库压缩流和不含路径穿越或符号链接的媒体包，并逐项核对命令行指定
的 Compose 文件、项目、服务、数据库和媒体路径。只有输入
`RESTORE xiaom-blog/blog` 才会继续：

```bash
scripts/restore.sh \
  --backup-dir /srv/blog-backups/20260823T120000Z \
  --project xiaom-blog \
  --db-service db \
  --database blog \
  --media-service api \
  --media-path /app/data/media
```

正常恢复先导入唯一临时数据库并执行可查询性检查；通过后才停止 API 写入、立即生成原
数据库 recovery dump，然后替换目标。数据库导入、验证或媒体切换任一步骤失败，退出
trap 会自动用 recovery dump 回滚数据库、还原旧媒体目录并重启 API。审计日志和原库
recovery dump 保留在备份目录内，请在人工复核后再按保留策略处理。

媒体目录使用同一父目录下的临时目录和重命名切换。Compose 必须把持久卷挂载到媒体
目录的**父目录**（本项目卷挂载 `/app/data`，应用媒体路径使用
`/app/data/media`），不能把媒体路径本身直接作为卷挂载点，否则容器运行时不允许
重命名该挂载点。

跨目标恢复默认拒绝。如确有灾备迁移需要，增加 `--allow-cross-target`；脚本会显示源和
目标，并要求额外输入 `CROSS TARGET xiaom-blog/blog`。该授权和最终状态会写入独立审计
日志，不能通过管道或 CI 绕过交互确认。

如 Compose 文件不在默认位置，通过 `--compose-file /absolute/path/docker-compose.yml`
显式指定。执行前应先确认服务健康，并把备份复制到异机或对象存储。恢复完成后需运行
Compose 健康检查并抽查文章、工具与媒体资源。

无 Docker 的合同测试：

```bash
scripts/tests/manifest_contract_test.sh
scripts/tests/restore_safety_test.sh
scripts/tests/backup_restore_static_test.sh
```
