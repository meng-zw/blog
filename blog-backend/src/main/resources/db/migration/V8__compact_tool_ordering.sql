DROP TEMPORARY TABLE IF EXISTS compact_tool_order;
CREATE TEMPORARY TABLE compact_tool_order (
    id BIGINT NOT NULL PRIMARY KEY,
    sort_order INT NOT NULL
) ENGINE=InnoDB;

INSERT INTO compact_tool_order (id, sort_order)
SELECT id, ROW_NUMBER() OVER (ORDER BY id) - 1
FROM tool;

START TRANSACTION;
UPDATE tool
JOIN compact_tool_order compact ON compact.id = tool.id
SET tool.sort_order = compact.sort_order;
COMMIT;

DROP TEMPORARY TABLE compact_tool_order;
