package com.oursky.todo

import cats.effect.IO
import com.oursky.todo.models.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.CirceEntityCodec.*
import io.circe.generic.auto._

class TodoRoutes(todoService: TodoService, qwenService: Option[QwenService], geminiService: Option[GeminiService]) {

  private val hardcodedFallback = (context: String) => List(
    s"Break down '${context}' into smaller steps",
    s"Research best practices for '${context}'",
    s"Gather required resources and tools",
    s"Create a timeline and schedule",
    s"Execute and track progress"
  )

  private def getSuggestions(context: String, isSubtask: Boolean): IO[List[String]] = {
    val qwen: IO[List[String]] = qwenService.fold[IO[List[String]]](
      IO.raiseError(new Throwable("Qwen not configured"))
    )(_.generateSubtaskSuggestions(context, isSubtask))

    val gemini: IO[List[String]] = geminiService.fold[IO[List[String]]](
      IO.raiseError(new Throwable("Gemini not configured"))
    )(_.generateSubtaskSuggestions(context, isSubtask))

    val fallback = IO.pure(hardcodedFallback(context))

    qwen.handleErrorWith { qwenErr =>
      IO.println(s"⚠️ Qwen failed, falling back to Gemini: ${qwenErr.getMessage}") *>
      gemini.handleErrorWith { geminiErr =>
        IO.println(s"⚠️ Gemini also failed, using hardcoded fallback: ${geminiErr.getMessage}") *>
        fallback
      }
    }
  }

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    // ============ HEALTH CHECK ============
    case GET -> Root / "health" =>
      Ok("""{"status": "ok"}""")

    // ============ AI ROUTES (must be before todos to avoid conflict) ============
    case req @ POST -> Root / "api" / "ai" / "suggestions" if qwenService.isDefined || geminiService.isDefined =>
      req.as[AISuggestionRequest].flatMap { body =>
        val isSubtask = body.subtaskId.isDefined
        val context = body.title
        getSuggestions(context, isSubtask).flatMap { suggestions =>
          Ok(AISuggestionResponse(suggestions.take(5)))
        }
      }

    case POST -> Root / "api" / "ai" / "suggestions" =>
      ServiceUnavailable("""{"error": "AI service not configured. Set QWEN_API_KEY or GEMINI_API_KEY"}""")

    // ============ TODO ROUTES ============
    case GET -> Root / "api" / "todos" =>
      todoService.getAll.flatMap(todos => Ok(todos))

    case req @ POST -> Root / "api" / "todos" =>
      req.as[CreateTodoRequest].flatMap { body =>
        todoService.create(body.title).flatMap(todo => Created(todo))
      }

    // ============ SUBTASK ROUTES (must be BEFORE single todo route) ============
    // Create subtask: POST /api/todos/{todoId}/subtasks
    case req @ POST -> Root / "api" / "todos" / LongVar(todoId) / "subtasks" =>
      req.as[AddSubtaskRequest].flatMap { body =>
        todoService.addSubtask(todoId, body.subtaskTitle, body.parentId).flatMap {
          case Some(todo) => Ok(todo)
          case None => NotFound(s"""{"error": "Todo not found"}""")
        }
      }

    // Update subtask: PUT /api/todos/{todoId}/subtasks/{subtaskId}
    case req @ PUT -> Root / "api" / "todos" / LongVar(todoId) / "subtasks" / LongVar(subtaskId) =>
      req.as[UpdateTodoRequest].flatMap { body =>
        todoService.updateSubtask(todoId, subtaskId, body.completed).flatMap {
          case Some(todo) => Ok(todo)
          case None => NotFound(s"""{"error": "Subtask not found"}""")
        }
      }

    // Delete subtask: DELETE /api/todos/{todoId}/subtasks/{subtaskId}
    case DELETE -> Root / "api" / "todos" / LongVar(todoId) / "subtasks" / LongVar(subtaskId) =>
      todoService.deleteSubtask(todoId, subtaskId).flatMap {
        case Some(_) => NoContent()
        case None => NotFound(s"""{"error": "Subtask not found"}""")
      }

    // Get single todo: GET /api/todos/{id}
    case GET -> Root / "api" / "todos" / LongVar(id) =>
      todoService.getById(id).flatMap {
        case Some(todo) => Ok(todo)
        case None => NotFound(s"""{"error": "Todo not found"}""")
      }

    // Update todo: PUT /api/todos/{id}
    case req @ PUT -> Root / "api" / "todos" / LongVar(id) =>
      req.as[UpdateTodoRequest].flatMap { body =>
        todoService.update(id, body.title, body.completed).flatMap {
          case Some(todo) => Ok(todo)
          case None => NotFound(s"""{"error": "Todo not found"}""")
        }
      }

    // Delete todo: DELETE /api/todos/{id}
    case DELETE -> Root / "api" / "todos" / LongVar(id) =>
      todoService.delete(id).flatMap { deleted =>
        if (deleted) NoContent()
        else NotFound(s"""{"error": "Todo not found"}""")
      }

    // ============ NOTIFICATIONS ============
    case req @ POST -> Root / "api" / "notifications" =>
      req.as[NotificationRequest].flatMap { body =>
        IO.println(s"📬 Notification: ${body.message} (${body.`type`})") *>
        Ok(s"""{"success": true, "message": "${body.message}"}""")
      }
  }
}
