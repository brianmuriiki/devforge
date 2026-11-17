-- V6: record whether a DevVault archive has authenticated encryption.

ALTER TABLE vault_backups ADD COLUMN encrypted INTEGER NOT NULL DEFAULT 0 CHECK (encrypted IN (0, 1));
