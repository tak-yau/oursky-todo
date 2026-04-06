package com.oursky.todo.db

import cats.effect.IO
import com.oursky.todo.models.{Todo, Subtask}
import slick.jdbc.JdbcProfile
import slick.jdbc.JdbcBackend
import scala.concurrent.ExecutionContext

class TodoRepository(db: JdbcBackend.Database, tables: Tables)(implicit ec: ExecutionContext) {
  import tables.profile.api._

  def getAll: IO[List[Todo]] = runDBIO {
    for {
      todoRows <- tables.todos.result
      subtaskRows <- tables.subtasks.result
    } yield buildTodos(todoRows, subtaskRows)
  }

  def getById(id: Long): IO[Option[Todo]] = runDBIO {
    for {
      todoOpt <- tables.todos.filter(_.id === id).result.headOption
      result <- todoOpt match {
        case None => DBIO.successful(None)
        case Some(todoRow) =>
          tables.subtasks.filter(_.todoId === id).result.map { subtaskRows =>
            Some(buildTodo(todoRow, subtaskRows))
          }
      }
    } yield result
  }

  def create(title: String): IO[Todo] = runDBIO {
    val now = System.currentTimeMillis()
    for {
      todoRow <- (tables.todos returning tables.todos.map(_.id) into ((row, id) => row.copy(id = id))) += TodoRow(0L, title, completed = false, createdAt = now)
      subtaskRows <- tables.subtasks.filter(_.todoId === todoRow.id).result
    } yield buildTodo(todoRow, subtaskRows)
  }

  def update(id: Long, title: Option[String], completed: Option[Boolean]): IO[Option[Todo]] = runDBIO {
    for {
      existingOpt <- tables.todos.filter(_.id === id).result.headOption
      result <- existingOpt match {
        case None => DBIO.successful(None)
        case Some(existing) =>
          val updated = existing.copy(
            title = title.getOrElse(existing.title),
            completed = completed.getOrElse(existing.completed)
          )
          for {
            _ <- tables.todos.filter(_.id === id).update(updated)
            subtaskRows <- tables.subtasks.filter(_.todoId === id).result
          } yield Some(buildTodo(updated, subtaskRows))
      }
    } yield result
  }

  def delete(id: Long): IO[Boolean] = runDBIO {
    tables.todos.filter(_.id === id).delete.map(_ > 0)
  }

  def addSubtask(todoId: Long, subtaskTitle: String, parentId: Option[Long]): IO[Option[Todo]] = runDBIO {
    for {
      todoOpt <- tables.todos.filter(_.id === todoId).result.headOption
      result <- todoOpt match {
        case None => DBIO.successful(None)
        case Some(todoRow) =>
          for {
            parentDepth <- parentId match {
              case Some(pid) =>
                tables.subtasks.filter(s => s.todoId === todoId && s.id === pid).map(_.depth).result.headOption.map(_.getOrElse(0))
              case None => DBIO.successful(0)
            }
            newDepth = parentDepth + 1
            _ <- if (newDepth > 4)
              DBIO.failed(new IllegalArgumentException("Maximum subtask depth (4 levels) exceeded"))
            else
              DBIO.successful(())
            _ <- tables.subtasks += SubtaskRow(0L, todoId, subtaskTitle, completed = false, parentId, newDepth)
            subtaskRows <- tables.subtasks.filter(_.todoId === todoId).result
          } yield Some(buildTodo(todoRow, subtaskRows))
      }
    } yield result
  }

  def updateSubtask(todoId: Long, subtaskId: Long, completed: Option[Boolean]): IO[Option[Todo]] = runDBIO {
    for {
      todoOpt <- tables.todos.filter(_.id === todoId).result.headOption
      result <- todoOpt match {
        case None => DBIO.successful(None)
        case Some(todoRow) =>
          for {
            subOpt <- tables.subtasks.filter(s => s.todoId === todoId && s.id === subtaskId).result.headOption
            result2 <- subOpt match {
              case None => DBIO.successful(None)
              case Some(existing) =>
                val updated = existing.copy(completed = completed.getOrElse(existing.completed))
                for {
                  _ <- tables.subtasks.filter(_.id === subtaskId).update(updated)
                  subtaskRows <- tables.subtasks.filter(_.todoId === todoId).result
                } yield Some(buildTodo(todoRow, subtaskRows))
            }
          } yield result2
      }
    } yield result
  }

  def deleteSubtask(todoId: Long, subtaskId: Long): IO[Option[Todo]] = runDBIO {
    for {
      todoOpt <- tables.todos.filter(_.id === todoId).result.headOption
      result <- todoOpt match {
        case None => DBIO.successful(None)
        case Some(todoRow) =>
          for {
            _ <- tables.subtasks.filter(s => s.todoId === todoId && s.id === subtaskId).delete
            subtaskRows <- tables.subtasks.filter(_.todoId === todoId).result
          } yield Some(buildTodo(todoRow, subtaskRows))
      }
    } yield result
  }

  private def runDBIO[T](action: DBIOAction[T, NoStream, _]): IO[T] =
    IO.fromFuture(IO(db.run(action)))

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
}
