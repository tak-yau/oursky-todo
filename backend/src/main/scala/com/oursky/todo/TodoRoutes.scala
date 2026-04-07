package com.oursky.todo

import com.oursky.todo.models.*
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode
import io.circe.generic.auto._

class TodoRoutes(todoService: TodoService, qwenService: Option[QwenService], geminiService: Option[GeminiService]) {

  private val hardcodedFallback = (context: String) => List(
    s"Break down '${context}' into smaller steps",
    s"Research best practices for '${context}'",
    s"Gather required resources and tools",
    s"Create a timeline and schedule",
    s"Execute and track progress"
  )

  private def getSuggestions(context: String, isSubtask: Boolean): List[String] = {
    if (qwenService.isDefined) {
      try {
        qwenService.get.generateSubtaskSuggestions(context, isSubtask)
      } catch {
        case _: Throwable =>
          if (geminiService.isDefined) {
            try {
              geminiService.get.generateSubtaskSuggestions(context, isSubtask)
            } catch {
              case _: Throwable => hardcodedFallback(context)
            }
          } else {
            hardcodedFallback(context)
          }
      }
    } else if (geminiService.isDefined) {
      try {
        geminiService.get.generateSubtaskSuggestions(context, isSubtask)
      } catch {
        case _: Throwable => hardcodedFallback(context)
      }
    } else {
      hardcodedFallback(context)
    }
  }

  private val healthEndpoint = endpoint.get
    .in("health")
    .out(stringBody)
    .handleSuccess(_ => """{"status": "ok"}""")

  private val todosGetEndpoint = endpoint.get
    .in("api" / "todos")
    .out(jsonBody[List[Todo]])
    .handle { _ =>
      Right(todoService.getAll)
    }

  private val todosPostEndpoint = endpoint.post
    .in("api" / "todos")
    .in(jsonBody[CreateTodoRequest])
    .out(jsonBody[Todo])
    .out(statusCode(StatusCode.Created))
    .handle { body =>
      Right(todoService.create(body.title))
    }

  private val todoGetEndpoint = endpoint.get
    .in("api" / "todos" / path[Long]("id"))
    .out(jsonBody[Todo])
    .errorOut(jsonBody[ErrorResponse])
    .handle { id =>
      todoService.getById(id) match {
        case Some(todo) => Right(todo)
        case None => Left(ErrorResponse("Todo not found"))
      }
    }

  private val todoPutEndpoint = endpoint.put
    .in("api" / "todos" / path[Long]("id"))
    .in(jsonBody[UpdateTodoRequest])
    .out(jsonBody[Todo])
    .errorOut(jsonBody[ErrorResponse])
    .handle { (id, body) =>
      todoService.update(id, body.title, body.completed) match {
        case Some(todo) => Right(todo)
        case None => Left(ErrorResponse("Todo not found"))
      }
    }

  private val todoDeleteEndpoint = endpoint.delete
    .in("api" / "todos" / path[Long]("id"))
    .out(statusCode(StatusCode.NoContent))
    .errorOut(jsonBody[ErrorResponse])
    .handle { id =>
      if (todoService.delete(id)) Right(())
      else Left(ErrorResponse("Todo not found"))
    }

  private val subtasksPostEndpoint = endpoint.post
    .in("api" / "todos" / path[Long]("todoId") / "subtasks")
    .in(jsonBody[AddSubtaskRequest])
    .out(jsonBody[Todo])
    .errorOut(jsonBody[ErrorResponse])
    .handle { (todoId, body) =>
      todoService.addSubtask(todoId, body.subtaskTitle, body.parentId) match {
        case Some(todo) => Right(todo)
        case None => Left(ErrorResponse("Todo not found"))
      }
    }

  private val subtasksPutEndpoint = endpoint.put
    .in("api" / "todos" / path[Long]("todoId") / "subtasks" / path[Long]("subtaskId"))
    .in(jsonBody[UpdateTodoRequest])
    .out(jsonBody[Todo])
    .errorOut(jsonBody[ErrorResponse])
    .handle { (todoId, subtaskId, body) =>
      todoService.updateSubtask(todoId, subtaskId, body.completed) match {
        case Some(todo) => Right(todo)
        case None => Left(ErrorResponse("Subtask not found"))
      }
    }

  private val subtasksDeleteEndpoint = endpoint.delete
    .in("api" / "todos" / path[Long]("todoId") / "subtasks" / path[Long]("subtaskId"))
    .out(statusCode(StatusCode.NoContent))
    .errorOut(jsonBody[ErrorResponse])
    .handle { (todoId, subtaskId) =>
      todoService.deleteSubtask(todoId, subtaskId) match {
        case Some(_) => Right(())
        case None => Left(ErrorResponse("Subtask not found"))
      }
    }

  private val suggestionsEndpoint = endpoint.post
    .in("api" / "ai" / "suggestions")
    .in(jsonBody[AISuggestionRequest])
    .out(jsonBody[AISuggestionResponse])
    .errorOut(jsonBody[ErrorResponse])
    .handle { body =>
      if (qwenService.isDefined || geminiService.isDefined) {
        val isSubtask = body.subtaskId.isDefined
        val context = body.title
        val suggestions = getSuggestions(context, isSubtask)
        Right(AISuggestionResponse(suggestions.take(5)))
      } else {
        Left(ErrorResponse("AI service not configured"))
      }
    }

  private val notificationsEndpoint = endpoint.post
    .in("api" / "notifications")
    .in(jsonBody[NotificationRequest])
    .out(jsonBody[NotificationResponse])
    .handle { body =>
      println(s"Notification: ${body.message} (${body.`type`})")
      Right(NotificationResponse(success = true, message = body.message))
    }

  val endpoints = List(
    healthEndpoint,
    todosGetEndpoint,
    todosPostEndpoint,
    todoGetEndpoint,
    todoPutEndpoint,
    todoDeleteEndpoint,
    subtasksPostEndpoint,
    subtasksPutEndpoint,
    subtasksDeleteEndpoint,
    suggestionsEndpoint,
    notificationsEndpoint
  )
}
