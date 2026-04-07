package com.oursky.todo.db

import com.augustnagro.magnum.*

case class TodoRowCreator(
  title: String,
  completed: Boolean,
  createdAt: Long
) derives DbCodec

@Table(H2DbType, SqlNameMapper.CamelToSnakeCase)
case class TodoRow(
  @Id id: Long,
  title: String,
  completed: Boolean,
  createdAt: Long
) derives DbCodec

case class SubtaskRowCreator(
  todoId: Long,
  title: String,
  completed: Boolean,
  parentId: Option[Long],
  depth: Int
) derives DbCodec

@Table(H2DbType, SqlNameMapper.CamelToSnakeCase)
case class SubtaskRow(
  @Id id: Long,
  todoId: Long,
  title: String,
  completed: Boolean,
  parentId: Option[Long],
  depth: Int
) derives DbCodec
