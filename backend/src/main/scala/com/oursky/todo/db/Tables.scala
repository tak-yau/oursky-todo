package com.oursky.todo.db

import slick.jdbc.H2Profile
import slick.jdbc.H2Profile.api._
import com.oursky.todo.db.TodoRow
import com.oursky.todo.db.SubtaskRow

class Tables(val profile: H2Profile.type) {

  class TodosTable(tag: Tag) extends Table[TodoRow](tag, "todos") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def title = column[String]("title", O.Length(500))
    def completed = column[Boolean]("completed", O.Default(false))
    def createdAt = column[Long]("created_at")

    def * = (id, title, completed, createdAt).<>(TodoRow.fromTuple, TodoRow.toTuple)
  }

  class SubtasksTable(tag: Tag) extends Table[SubtaskRow](tag, "subtasks") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def todoId = column[Long]("todo_id")
    def title = column[String]("title", O.Length(500))
    def completed = column[Boolean]("completed", O.Default(false))
    def parentId = column[Option[Long]]("parent_id")
    def depth = column[Int]("depth", O.Default(1))

    def * = (id, todoId, title, completed, parentId, depth).<>(SubtaskRow.fromTuple, SubtaskRow.toTuple)

    def todoFk = foreignKey("fk_subtask_todo", todoId, TableQuery[TodosTable])(_.id, onDelete = ForeignKeyAction.Cascade)
  }

  val todos = TableQuery[TodosTable]
  val subtasks = TableQuery[SubtasksTable]

  val schema = todos.schema ++ subtasks.schema
}
