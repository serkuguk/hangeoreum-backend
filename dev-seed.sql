-- Dev-seed: алфавит (40 자모) + демо-контент курса. Идемпотентно.
BEGIN;

-- ---------- Алфавит ----------
INSERT INTO alphabet_letters (jamo, romanization, letter_group, position) VALUES
('ㅏ','a','VOWEL_BASIC',1),('ㅑ','ya','VOWEL_BASIC',2),('ㅓ','eo','VOWEL_BASIC',3),
('ㅕ','yeo','VOWEL_BASIC',4),('ㅗ','o','VOWEL_BASIC',5),('ㅛ','yo','VOWEL_BASIC',6),
('ㅜ','u','VOWEL_BASIC',7),('ㅠ','yu','VOWEL_BASIC',8),('ㅡ','eu','VOWEL_BASIC',9),
('ㅣ','i','VOWEL_BASIC',10),
('ㅐ','ae','VOWEL_COMPOUND',1),('ㅒ','yae','VOWEL_COMPOUND',2),('ㅔ','e','VOWEL_COMPOUND',3),
('ㅖ','ye','VOWEL_COMPOUND',4),('ㅘ','wa','VOWEL_COMPOUND',5),('ㅙ','wae','VOWEL_COMPOUND',6),
('ㅚ','oe','VOWEL_COMPOUND',7),('ㅝ','wo','VOWEL_COMPOUND',8),('ㅞ','we','VOWEL_COMPOUND',9),
('ㅟ','wi','VOWEL_COMPOUND',10),('ㅢ','ui','VOWEL_COMPOUND',11),
('ㄱ','g/k','CONSONANT_BASIC',1),('ㄴ','n','CONSONANT_BASIC',2),('ㄷ','d/t','CONSONANT_BASIC',3),
('ㄹ','r/l','CONSONANT_BASIC',4),('ㅁ','m','CONSONANT_BASIC',5),('ㅂ','b/p','CONSONANT_BASIC',6),
('ㅅ','s','CONSONANT_BASIC',7),('ㅇ','-/ng','CONSONANT_BASIC',8),('ㅈ','j','CONSONANT_BASIC',9),
('ㅊ','ch','CONSONANT_BASIC',10),('ㅋ','k','CONSONANT_BASIC',11),('ㅌ','t','CONSONANT_BASIC',12),
('ㅍ','p','CONSONANT_BASIC',13),('ㅎ','h','CONSONANT_BASIC',14),
('ㄲ','kk','CONSONANT_DOUBLE',1),('ㄸ','tt','CONSONANT_DOUBLE',2),('ㅃ','pp','CONSONANT_DOUBLE',3),
('ㅆ','ss','CONSONANT_DOUBLE',4),('ㅉ','jj','CONSONANT_DOUBLE',5)
ON CONFLICT (jamo) DO NOTHING;

-- ---------- Тема и слова ----------
INSERT INTO topics (code, title, icon)
SELECT 'intro', 'Знакомство', '👋'
WHERE NOT EXISTS (SELECT 1 FROM topics WHERE code = 'intro');

INSERT INTO words (hangul, romanization, translation, part_of_speech, topic_id, example_ko, example_translation, grammar_note)
SELECT w.hangul, w.rom, w.tr, w.pos, (SELECT id FROM topics WHERE code = 'intro'), w.ex, w.extr, w.note
FROM (VALUES
  ('안녕하세요','annyeonghaseyo','здравствуйте','приветствие','안녕하세요! 저는 지민이에요.','Здравствуйте! Я — Чимин.',NULL),
  ('친구','chingu','друг','существительное','제 친구는 학생이에요.','Мой друг — студент.',NULL),
  ('학생','haksaeng','студент','существительное','저는 학생이에요.','Я — студент.',NULL),
  ('이름','ireum','имя','существительное','이름이 뭐예요?','Как вас зовут?',NULL),
  ('사랑','sarang','любовь','существительное','사랑해요.','Я люблю тебя.',NULL),
  ('시간','sigan','время','существительное','시간이 없어요.','Нет времени.',NULL),
  ('학교','hakgyo','школа','существительное','학교에 가요.','Иду в школу.',NULL),
  ('행복','haengbok','счастье','существительное','저는 지금 행복해요.','Сейчас я счастлив.','행복하다 — быть счастливым')
) AS w(hangul, rom, tr, pos, ex, extr, note)
WHERE NOT EXISTS (SELECT 1 FROM words x WHERE x.hangul = w.hangul);

-- ---------- Курс / юнит / уроки ----------
UPDATE courses SET is_published = TRUE;

INSERT INTO units (course_id, position, title, description, color, is_published)
SELECT (SELECT id FROM courses LIMIT 1), 1, 'Юнит 1 · Знакомство',
       'Приветствия, «я — …» и частицы темы', '#3B6BFF', TRUE
WHERE NOT EXISTS (SELECT 1 FROM units WHERE title = 'Юнит 1 · Знакомство');

INSERT INTO lessons (unit_id, position, type, title, xp_reward, is_free, is_published)
SELECT u.id, l.pos, l.type::lesson_type, l.title, 20, TRUE, TRUE
FROM (SELECT id FROM units WHERE title = 'Юнит 1 · Знакомство') u,
(VALUES
  (1, 'LESSON', 'Приветствия'),
  (2, 'GRAMMAR', 'Частицы 은/는')
) AS l(pos, type, title)
WHERE NOT EXISTS (SELECT 1 FROM lessons x WHERE x.title = l.title);

-- ---------- Tip + упражнения урока «Приветствия» ----------
INSERT INTO learning_tips (lesson_id, title, body_md, examples)
SELECT id, 'Приветствие 안녕하세요',
E'안녕하세요 — универсальное вежливое «здравствуйте».\nДословно: 안녕 (мир, покой) + 하세요 (делайте) — «пребывайте в покое».',
'[{"ko":"안녕하세요! 저는 지민이에요.","translation":"Здравствуйте! Я — Чимин.","highlight":["안녕하세요"]},{"ko":"안녕히 가세요.","translation":"До свидания (уходящему).","highlight":["안녕히"]}]'::jsonb
FROM lessons WHERE title = 'Приветствия'
ON CONFLICT (lesson_id) DO NOTHING;

INSERT INTO exercises (lesson_id, position, kind, payload)
SELECT l.id, e.pos, e.kind::exercise_kind, e.payload::jsonb
FROM (SELECT id FROM lessons WHERE title = 'Приветствия') l,
(VALUES
  (1, 'CHOICE', '{"question":"안녕하세요","romanization":"annyeonghaseyo","options":[{"text":"здравствуйте","icon":"👋","correct":true},{"text":"спасибо","icon":"🙏"},{"text":"до свидания","icon":"🚪"},{"text":"извините","icon":"😔"}]}'),
  (2, 'CHOICE', '{"question":"친구","romanization":"chingu","options":[{"text":"друг","icon":"🧑‍🤝‍🧑","correct":true},{"text":"школа","icon":"🏫"},{"text":"время","icon":"⏰"},{"text":"любовь","icon":"❤️"}]}'),
  (3, 'MATCH_PAIRS', '{"pairs":[{"left":"친구","right":"друг"},{"left":"학교","right":"школа"},{"left":"사랑","right":"любовь"},{"left":"시간","right":"время"}]}'),
  (4, 'WORD_ORDER', '{"translation":"Здравствуйте! Я — студент.","tokens":["안녕하세요","저는","학생","이에요"],"extra":["친구는"]}'),
  (5, 'TYPE_WORD', '{"translation":"друг","answer":"친구","romanization":"chingu"}')
) AS e(pos, kind, payload)
WHERE NOT EXISTS (SELECT 1 FROM exercises x WHERE x.lesson_id = l.id);

-- ---------- Tip + упражнения урока «Частицы 은/는» ----------
INSERT INTO learning_tips (lesson_id, title, body_md, examples)
SELECT id, 'Частицы 은/는',
E'Частица темы. 은 — после согласной, 는 — после гласной.\nВыделяет то, о чём идёт речь: «что касается X…»',
'[{"ko":"저는 학생이에요.","translation":"Я — студент.","highlight":["는"]},{"ko":"이름은 지민이에요.","translation":"Имя — Чимин.","highlight":["은"]}]'::jsonb
FROM lessons WHERE title = 'Частицы 은/는'
ON CONFLICT (lesson_id) DO NOTHING;

INSERT INTO exercises (lesson_id, position, kind, payload)
SELECT l.id, e.pos, e.kind::exercise_kind, e.payload::jsonb
FROM (SELECT id FROM lessons WHERE title = 'Частицы 은/는') l,
(VALUES
  (1, 'FILL_BLANK', '{"sentence":"저 ___ 학생이에요.","translation":"Я — студент","hint":"저 кончается на гласную","options":[{"text":"는","hint":"после гласной"},{"text":"은","hint":"после согласной"},{"text":"이","hint":"подлежащее"}],"correct":"는"}'),
  (2, 'FILL_BLANK', '{"sentence":"이름 ___ 지민이에요.","translation":"Имя — Чимин","hint":"이름 кончается на согласную","options":[{"text":"은","hint":"после согласной"},{"text":"는","hint":"после гласной"}],"correct":"은"}'),
  (3, 'WORD_ORDER', '{"translation":"Мой друг — студент","tokens":["제","친구는","학생","이에요"],"extra":["저는"]}'),
  (4, 'CHOICE', '{"question":"학생","romanization":"haksaeng","options":[{"text":"студент","icon":"🎓","correct":true},{"text":"учитель","icon":"🧑‍🏫"},{"text":"друг","icon":"🧑‍🤝‍🧑"},{"text":"имя","icon":"📛"}]}')
) AS e(pos, kind, payload)
WHERE NOT EXISTS (SELECT 1 FROM exercises x WHERE x.lesson_id = l.id);

-- ---------- Слова уроков ----------
INSERT INTO lesson_words (lesson_id, word_id)
SELECT l.id, w.id FROM lessons l, words w
WHERE l.title = 'Приветствия' AND w.hangul IN ('안녕하세요','친구','학교','사랑','시간')
ON CONFLICT DO NOTHING;

INSERT INTO lesson_words (lesson_id, word_id)
SELECT l.id, w.id FROM lessons l, words w
WHERE l.title = 'Частицы 은/는' AND w.hangul IN ('학생','이름')
ON CONFLICT DO NOTHING;

COMMIT;
