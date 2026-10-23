-- ============================================================================
-- Seed account so you can log in as soon as the stack is up:
--   e-mail: admin@example.com     password: password123
-- The password is stored as a BCrypt hash (cost 10, $2y$ prefix - accepted by Spring's BCryptPasswordEncoder).
-- Regenerate with:  php -r 'echo password_hash("password123", PASSWORD_BCRYPT);'
-- ============================================================================
USE user_db;

INSERT INTO users (id, email, password, first_name, last_name, headline, about, location, role, created_at, updated_at)
VALUES ('00000000-0000-4000-8000-000000000001',
        'admin@example.com',
        '$2y$10$sdQfVZldETpn23TdLyH6buyF7YcpKvB190hL15L8zLSHlEuI0T0Mm',
        'Admin', 'User',
        'Platform administrator',
        'Seeded administrator account.',
        'Remote',
        'ADMIN', NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE email = VALUES(email);

INSERT INTO user_skills (user_id, skill)
SELECT '00000000-0000-4000-8000-000000000001', 'administration'
WHERE NOT EXISTS (SELECT 1 FROM user_skills WHERE user_id = '00000000-0000-4000-8000-000000000001');
