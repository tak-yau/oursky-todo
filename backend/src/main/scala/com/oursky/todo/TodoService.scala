package com.oursky.todo

import cats.effect.IO
import com.oursky.todo.models.{Todo, Subtask}
import com.oursky.todo.db.TodoRepository

class TodoService(repo: TodoRepository) {
  def getAll: IO[List[Todo]] = repo.getAll
  def getById(id: Long): IO[Option[Todo]] = repo.getById(id)
  def create(title: String): IO[Todo] = repo.create(title)
  def update(id: Long, title: Option[String], completed: Option[Boolean]): IO[Option[Todo]] = repo.update(id, title, completed)
  def delete(id: Long): IO[Boolean] = repo.delete(id)
  def addSubtask(todoId: Long, subtaskTitle: String, parentId: Option[Long] = None): IO[Option[Todo]] = repo.addSubtask(todoId, subtaskTitle, parentId)
  def updateSubtask(todoId: Long, subtaskId: Long, completed: Option[Boolean]): IO[Option[Todo]] = repo.updateSubtask(todoId, subtaskId, completed)
  def deleteSubtask(todoId: Long, subtaskId: Long): IO[Option[Todo]] = repo.deleteSubtask(todoId, subtaskId)
}
