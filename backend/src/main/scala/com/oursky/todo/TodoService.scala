package com.oursky.todo

import com.oursky.todo.models.{Todo, Subtask}
import com.oursky.todo.db.TodoRepository

class TodoService(repo: TodoRepository) {
  def getAll: List[Todo] = repo.getAll
  def getById(id: Long): Option[Todo] = repo.getById(id)
  def create(title: String): Todo = repo.create(title)
  def update(id: Long, title: Option[String], completed: Option[Boolean]): Option[Todo] = repo.update(id, title, completed)
  def delete(id: Long): Boolean = repo.delete(id)
  def addSubtask(todoId: Long, subtaskTitle: String, parentId: Option[Long] = None): Option[Todo] = repo.addSubtask(todoId, subtaskTitle, parentId)
  def updateSubtask(todoId: Long, subtaskId: Long, completed: Option[Boolean]): Option[Todo] = repo.updateSubtask(todoId, subtaskId, completed)
  def deleteSubtask(todoId: Long, subtaskId: Long): Option[Todo] = repo.deleteSubtask(todoId, subtaskId)
}
