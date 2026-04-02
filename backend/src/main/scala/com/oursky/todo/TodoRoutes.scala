package com.oursky.todo

import cats.effect.IO
import com.oursky.todo.models.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.CirceEntityCodec.*
import io.circe.generic.auto._

class TodoRoutes(todoService: TodoService, geminiService: Option[GeminiService]) {
  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    // ============ AI ROUTES (must be before todos to avoid conflict) ============
    case req @ POST -> Root / "api" / "ai" / "suggestions" if geminiService.isDefined =>
      req.as[AISuggestionRequest].flatMap { body =>
        val isSubtask = body.subtaskId.isDefined
        val context = body.title
        geminiService.get.generateSubtaskSuggestions(context, isSubtask).flatMap { suggestions =>
          Ok(AISuggestionResponse(suggestions.take(5)))
        }
      }

    case POST -> Root / "api" / "ai" / "suggestions" =>
      ServiceUnavailable("""{"error": "AI service not configured. Set GEMINI_API_KEY"}""")

    // ============ TODO ROUTES ============
    case GET -> Root / "api" / "todos" =>
      Ok(todoService.getAll)

    case req @ POST -> Root / "api" / "todos" =>
      req.as[CreateTodoRequest].flatMap { body =>
        val todo = todoService.create(body.title)
        Created(todo)
      }

    // ============ SUBTASK ROUTES (must be BEFORE single todo route) ============
    // Create subtask: POST /api/todos/{todoId}/subtasks
    case req @ POST -> Root / "api" / "todos" / LongVar(todoId) / "subtasks" =>
      req.as[AddSubtaskRequest].flatMap { body =>
        todoService.addSubtask(todoId, body.subtaskTitle, body.parentId) match {
          case Some(todo) => Ok(todo)
          case None => NotFound(s"""{"error": "Todo not found"}""")
        }
      }

    // Update subtask: PUT /api/todos/{todoId}/subtasks/{subtaskId}
    case req @ PUT -> Root / "api" / "todos" / LongVar(todoId) / "subtasks" / LongVar(subtaskId) =>
      req.as[UpdateTodoRequest].flatMap { body =>
        todoService.updateSubtask(todoId, subtaskId, body.completed) match {
          case Some(todo) => Ok(todo)
          case None => NotFound(s"""{"error": "Subtask not found"}""")
        }
      }

    // Delete subtask: DELETE /api/todos/{todoId}/subtasks/{subtaskId}
    case DELETE -> Root / "api" / "todos" / LongVar(todoId) / "subtasks" / LongVar(subtaskId) =>
      todoService.deleteSubtask(todoId, subtaskId) match {
        case Some(_) => NoContent()
        case None => NotFound(s"""{"error": "Subtask not found"}""")
      }

    // Get single todo: GET /api/todos/{id}
    case GET -> Root / "api" / "todos" / LongVar(id) =>
      todoService.getById(id) match {
        case Some(todo) => Ok(todo)
        case None => NotFound(s"""{"error": "Todo not found"}""")
      }

    // Update todo: PUT /api/todos/{id}
    case req @ PUT -> Root / "api" / "todos" / LongVar(id) =>
      req.as[UpdateTodoRequest].flatMap { body =>
        todoService.update(id, body.title, body.completed) match {
          case Some(todo) => Ok(todo)
          case None => NotFound(s"""{"error": "Todo not found"}""")
        }
      }

    // Delete todo: DELETE /api/todos/{id}
    case DELETE -> Root / "api" / "todos" / LongVar(id) =>
      if (todoService.delete(id)) NoContent()
      else NotFound(s"""{"error": "Todo not found"}""")

    // ============ NOTIFICATIONS ============
    case req @ POST -> Root / "api" / "notifications" =>
      req.as[NotificationRequest].flatMap { body =>
        IO.println(s"📬 Notification: ${body.message} (${body.`type`})") *>
        Ok(s"""{"success": true, "message": "${body.message}"}""")
      }
  }
}