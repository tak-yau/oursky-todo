package com.oursky.todo.db

import com.augustnagro.magnum.*

object MyCustomMapper extends SqlNameMapper:
  def toTableName(className: String): String = 
    className.replaceAll("Row$", "") + "s"

  def toColumnName(fieldName: String): String = 
    SqlNameMapper.CamelToSnakeCase.toColumnName(fieldName)

case class TodoRowCreator(
  title: String,
  completed: Boolean,
  createdAt: Long
) derives DbCodec

@Table(H2DbType, MyCustomMapper)
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

@Table(H2DbType, MyCustomMapper)
case class SubtaskRow(
  @Id id: Long,
  todoId: Long,
  title: String,
  completed: Boolean,
  parentId: Option[Long],
  depth: Int
) derives DbCodec