package com.oursky.todo

import com.oursky.todo.models.*
import com.oursky.todo.TodoError
import sttp.tapir.*
import sttp.tapir.json.upickle.*
import sttp.model.StatusCode
import sttp.tapir.oneOf
import sttp.tapir.oneOfVariant
import sttp.tapir.statusCode

class TodoRoutes(val todoService: TodoService, val qwenService: Option[QwenService], val geminiService: Option[GeminiService]):

  private val hardcodedFallback = (context: String) => List(
    s"Break down '${context}' into smaller steps",
    s"Research best practices for '${context}'",
    s"Gather required resources and tools",
    s"Create a timeline and schedule",
    s"Execute and track progress"
  )

  private def getSuggestions(context: String, isSubtask: Boolean): List[String] =
    if qwenService.isDefined then
      try qwenService.get.generateSubtaskSuggestions(context, isSubtask)
      catch case _: Throwable =>
        if geminiService.isDefined then
          try geminiService.get.generateSubtaskSuggestions(context, isSubtask)
          catch case _: Throwable => hardcodedFallback(context)
        else hardcodedFallback(context)
    else if geminiService.isDefined then
      try geminiService.get.generateSubtaskSuggestions(context, isSubtask)
      catch case _: Throwable => hardcodedFallback(context)
    else hardcodedFallback(context)

  private def errorToStatus(error: TodoError): StatusCode = error match
    case TodoError.NotFound(_) => StatusCode.NotFound
    case TodoError.MaxDepthExceeded => StatusCode.BadRequest

  private def errorToMessage(error: TodoError): String = error match
    case TodoError.NotFound(msg) => msg
    case TodoError.MaxDepthExceeded => "Maximum subtask depth exceeded"

  private val healthEndpoint = endpoint.get
    .in("health")
    .out(stringBody)
    .handleSuccess(_ => """{"status": "ok"}""")

  private val todosGetEndpoint = endpoint.get
    .in("api" / "todos")
    .out(jsonBody[List[Todo]])
    .handle(_ => Right(todoService.getAll))

  private val todosPostEndpoint = endpoint.post
    .in("api" / "todos")
    .in(jsonBody[CreateTodoRequest])
    .out(jsonBody[Todo])
    .out(statusCode(StatusCode.Created))
    .handle(body => Right(todoService.create(body.title)))

  private val todoGetEndpoint = endpoint.get
    .in("api" / "todos" / path[Long]("id"))
    .out(jsonBody[Todo])
    .errorOut(oneOf(
      oneOfVariant(StatusCode.NotFound, jsonBody[ErrorResponse])
    ))
    .handle { id =>
      todoService.getById(id) match
        case Right(todo) => Right(todo)
        case Left(error) => Left(ErrorResponse(errorToMessage(error)))
    }

  private val todoPutEndpoint = endpoint.put
    .in("api" / "todos" / path[Long]("id"))
    .in(jsonBody[UpdateTodoRequest])
    .out(jsonBody[Todo])
    .errorOut(oneOf(
      oneOfVariant(StatusCode.NotFound, jsonBody[ErrorResponse]),
      oneOfVariant(StatusCode.BadRequest, jsonBody[ErrorResponse])
    ))
    .handle { (id, body) =>
      todoService.update(id, body.title, body.completed) match
        case Right(todo) => Right(todo)
        case Left(error) => Left(ErrorResponse(errorToMessage(error)))
    }

  private val todoDeleteEndpoint = endpoint.delete
    .in("api" / "todos" / path[Long]("id"))
    .out(statusCode(StatusCode.NoContent))
    .errorOut(oneOf(
      oneOfVariant(StatusCode.NotFound, jsonBody[ErrorResponse])
    ))
    .handle { id =>
      todoService.delete(id) match
        case Right(_) => Right(())
        case Left(error) => Left(ErrorResponse(errorToMessage(error)))
    }

  private val subtasksPostEndpoint = endpoint.post
    .in("api" / "todos" / path[Long]("todoId") / "subtasks")
    .in(jsonBody[AddSubtaskRequest])
    .out(jsonBody[Todo])
    .errorOut(oneOf(
      oneOfVariant(StatusCode.NotFound, jsonBody[ErrorResponse]),
      oneOfVariant(StatusCode.BadRequest, jsonBody[ErrorResponse])
    ))
    .handle { (todoId, body) =>
      todoService.addSubtask(todoId, body.subtaskTitle, body.parentId) match
        case Right(todo) => Right(todo)
        case Left(error) => Left(ErrorResponse(errorToMessage(error)))
    }

  private val suggestionsEndpoint = endpoint.post
    .in("api" / "ai" / "suggestions")
    .in(jsonBody[AISuggestionRequest])
    .out(jsonBody[AISuggestionResponse])
    .errorOut(oneOf(
      oneOfVariant(StatusCode.ServiceUnavailable, jsonBody[ErrorResponse])
    ))
    .handle { body =>
      if qwenService.isDefined || geminiService.isDefined then
        val isSubtask = body.subtaskId.isDefined
        Right(AISuggestionResponse(getSuggestions(body.title, isSubtask).take(5)))
      else
        Left(ErrorResponse("AI service not configured"))
    }

  private val notificationsEndpoint = endpoint.post
    .in("api" / "notifications")
    .in(jsonBody[NotificationRequest])
    .out(jsonBody[NotificationResponse])
    .handle { body =>
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
    suggestionsEndpoint,
    notificationsEndpoint
  )