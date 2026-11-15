-- клип привязывается к юниту: лента Immerse показывает видео текущего юнита ученика
ALTER TABLE media_clips ADD COLUMN unit_id UUID REFERENCES units(id) ON DELETE SET NULL;
CREATE INDEX idx_media_clips_unit ON media_clips(unit_id) WHERE is_published;
