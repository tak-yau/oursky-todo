package com.oursky.todo

import com.oursky.todo.models.*
import scala.collection.mutable
import java.util.concurrent.atomic.AtomicLong

class TodoService {
  private val todos = mutable.Map[Long, Todo]()
  private val idGenerator = new AtomicLong(1)
  private val subtaskIdGenerator = new AtomicLong(1)

  def getAll: List[Todo] = todos.values.toList.sortBy(_.createdAt)
  def getById(id: Long): Option[Todo] = todos.get(id)

  def create(title: String): Todo = {
    val id = idGenerator.getAndIncrement()
    val todo = Todo(id, title)
    todos.put(id, todo)
    todo
  }

  def update(id: Long, title: Option[String], completed: Option[Boolean]): Option[Todo] = {
    todos.get(id).map { existing =>
      val updated = existing.copy(
        title = title.getOrElse(existing.title),
        completed = completed.getOrElse(existing.completed)
      )
      todos.update(id, updated)
      updated
    }
  }

  def addSubtask(todoId: Long, subtaskTitle: String, parentId: Option[Long] = None): Option[Todo] = {
    todos.get(todoId).map { existing =>
      // Find parent depth if parentId is specified
      val parentDepth = parentId.flatMap(findSubtaskDepth(_, existing.subtasks))
      val newDepth = parentDepth.map(_ + 1).getOrElse(1)
      
      // Check depth limit
      if (newDepth > 5) {
        throw new IllegalArgumentException("Maximum subtask depth (5 levels) exceeded")
      }
      
      val newId = subtaskIdGenerator.getAndIncrement()
      val updatedSubtasks = addSubtaskRecursive(subtaskTitle, parentId, newId, newDepth, existing.subtasks)
      val updated = existing.copy(subtasks = updatedSubtasks)
      todos.update(todoId, updated)
      updated
    }
  }

  private def findSubtaskDepth(id: Long, subtasks: List[Subtask]): Option[Int] = {
    subtasks.find(_.id == id) match {
      case Some(st) => Some(st.depth)
      case None =>
        subtasks.flatMap(st => findSubtaskDepth(id, st.subtasks)).headOption
    }
  }

  private def addSubtaskRecursive(title: String, parentId: Option[Long], newId: Long, newDepth: Int, subtasks: List[Subtask]): List[Subtask] = {
    parentId match {
      case Some(pid) =>
        subtasks.map { st =>
          if (st.id == pid) {
            st.copy(subtasks = st.subtasks :+ Subtask(newId, title, false, Nil, newDepth))
          } else {
            st.copy(subtasks = addSubtaskRecursive(title, parentId, newId, newDepth, st.subtasks))
          }
        }
      case None =>
        subtasks :+ Subtask(newId, title, false, Nil, newDepth)
    }
  }

  def updateSubtask(todoId: Long, subtaskId: Long, completed: Option[Boolean]): Option[Todo] = {
    todos.get(todoId).map { existing =>
      val updated = existing.copy(subtasks = updateSubtaskRecursive(subtaskId, completed, existing.subtasks))
      todos.update(todoId, updated)
      updated
    }
  }

  private def updateSubtaskRecursive(id: Long, completed: Option[Boolean], subtasks: List[Subtask]): List[Subtask] = {
    subtasks.map { st =>
      if (st.id == id) {
        st.copy(completed = completed.getOrElse(st.completed))
      } else {
        st.copy(subtasks = updateSubtaskRecursive(id, completed, st.subtasks))
      }
    }
  }

  def delete(id: Long): Boolean = todos.remove(id).isDefined

  def deleteSubtask(todoId: Long, subtaskId: Long): Option[Todo] = {
    todos.get(todoId).map { existing =>
      val updated = existing.copy(subtasks = deleteSubtaskRecursive(subtaskId, existing.subtasks))
      todos.update(todoId, updated)
      updated
    }
  }

  private def deleteSubtaskRecursive(id: Long, subtasks: List[Subtask]): List[Subtask] = {
    subtasks.filterNot(_.id == id).map { st =>
      st.copy(subtasks = deleteSubtaskRecursive(id, st.subtasks))
    }
  }
}
