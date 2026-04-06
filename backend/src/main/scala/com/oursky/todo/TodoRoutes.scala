package com.oursky.todo

import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.pattern.ask
import org.apache.pekko.util.Timeout
import scala.concurrent.duration._
import io.circe.syntax._
import io.circe.generic.auto._
import com.oursky.todo.models._
import scala.concurrent.ExecutionContext.Implicits.global

class TodoRoutes(guardianActor: ActorRef, aiSuggestionActor: ActorRef, qwenEnabled: Boolean, geminiEnabled: Boolean) {

  implicit val timeout: Timeout = 10.seconds

  private def toJson[T: io.circe.Encoder](obj: T): String = obj.asJson.noSpaces

  val routes: Route =
    path("health") {
      get {
        complete("""{"status": "ok"}""")
      }
    } ~
    path("api" / "todos") {
      get {
        onSuccess(guardianActor ? TodoCommand.GetAllTodos) {
          case TodoResponse.TodosResponse(todos) => complete(toJson(todos))
          case _ => complete(StatusCodes.InternalServerError -> """{"error": "Failed"}""")
        }
      } ~
      post {
        entity(as[String]) { jsonStr =>
          val decoded = io.circe.parser.decode[CreateTodoRequest](jsonStr)
          decoded match {
            case Right(request) =>
              onSuccess(guardianActor ? TodoCommand.CreateTodo(request.title)) {
                case TodoResponse.TodoItemResponse(Some(todo)) => complete(StatusCodes.Created -> toJson(todo))
                case _ => complete(StatusCodes.InternalServerError -> """{"error": "Failed"}""")
              }
            case Left(_) => 
              complete(StatusCodes.BadRequest -> """{"error": "Invalid JSON"}""")
          }
        }
      }
    } ~
    path("api" / "todos" / LongNumber) { id =>
      get {
        onSuccess(guardianActor ? TodoCommand.GetTodoById(id)) {
          case TodoResponse.TodoItemResponse(Some(todo)) => complete(toJson(todo))
          case TodoResponse.TodoItemResponse(None) => complete(StatusCodes.NotFound -> """{"error": "Not found"}""")
          case _ => complete(StatusCodes.InternalServerError -> """{"error": "Failed"}""")
        }
      } ~
      put {
        entity(as[String]) { jsonStr =>
          val decoded = io.circe.parser.decode[UpdateTodoRequest](jsonStr)
          decoded match {
            case Right(request) =>
              onSuccess(guardianActor ? TodoCommand.UpdateTodo(id, request.title, request.completed)) {
                case TodoResponse.TodoItemResponse(Some(todo)) => complete(toJson(todo))
                case TodoResponse.TodoItemResponse(None) => complete(StatusCodes.NotFound -> """{"error": "Not found"}""")
                case _ => complete(StatusCodes.InternalServerError -> """{"error": "Failed"}""")
              }
            case Left(_) => 
              complete(StatusCodes.BadRequest -> """{"error": "Invalid JSON"}""")
          }
        }
      } ~
      delete {
        onSuccess(guardianActor ? TodoCommand.DeleteTodo(id)) {
          case TodoResponse.BooleanResponse(true) => complete(StatusCodes.NoContent)
          case TodoResponse.BooleanResponse(false) => complete(StatusCodes.NotFound -> """{"error": "Not found}""")
          case _ => complete(StatusCodes.InternalServerError -> """{"error": "Failed"}""")
        }
      }
    } ~
    path("api" / "todos" / LongNumber / "subtasks") { todoId =>
      post {
        entity(as[String]) { jsonStr =>
          val decoded = io.circe.parser.decode[AddSubtaskRequest](jsonStr)
          decoded match {
            case Right(request) =>
              onSuccess(guardianActor ? TodoCommand.AddSubtask(todoId, request.subtaskTitle, request.parentId)) {
                case TodoResponse.TodoItemResponse(Some(todo)) => complete(toJson(todo))
                case TodoResponse.TodoItemResponse(None) => complete(StatusCodes.NotFound -> """{"error": "Todo not found"}""")
                case _ => complete(StatusCodes.InternalServerError -> """{"error": "Failed"}""")
              }
            case Left(_) => 
              complete(StatusCodes.BadRequest -> """{"error": "Invalid JSON"}""")
          }
        }
      }
    } ~
    path("api" / "todos" / LongNumber / "subtasks" / LongNumber) { (todoId, subtaskId) =>
      put {
        entity(as[String]) { jsonStr =>
          val decoded = io.circe.parser.decode[UpdateTodoRequest](jsonStr)
          decoded match {
            case Right(request) =>
              onSuccess(guardianActor ? TodoCommand.UpdateSubtask(todoId, subtaskId, request.completed)) {
                case TodoResponse.TodoItemResponse(Some(todo)) => complete(toJson(todo))
                case TodoResponse.TodoItemResponse(None) => complete(StatusCodes.NotFound -> """{"error": "Subtask not found"}""")
                case _ => complete(StatusCodes.InternalServerError -> """{"error": "Failed"}""")
              }
            case Left(_) => 
              complete(StatusCodes.BadRequest -> """{"error": "Invalid JSON"}""")
          }
        }
      } ~
      delete {
        onSuccess(guardianActor ? TodoCommand.DeleteSubtask(todoId, subtaskId)) {
          case TodoResponse.TodoItemResponse(Some(_)) => complete(StatusCodes.NoContent)
          case TodoResponse.TodoItemResponse(None) => complete(StatusCodes.NotFound -> """{"error": "Subtask not found}""")
          case _ => complete(StatusCodes.InternalServerError -> """{"error": "Failed"}""")
        }
      }
    } ~
    path("api" / "ai" / "suggestions") {
      post {
        entity(as[String]) { jsonStr =>
          val parsed = io.circe.parser.parse(jsonStr).getOrElse(io.circe.Json.Null)
          val title = parsed.hcursor.get[String]("title").getOrElse("")
          val isSubtask = parsed.hcursor.get[Option[Long]]("subtaskId").getOrElse(None).isDefined
          onSuccess(aiSuggestionActor ? AICommand.GetAISuggestions(title, isSubtask)) {
            case AIResponse.AISuggestionsResponse(suggestions) => 
              complete(toJson(AISuggestionResponseBody(suggestions.take(5))))
            case AIResponse.AIErrorResponse(error) => 
              complete(StatusCodes.ServiceUnavailable -> s"""{"error": "$error}""")
            case _ => 
              complete(StatusCodes.InternalServerError -> """{"error": "Failed"}""")
          }
        }
      }
    }
}