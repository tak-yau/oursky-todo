package com.oursky.todo.models

import upickle.default.{macroRW, ReadWriter as RW}
import sttp.tapir.Schema

case class Subtask(
  id: Long,
  title: String,
  completed: Boolean = false,
  subtasks: List[Subtask] = Nil,
  depth: Int = 1
)

case class Todo(
  id: Long,
  title: String,
  completed: Boolean = false,
  createdAt: Long,
  subtasks: List[Subtask] = Nil
)

case class CreateTodoRequest(title: String)
case class UpdateTodoRequest(title: Option[String], completed: Option[Boolean])
case class AddSubtaskRequest(subtaskTitle: String, parentId: Option[Long] = None)
case class AISuggestionRequest(title: String, taskId: Option[Long] = None, subtaskId: Option[Long] = None)
case class AISuggestionResponse(suggestions: List[String])
case class NotificationRequest(message: String, `type`: String = "success")
case class NotificationResponse(success: Boolean, message: String)
case class ErrorResponse(error: String)

object Subtask {
  implicit val rw: RW[Subtask] = macroRW
  implicit lazy val schema: Schema[Subtask] = Schema.derived[Subtask]
}

object Todo {
  implicit val rw: RW[Todo] = macroRW
  implicit lazy val schema: Schema[Todo] = Schema.derived[Todo]
}

object CreateTodoRequest {
  implicit val rw: RW[CreateTodoRequest] = macroRW
  implicit val schema: Schema[CreateTodoRequest] = Schema.derived[CreateTodoRequest]
}

object UpdateTodoRequest {
  implicit val rw: RW[UpdateTodoRequest] = macroRW
  implicit val schema: Schema[UpdateTodoRequest] = Schema.derived[UpdateTodoRequest]
}

object AddSubtaskRequest {
  implicit val rw: RW[AddSubtaskRequest] = macroRW
  implicit val schema: Schema[AddSubtaskRequest] = Schema.derived[AddSubtaskRequest]
}

object AISuggestionRequest {
  implicit val rw: RW[AISuggestionRequest] = macroRW
  implicit val schema: Schema[AISuggestionRequest] = Schema.derived[AISuggestionRequest]
}

object AISuggestionResponse {
  implicit val rw: RW[AISuggestionResponse] = macroRW
  implicit val schema: Schema[AISuggestionResponse] = Schema.derived[AISuggestionResponse]
}

object NotificationRequest {
  implicit val rw: RW[NotificationRequest] = macroRW
  implicit val schema: Schema[NotificationRequest] = Schema.derived[NotificationRequest]
}

object NotificationResponse {
  implicit val rw: RW[NotificationResponse] = macroRW
  implicit val schema: Schema[NotificationResponse] = Schema.derived[NotificationResponse]
}

object ErrorResponse {
  implicit val rw: RW[ErrorResponse] = macroRW
  implicit val schema: Schema[ErrorResponse] = Schema.derived[ErrorResponse]
}
