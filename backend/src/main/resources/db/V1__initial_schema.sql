-- V1__initial_schema.sql
-- Initial schema for the todo application
-- Matches Magnum @Table annotations in Models.scala
-- Uses CamelToSnakeCase mapping: TodoRow -> todo_row
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
