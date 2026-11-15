-- оригинальное имя загруженного видео: файл на диске хранится как UUID.ext
ALTER TABLE media_clips ADD COLUMN video_name VARCHAR(255);
