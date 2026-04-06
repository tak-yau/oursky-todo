package com.oursky.todo.models

import io.circe._
import io.circe.generic.semiauto._

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
case class AISuggestionResponseBody(suggestions: List[String])
case class NotificationRequest(message: String, `type`: String = "success")

object Subtask {
  implicit val encoder: Encoder[Subtask] = deriveEncoder[Subtask]
  implicit val decoder: Decoder[Subtask] = deriveDecoder[Subtask]
}

object Todo {
  implicit val encoder: Encoder[Todo] = deriveEncoder[Todo]
  implicit val decoder: Decoder[Todo] = deriveDecoder[Todo]
}

object CreateTodoRequest {
  implicit val encoder: Encoder[CreateTodoRequest] = deriveEncoder[CreateTodoRequest]
  implicit val decoder: Decoder[CreateTodoRequest] = deriveDecoder[CreateTodoRequest]
}

object UpdateTodoRequest {
  implicit val encoder: Encoder[UpdateTodoRequest] = deriveEncoder[UpdateTodoRequest]
  implicit val decoder: Decoder[UpdateTodoRequest] = deriveDecoder[UpdateTodoRequest]
}

object AddSubtaskRequest {
  implicit val encoder: Encoder[AddSubtaskRequest] = deriveEncoder[AddSubtaskRequest]
  implicit val decoder: Decoder[AddSubtaskRequest] = deriveDecoder[AddSubtaskRequest]
}

object AISuggestionRequest {
  implicit val encoder: Encoder[AISuggestionRequest] = deriveEncoder[AISuggestionRequest]
  implicit val decoder: Decoder[AISuggestionRequest] = deriveDecoder[AISuggestionRequest]
}

object AISuggestionResponseBody {
  implicit val encoder: Encoder[AISuggestionResponseBody] = deriveEncoder[AISuggestionResponseBody]
  implicit val decoder: Decoder[AISuggestionResponseBody] = deriveDecoder[AISuggestionResponseBody]
}

object NotificationRequest {
  implicit val encoder: Encoder[NotificationRequest] = deriveEncoder[NotificationRequest]
  implicit val decoder: Decoder[NotificationRequest] = deriveDecoder[NotificationRequest]
}