package com.oursky.todo.db

import com.augustnagro.magnum.*
import com.oursky.todo.models.{Todo, Subtask}

class TodoModel(db: DB):

  private val todoRepo = Repo[TodoRowCreator, TodoRow, Long]
  private val subtaskRepo = Repo[SubtaskRowCreator, SubtaskRow, Long]
  private val t = TableInfo[TodoRowCreator, TodoRow, Long]
  private val s = TableInfo[SubtaskRowCreator, SubtaskRow, Long]

  def getAll(using DbCon): List[Todo] =
    val todoRows = todoRepo.findAll
    todoRows.map { row =>
      Todo(
        id = row.id,
        title = row.title,
        completed = row.completed,
        createdAt = row.createdAt
      )
    }.toList.sortBy(_.createdAt)

  def getById(id: Long)(using DbCon): Option[Todo] =
    todoRepo.findById(id).map { row =>
      Todo(
        id = row.id,
        title = row.title,
        completed = row.completed,
        createdAt = row.createdAt
      )
    }

  def create(title: String)(using DbCon): Todo =
    val now = System.currentTimeMillis()
    val creator = TodoRowCreator(title, completed = false, createdAt = now)
    val inserted = todoRepo.insertReturning(creator)
    Todo(
      id = inserted.id,
      title = inserted.title,
      completed = inserted.completed,
      createdAt = inserted.createdAt
    )

  def update(id: Long, title: Option[String], completed: Option[Boolean])(using DbCon): Option[Todo] =
    todoRepo.findById(id).map { existing =>
      val updated = existing.copy(
        title = title.getOrElse(existing.title),
        completed = completed.getOrElse(existing.completed)
      )
      todoRepo.update(updated)
      Todo(
        id = updated.id,
        title = updated.title,
        completed = updated.completed,
        createdAt = updated.createdAt
      )
    }

  def delete(id: Long)(using DbCon): Boolean =
    val count = sql"DELETE FROM $t WHERE ${t.id} = $id".update.run()
    count > 0

  def addSubtask(todoId: Long, subtaskTitle: String, parentId: Option[Long])(using DbCon): Option[Todo] =
    todoRepo.findById(todoId).map { todoRow =>
      val parentDepth = parentId match
        case Some(pid) =>
          subtaskRepo.findById(pid).map(_.depth).getOrElse(0)
        case None => 0

      val newDepth = parentDepth + 1
      if newDepth > 4 then
        throw IllegalArgumentException("Maximum subtask depth (4 levels) exceeded")

      val creator = SubtaskRowCreator(todoId, subtaskTitle, completed = false, parentId, newDepth)
      subtaskRepo.insert(creator)

      Todo(
        id = todoRow.id,
        title = todoRow.title,
        completed = todoRow.completed,
        createdAt = todoRow.createdAt
      )
    }

  def updateSubtask(todoId: Long, subtaskId: Long, completed: Option[Boolean])(using DbCon): Option[Todo] =
    todoRepo.findById(todoId).flatMap { todoRow =>
      subtaskRepo.findById(subtaskId).filter(_.todoId == todoId).map { existing =>
        val updated = existing.copy(completed = completed.getOrElse(existing.completed))
        subtaskRepo.update(updated)
        Todo(
          id = todoRow.id,
          title = todoRow.title,
          completed = todoRow.completed,
          createdAt = todoRow.createdAt
        )
      }
    }

  def deleteSubtask(todoId: Long, subtaskId: Long)(using DbCon): Option[Todo] =
    todoRepo.findById(todoId).flatMap { todoRow =>
      val deleted = sql"DELETE FROM $s WHERE id = $subtaskId AND todo_id = $todoId".update.run()
      if deleted > 0 then Some(Todo(
        id = todoRow.id,
        title = todoRow.title,
        completed = todoRow.completed,
        createdAt = todoRow.createdAt
      ))
      else None
    }