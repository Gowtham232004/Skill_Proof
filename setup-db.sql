-- Create database
CREATE DATABASE IF NOT EXISTS skillproof
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- Create user and grant privileges
CREATE USER IF NOT EXISTS 'skillproof_user'@'localhost' IDENTIFIED BY 'MS@6363';
ALTER USER 'skillproof_user'@'localhost' IDENTIFIED BY 'MS@6363';

-- Grant all privileges on the skillproof database
GRANT ALL PRIVILEGES ON skillproof.* TO 'skillproof_user'@'localhost';

-- Apply changes
FLUSH PRIVILEGES;

-- Feature 2: PR review challenge table
CREATE TABLE IF NOT EXISTS skillproof.pr_reviews (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  badge_token VARCHAR(100) NOT NULL,
  recruiter_id BIGINT NOT NULL,
  file_path VARCHAR(500),
  original_code TEXT,
  modified_code TEXT,
  bug_description TEXT,
  candidate_username VARCHAR(100),
  repo_name VARCHAR(200),
  review_token VARCHAR(100) UNIQUE,
  status ENUM('PENDING', 'ACTIVE', 'COMPLETED', 'EXPIRED') DEFAULT 'PENDING',
  candidate_review_json TEXT,
  overall_score INT,
  bugs_found_count INT DEFAULT 0,
  ai_feedback TEXT,
  time_taken_seconds INT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  expires_at DATETIME,
  completed_at DATETIME,
  CONSTRAINT fk_pr_reviews_recruiter
    FOREIGN KEY (recruiter_id) REFERENCES skillproof.users(id)
);

-- Phase 7: in-app notifications table
CREATE TABLE IF NOT EXISTS skillproof.notifications (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  recipient_user_id BIGINT NOT NULL,
  sender_user_id BIGINT NULL,
  type VARCHAR(50) NOT NULL,
  title VARCHAR(200) NOT NULL,
  message TEXT NOT NULL,
  action_url VARCHAR(500),
  is_read BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  read_at DATETIME NULL,
  metadata_json TEXT,
  email_sent BOOLEAN NOT NULL DEFAULT FALSE,
  email_sent_at DATETIME NULL,
  CONSTRAINT fk_notifications_recipient
    FOREIGN KEY (recipient_user_id) REFERENCES skillproof.users(id),
  CONSTRAINT fk_notifications_sender
    FOREIGN KEY (sender_user_id) REFERENCES skillproof.users(id)
);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_created
  ON skillproof.notifications (recipient_user_id, created_at);

CREATE INDEX IF NOT EXISTS idx_notifications_unread
  ON skillproof.notifications (recipient_user_id, is_read);

-- Phase 4: repo-grounded challenge provenance columns
ALTER TABLE skillproof.challenges
  ADD COLUMN IF NOT EXISTS challenge_mode ENUM('MANUAL', 'REPO_GROUNDED') NOT NULL DEFAULT 'MANUAL',
  ADD COLUMN IF NOT EXISTS access_mode ENUM('OPEN', 'ASSIGNED') NOT NULL DEFAULT 'OPEN',
  ADD COLUMN IF NOT EXISTS assigned_candidates_json LONGTEXT,
  ADD COLUMN IF NOT EXISTS source_badge_token VARCHAR(100),
  ADD COLUMN IF NOT EXISTS source_repo_name VARCHAR(255),
  ADD COLUMN IF NOT EXISTS source_file_path VARCHAR(500),
  ADD COLUMN IF NOT EXISTS source_snippet_hash VARCHAR(64),
  ADD COLUMN IF NOT EXISTS generation_reason VARCHAR(1000);

CREATE INDEX IF NOT EXISTS idx_challenges_source_badge_token
  ON skillproof.challenges (source_badge_token);

-- Verify
SELECT CONCAT('User created: ', user, '@', host) as result FROM mysql.user WHERE user='skillproof_user';
