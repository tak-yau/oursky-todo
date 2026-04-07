package com.oursky.todo.models

import io.circe._
import io.circe.generic.semiauto._
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
  createdAt: Long = System.currentTimeMillis(),
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

// Semi-auto JSON derivation
object Subtask {
  implicit val encoder: Encoder[Subtask] = deriveEncoder[Subtask]
  implicit val decoder: Decoder[Subtask] = deriveDecoder[Subtask]
  implicit lazy val schema: Schema[Subtask] = Schema.derived[Subtask]
}

object Todo {
  implicit val encoder: Encoder[Todo] = deriveEncoder[Todo]
  implicit val decoder: Decoder[Todo] = deriveDecoder[Todo]
  implicit lazy val schema: Schema[Todo] = Schema.derived[Todo]
}

object CreateTodoRequest {
  implicit val encoder: Encoder[CreateTodoRequest] = deriveEncoder[CreateTodoRequest]
  implicit val decoder: Decoder[CreateTodoRequest] = deriveDecoder[CreateTodoRequest]
  implicit val schema: Schema[CreateTodoRequest] = Schema.derived[CreateTodoRequest]
}

object UpdateTodoRequest {
  implicit val encoder: Encoder[UpdateTodoRequest] = deriveEncoder[UpdateTodoRequest]
  implicit val decoder: Decoder[UpdateTodoRequest] = deriveDecoder[UpdateTodoRequest]
  implicit val schema: Schema[UpdateTodoRequest] = Schema.derived[UpdateTodoRequest]
}

object AddSubtaskRequest {
  implicit val encoder: Encoder[AddSubtaskRequest] = deriveEncoder[AddSubtaskRequest]
  implicit val decoder: Decoder[AddSubtaskRequest] = deriveDecoder[AddSubtaskRequest]
  implicit val schema: Schema[AddSubtaskRequest] = Schema.derived[AddSubtaskRequest]
}

object AISuggestionRequest {
  implicit val encoder: Encoder[AISuggestionRequest] = deriveEncoder[AISuggestionRequest]
  implicit val decoder: Decoder[AISuggestionRequest] = deriveDecoder[AISuggestionRequest]
  implicit val schema: Schema[AISuggestionRequest] = Schema.derived[AISuggestionRequest]
}

object AISuggestionResponse {
  implicit val encoder: Encoder[AISuggestionResponse] = deriveEncoder[AISuggestionResponse]
  implicit val decoder: Decoder[AISuggestionResponse] = deriveDecoder[AISuggestionResponse]
  implicit val schema: Schema[AISuggestionResponse] = Schema.derived[AISuggestionResponse]
}

object NotificationRequest {
  implicit val encoder: Encoder[NotificationRequest] = deriveEncoder[NotificationRequest]
  implicit val decoder: Decoder[NotificationRequest] = deriveDecoder[NotificationRequest]
  implicit val schema: Schema[NotificationRequest] = Schema.derived[NotificationRequest]
}

object NotificationResponse {
  implicit val encoder: Encoder[NotificationResponse] = deriveEncoder[NotificationResponse]
  implicit val decoder: Decoder[NotificationResponse] = deriveDecoder[NotificationResponse]
  implicit val schema: Schema[NotificationResponse] = Schema.derived[NotificationResponse]
}

object ErrorResponse {
  implicit val encoder: Encoder[ErrorResponse] = deriveEncoder[ErrorResponse]
  implicit val decoder: Decoder[ErrorResponse] = deriveDecoder[ErrorResponse]
  implicit val schema: Schema[ErrorResponse] = Schema.derived[ErrorResponse]
}
