DROP TEMPORARY TABLE IF EXISTS canonical_topic_membership;
DROP TEMPORARY TABLE IF EXISTS compact_topic_membership;

CREATE TEMPORARY TABLE canonical_topic_membership (
    article_id BIGINT NOT NULL PRIMARY KEY,
    topic_id BIGINT NOT NULL,
    original_sort_order INT NOT NULL
) ENGINE=InnoDB;

INSERT INTO canonical_topic_membership (article_id, topic_id, original_sort_order)
SELECT membership.article_id, membership.topic_id, membership.sort_order
FROM topic_article membership
WHERE NOT EXISTS (
    SELECT 1
    FROM topic_article preferred
    WHERE preferred.article_id = membership.article_id
      AND (preferred.sort_order < membership.sort_order
        OR (preferred.sort_order = membership.sort_order AND preferred.topic_id < membership.topic_id))
);

CREATE TEMPORARY TABLE compact_topic_membership (
    article_id BIGINT NOT NULL PRIMARY KEY,
    topic_id BIGINT NOT NULL,
    compact_sort_order INT NOT NULL
) ENGINE=InnoDB;

INSERT INTO compact_topic_membership (article_id, topic_id, compact_sort_order)
SELECT article_id, topic_id,
       ROW_NUMBER() OVER (PARTITION BY topic_id ORDER BY original_sort_order, article_id) - 1
FROM canonical_topic_membership;

START TRANSACTION;

UPDATE article article_row
LEFT JOIN canonical_topic_membership canonical ON canonical.article_id = article_row.id
SET article_row.topic_id = canonical.topic_id;

DELETE membership
FROM topic_article membership
LEFT JOIN canonical_topic_membership canonical
    ON canonical.article_id = membership.article_id
   AND canonical.topic_id = membership.topic_id
WHERE canonical.article_id IS NULL;

UPDATE topic_article membership
JOIN compact_topic_membership compact
  ON compact.article_id = membership.article_id
 AND compact.topic_id = membership.topic_id
SET membership.sort_order = compact.compact_sort_order;

COMMIT;

DROP TEMPORARY TABLE compact_topic_membership;
DROP TEMPORARY TABLE canonical_topic_membership;
