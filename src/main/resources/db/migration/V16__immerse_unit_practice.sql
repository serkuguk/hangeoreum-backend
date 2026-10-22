ALTER TABLE media_clips ADD COLUMN title text;
ALTER TABLE media_clips ADD COLUMN position integer NOT NULL DEFAULT 0 CHECK (position >= 0);
WITH ordered AS (
    SELECT id, (row_number() OVER (PARTITION BY unit_id ORDER BY created_at, id) - 1)::integer AS position
    FROM media_clips
)
UPDATE media_clips c SET position = ordered.position FROM ordered WHERE c.id = ordered.id;
CREATE INDEX media_clips_unit_position_idx ON media_clips(unit_id, position, id)
    WHERE kind = 'IMMERSE' AND is_published = true;
