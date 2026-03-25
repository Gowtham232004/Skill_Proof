-- Create database
CREATE DATABASE IF NOT EXISTS skillproof
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- Create user and grant privileges
CREATE USER IF NOT EXISTS 'skillproof_user'@'localhost' IDENTIFIED BY 'skillproof123';

-- Grant all privileges on the skillproof database
GRANT ALL PRIVILEGES ON skillproof.* TO 'skillproof_user'@'localhost';

-- Apply changes
FLUSH PRIVILEGES;

-- Verify
SELECT CONCAT('User created: ', user, '@', host) as result FROM mysql.user WHERE user='skillproof_user';
