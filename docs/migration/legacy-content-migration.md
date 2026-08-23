# 旧内容迁移与备份说明

## 本次只读清点（2026-08-23）

当前开发配置指向 `jdbc:mysql://localhost:3306/blog`，但工作环境没有安装 MySQL
客户端，也没有提供可验证的只读源数据库连接。因此本次**没有连接、启动或修改任何数据库**，
文章、工具、分类、标签及媒体引用的真实行数均为“未能计数”，不得据此推断为 0。

文件系统清点发现旧目录 `blog-backend/uploads` 中有 1 个文件：
`202608/53d44c9ce89e4d3da22738862ac704ae.png`（4304 字节）。该文件被原样保留；在没有
数据库引用证据前，不能判断它是有效内容、孤儿文件或测试文件。新媒体目录默认值为
`blog-backend/media`，本次清点时不存在。

由于没有真实数据库证据，本次不创建 V9 内容迁移，也不臆造文章、工具或关联数据。

## 上线前只读计数

请让数据库管理员使用仅有 `SELECT` 权限的账号，在实际源库中执行：

```sql
SELECT 'article' AS object_type, COUNT(*) AS row_count FROM article WHERE type = 'ARTICLE'
UNION ALL
SELECT 'tool', COUNT(*) FROM tool
UNION ALL
SELECT 'category', COUNT(*) FROM category
UNION ALL
SELECT 'tag', COUNT(*) FROM tag
UNION ALL
SELECT 'media_asset', COUNT(*) FROM media_asset;

SELECT 'article_cover_media' AS reference_type, COUNT(*) AS reference_count
FROM article WHERE cover_media_id IS NOT NULL
UNION ALL
SELECT 'tool_cover_media', COUNT(*) FROM tool WHERE cover_media_id IS NOT NULL
UNION ALL
SELECT 'topic_cover_media', COUNT(*) FROM topic WHERE cover_media_id IS NOT NULL
UNION ALL
SELECT 'profile_avatar_media', COUNT(*) FROM site_profile WHERE avatar_media_id IS NOT NULL;
```

若源库还是旧表结构，应先用 `SHOW TABLES` 和 `SHOW COLUMNS FROM <table>` 只读确认实际
列名，再由人工审阅后的查询计数；不要在未知结构上运行改写语句。

## 强制备份门槛

在任何生产迁移之前，必须同时完成数据库与媒体目录备份，并验证备份可读取。下列命令中的
尖括号为必须显式替换的占位符，不要直接复制执行：

```bash
mysqldump --single-transaction --routines --triggers --host=<host> --user=<readonly-backup-user> --password <database> > <backup.sql>
tar -C <legacy-upload-parent> -czf <legacy-uploads-backup.tar.gz> <legacy-upload-directory>
```

恢复演练应在隔离的空数据库和空媒体目录中进行：

```bash
mysql --host=<restore-host> --user=<restore-user> --password <empty-restore-database> < <backup.sql>
tar -C <empty-restore-media-parent> -xzf <legacy-uploads-backup.tar.gz>
```

只有当只读计数证明存在非种子/非测试内容时，才新增经过审阅的 Flyway 迁移。迁移范围仅限
文章、工具、分类、标签和媒体字段；稳定 slug 必须可重复生成并处理冲突。旧用户、评论、点赞、
收藏及测试账号不得迁移。迁移必须先在备份恢复出的隔离副本验证行数、关联完整性和回滚方案。
