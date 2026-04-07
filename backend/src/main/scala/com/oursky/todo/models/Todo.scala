package com.oursky.todo.models

import upickle.default.{macroRW, ReadWriter as RW}

case class Subtask(
  id: Long,
  title: String,
  completed: Boolean = false,
  depth: Int = 1
)

case class Todo(
  id: Long,
  title: String,
  completed: Boolean = false,
  createdAt: Long
)

case class CreateTodoRequest(title: String)
case class UpdateTodoRequest(title: Option[String], completed: Option[Boolean])
case class AddSubtaskRequest(subtaskTitle: String, parentId: Option[Long] = None)
case class AISuggestionRequest(title: String, taskId: Option[Long] = None, subtaskId: Option[Long] = None)
case class AISuggestionResponse(suggestions: List[String])
case class NotificationRequest(message: String, `type`: String = "success")
case class NotificationResponse(success: Boolean, message: String)
case class ErrorResponse(error: String)

object Subtask:
  implicit val rw: RW[Subtask] = macroRW

object Todo:
  implicit val rw: RW[Todo] = macroRW

object CreateTodoRequest:
  implicit val rw: RW[CreateTodoRequest] = macroRW

object UpdateTodoRequest:
  implicit val rw: RW[UpdateTodoRequest] = macroRW

object AddSubtaskRequest:
  implicit val rw: RW[AddSubtaskRequest] = macroRW

object AISuggestionRequest:
  implicit val rw: RW[AISuggestionRequest] = macroRW

object AISuggestionResponse:
  implicit val rw: RW[AISuggestionResponse] = macroRW

object NotificationRequest:
  implicit val rw: RW[NotificationRequest] = macroRW

object NotificationResponse:
  implicit val rw: RW[NotificationResponse] = macroRW

object ErrorResponse:
  implicit val rw: RW[ErrorResponse] = macroRW