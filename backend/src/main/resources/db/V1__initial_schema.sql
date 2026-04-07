-- V1__initial_schema.sql
-- Initial schema for the todo application
-- Uses custom MyCustomMapper: TodoRow -> todos, SubtaskRow -> subtasks
-- Idempotent: safe to run multiple times
-- PostgreSQL version

CREATE TABLE IF NOT EXISTS todos (
  id BIGSERIAL PRIMARY KEY,
  title VARCHAR(500) NOT NULL,
  completed BOOLEAN NOT NULL DEFAULT false,
  created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS subtasks (
  id BIGSERIAL PRIMARY KEY,
  todo_id BIGINT NOT NULL,
  title VARCHAR(500) NOT NULL,
  completed BOOLEAN NOT NULL DEFAULT false,
  parent_id BIGINT,
  depth INT NOT NULL DEFAULT 1,
  CONSTRAINT fk_subtask_todo FOREIGN KEY (todo_id) REFERENCES todos(id) ON DELETE CASCADE
);