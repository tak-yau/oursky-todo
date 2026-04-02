package com.oursky.todo

import munit.FunSuite
import com.todo.models.*

class TodoServiceSuite extends FunSuite {
  private var service: TodoService = _

  override def beforeEach(context: BeforeEach): Unit = {
    service = TodoService()
  }

  test("create todo should return todo with id") {
    val todo = service.create("Test Task")
    assertEquals(todo.id, 1L)
    assertEquals(todo.title, "Test Task")
    assertEquals(todo.completed, false)
  }

  test("get all todos should return list") {
    service.create("Task 1")
    service.create("Task 2")
    val todos = service.getAll
    assertEquals(todos.length, 2)
  }

  test("update todo should change title") {
    val todo = service.create("Original")
    val updated = service.update(todo.id, Some("Updated"), None)
    assertEquals(updated.get.title, "Updated")
  }

  test("update todo should change completed status") {
    val todo = service.create("Task")
    val updated = service.update(todo.id, None, Some(true))
    assertEquals(updated.get.completed, true)
  }

  test("add subtask should work") {
    val todo = service.create("Parent Task")
    val updated = service.addSubtask(todo.id, "Child Task", None)
    assertEquals(updated.get.subtasks.length, 1)
    assertEquals(updated.get.subtasks.head.title, "Child Task")
    assertEquals(updated.get.subtasks.head.depth, 1)
  }

  test("delete todo should remove from list") {
    val todo = service.create("To Delete")
    val deleted = service.delete(todo.id)
    assertEquals(deleted, true)
    assertEquals(service.getAll.length, 0)
  }

  test("subtask depth should not exceed 5 levels") {
    val todo = service.create("Root")
    
    // Helper function to find the deepest subtask ID
    def findDeepestSubtaskId(subtasks: List[Subtask]): Option[Long] = {
      if (subtasks.isEmpty) None
      else {
        val deepest = subtasks.maxByOption(st => st.depth + countDepth(st.subtasks))
        deepest match {
          case Some(st) if st.subtasks.nonEmpty =>
            findDeepestSubtaskId(st.subtasks)
          case Some(st) =>
            Some(st.id)
          case None => None
        }
      }
    }
    
    def countDepth(subtasks: List[Subtask]): Int = {
      if (subtasks.isEmpty) 0
      else subtasks.map(st => 1 + countDepth(st.subtasks)).max
    }
    
    // Add 5 levels of nested subtasks
    var parentId: Option[Long] = None
    for (i <- 1 to 5) {
      val updated = service.addSubtask(todo.id, s"Level $i", parentId)
      // Find the newly added subtask (the one with depth = i)
      parentId = updated.flatMap(t => 
        findSubtaskByDepth(t.subtasks, i).map(_.id)
      )
    }
    
    // 6th level should throw error
    intercept[IllegalArgumentException] {
      service.addSubtask(todo.id, "Level 6", parentId)
    }
  }
  
  private def findSubtaskByDepth(subtasks: List[Subtask], targetDepth: Int): Option[Subtask] = {
    subtasks.find(_.depth == targetDepth) match {
      case Some(st) => Some(st)
      case None => subtasks.flatMap(st => findSubtaskByDepth(st.subtasks, targetDepth)).headOption
    }
  }
}
