package com.oursky.todo

import com.oursky.todo.models.{Todo, Subtask}
import com.oursky.todo.db.{TodoModel, DB}
import com.augustnagro.magnum.DbCon

enum TodoError:
  case NotFound(msg: String)
  case MaxDepthExceeded

class TodoService(todoModel: TodoModel, db: DB):

  def getAll: List[Todo] =
    db.transact(todoModel.getAll)

  def getById(id: Long): Either[TodoError, Todo] =
    db.transactEither(todoModel.getById(id).toRight(TodoError.NotFound("Todo not found")))

  def create(title: String): Todo =
    db.transact(todoModel.create(title))

  def update(id: Long, title: Option[String], completed: Option[Boolean]): Either[TodoError, Todo] =
    db.transactEither(todoModel.update(id, title, completed).toRight(TodoError.NotFound("Todo not found")))

  def delete(id: Long): Either[TodoError, Unit] =
    db.transactEither {
      if todoModel.delete(id) then Right(())
      else Left(TodoError.NotFound("Todo not found"))
    }

  def addSubtask(todoId: Long, subtaskTitle: String, parentId: Option[Long]): Either[TodoError, Todo] =
    db.transactEither {
      todoModel.addSubtask(todoId, subtaskTitle, parentId) match
        case Some(todo) => Right(todo)
        case None => Left(TodoError.NotFound("Todo not found"))
    }

  def updateSubtask(todoId: Long, subtaskId: Long, completed: Option[Boolean]): Either[TodoError, Todo] =
    db.transactEither {
      todoModel.updateSubtask(todoId, subtaskId, completed) match
        case Some(todo) => Right(todo)
        case None => Left(TodoError.NotFound("Subtask not found"))
    }

  def deleteSubtask(todoId: Long, subtaskId: Long): Either[TodoError, Unit] =
    db.transactEither {
      todoModel.deleteSubtask(todoId, subtaskId) match
        case Some(_) => Right(())
        case None => Left(TodoError.NotFound("Subtask not found"))
    }
