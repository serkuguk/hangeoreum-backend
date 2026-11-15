ALTER TABLE units DROP CONSTRAINT units_course_id_position_key;
ALTER TABLE units ADD CONSTRAINT units_course_id_position_key
    UNIQUE (course_id, position) DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE lessons DROP CONSTRAINT lessons_unit_id_position_key;
ALTER TABLE lessons ADD CONSTRAINT lessons_unit_id_position_key
    UNIQUE (unit_id, position) DEFERRABLE INITIALLY IMMEDIATE;
