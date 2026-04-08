-- V1__initial_schema.sql
-- Initial schema for the todo application
-- Uses Magnum default CamelToSnakeCase mapping (TodoRow -> todo_row)
-- Idempotent: safe to run multiple times
-- PostgreSQL version

CREATE TABLE IF NOT EXISTS todo_row (
  id BIGSERIAL PRIMARY KEY,
  title VARCHAR(500) NOT NULL,
  completed BOOLEAN NOT NULL DEFAULT false,
  created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS subtask_row (
  id BIGSERIAL PRIMARY KEY,
  todo_id BIGINT NOT NULL,
  title VARCHAR(500) NOT NULL,
  completed BOOLEAN NOT NULL DEFAULT false,
  parent_id BIGINT,
  depth INT NOT NULL DEFAULT 1,
  CONSTRAINT fk_subtask_todo FOREIGN KEY (todo_id) REFERENCES todo_row(id) ON DELETE CASCADE
);

-- Indexes for better query performance on nested subtask lookups
CREATE INDEX IF NOT EXISTS idx_subtask_todo_id ON subtask_row(todo_id);
CREATE INDEX IF NOT EXISTS idx_subtask_parent_id ON subtask_row(parent_id);