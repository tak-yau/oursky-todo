package com.oursky.todo.db

case class TodoRow(
  id: Long,
  title: String,
  completed: Boolean,
  createdAt: Long
)

object TodoRow {
  def fromTuple(t: (Long, String, Boolean, Long)): TodoRow =
    TodoRow(t._1, t._2, t._3, t._4)
  def toTuple(r: TodoRow): Option[(Long, String, Boolean, Long)] =
    Some((r.id, r.title, r.completed, r.createdAt))
}

case class SubtaskRow(
  id: Long,
  todoId: Long,
  title: String,
  completed: Boolean,
  parentId: Option[Long],
  depth: Int
)

object SubtaskRow {
  def fromTuple(t: (Long, Long, String, Boolean, Option[Long], Int)): SubtaskRow =
    SubtaskRow(t._1, t._2, t._3, t._4, t._5, t._6)
  def toTuple(r: SubtaskRow): Option[(Long, Long, String, Boolean, Option[Long], Int)] =
    Some((r.id, r.todoId, r.title, r.completed, r.parentId, r.depth))
}
