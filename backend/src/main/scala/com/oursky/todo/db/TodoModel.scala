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
    val subtaskRows = subtaskRepo.findAll
    buildTodos(todoRows, subtaskRows)

  def getById(id: Long)(using DbCon): Option[Todo] =
    todoRepo.findById(id).map { todoRow =>
      val subtaskRows = sql"SELECT * FROM $s WHERE todo_id = $id".query[SubtaskRow].run()
      buildTodo(todoRow, subtaskRows)
    }

  def create(title: String)(using DbCon): Todo =
    val now = System.currentTimeMillis()
    val creator = TodoRowCreator(title, completed = false, createdAt = now)
    val inserted = todoRepo.insertReturning(creator)
    val subtaskRows = Seq.empty[SubtaskRow]
    buildTodo(inserted, subtaskRows)

  def update(id: Long, title: Option[String], completed: Option[Boolean])(using DbCon): Option[Todo] =
    todoRepo.findById(id).map { existing =>
      val updated = existing.copy(
        title = title.getOrElse(existing.title),
        completed = completed.getOrElse(existing.completed)
      )
      todoRepo.update(updated)
      val subtaskRows = sql"SELECT * FROM $s WHERE todo_id = $id".query[SubtaskRow].run()
      buildTodo(updated, subtaskRows)
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

      val subtaskRows = sql"SELECT * FROM $s WHERE todo_id = $todoId".query[SubtaskRow].run()
      buildTodo(todoRow, subtaskRows)
    }

  def updateSubtask(todoId: Long, subtaskId: Long, completed: Option[Boolean])(using DbCon): Option[Todo] =
    todoRepo.findById(todoId).flatMap { todoRow =>
      subtaskRepo.findById(subtaskId).map { existing =>
        val updated = existing.copy(completed = completed.getOrElse(existing.completed))
        subtaskRepo.update(updated)
        val subtaskRows = sql"SELECT * FROM $s WHERE todo_id = $todoId".query[SubtaskRow].run()
        buildTodo(todoRow, subtaskRows)
      }
    }

  def deleteSubtask(todoId: Long, subtaskId: Long)(using DbCon): Option[Todo] =
    todoRepo.findById(todoId).map { todoRow =>
      sql"DELETE FROM $s WHERE id = $subtaskId".update.run()
      val subtaskRows = sql"SELECT * FROM $s WHERE todo_id = $todoId".query[SubtaskRow].run()
      buildTodo(todoRow, subtaskRows)
    }

  private def buildTodo(row: TodoRow, subtaskRows: Seq[SubtaskRow]): Todo =
    Todo(
      id = row.id,
      title = row.title,
      completed = row.completed,
      createdAt = row.createdAt,
      subtasks = buildSubtaskTree(subtaskRows.toList, None)
    )

  private def buildTodos(todoRows: Seq[TodoRow], subtaskRows: Seq[SubtaskRow]): List[Todo] =
    todoRows.map { tr =>
      val subs = subtaskRows.filter(_.todoId == tr.id)
      buildTodo(tr, subs)
    }.toList.sortBy(_.createdAt)

  private def buildSubtaskTree(subtasks: List[SubtaskRow], parentId: Option[Long]): List[Subtask] =
    subtasks
      .filter(_.parentId == parentId)
      .map { sr =>
        Subtask(
          id = sr.id,
          title = sr.title,
          completed = sr.completed,
          subtasks = buildSubtaskTree(subtasks, Some(sr.id)),
          depth = sr.depth
        )
      }
