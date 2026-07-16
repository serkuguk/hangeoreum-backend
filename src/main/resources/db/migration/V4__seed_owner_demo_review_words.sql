-- Owner-only demo deck: every currently seeded word is immediately available
-- for review. Existing SRS progress remains untouched.
INSERT INTO user_words (user_id, word_id, due_date)
SELECT users.id, words.id, CURRENT_DATE
FROM users
CROSS JOIN words
WHERE users.email = 'sergio.kuguk@gmail.com'
ON CONFLICT (user_id, word_id) DO NOTHING;
