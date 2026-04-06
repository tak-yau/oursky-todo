package com.oursky.todo.actors

import org.apache.pekko.actor.{Actor, ActorLogging, Props}
import scala.concurrent.ExecutionContext
import com.oursky.todo.{QwenService, GeminiService, AICommand, AIResponse}

class AISuggestionActor(
  qwenService: Option[QwenService],
  geminiService: Option[GeminiService]
)(implicit ec: ExecutionContext) extends Actor with ActorLogging {

  import AICommand._
  import AIResponse._

  override def receive: Receive = {
    case GetAISuggestions(context, isSubtask) =>
      log.info(s"Received GetAISuggestions for: $context")
      handleAISuggestions(context, isSubtask)
  }

  private def handleAISuggestions(context: String, isSubtask: Boolean): Unit = {
    val qwenFuture = qwenService.map(_.generateSubtaskSuggestions(context, isSubtask))
    val geminiFuture = geminiService.map(_.generateSubtaskSuggestions(context, isSubtask))
    val senderRef = sender()

    (qwenFuture, geminiFuture) match {
      case (Some(qf), Some(gf)) =>
        qf.onComplete {
          case scala.util.Success(suggestions) =>
            log.info("Qwen service succeeded")
            senderRef ! AISuggestionsResponse(suggestions)
          case scala.util.Failure(qwenErr) =>
            log.warning(s"Qwen failed: ${qwenErr.getMessage}, trying Gemini")
            gf.onComplete {
              case scala.util.Success(suggestions) =>
                log.info("Gemini fallback succeeded")
                senderRef ! AISuggestionsResponse(suggestions)
              case scala.util.Failure(geminiErr) =>
                log.error(s"Gemini also failed: ${geminiErr.getMessage}")
                senderRef ! AIErrorResponse(s"AI services unavailable: ${geminiErr.getMessage}")
            }
        }

      case (Some(qf), None) =>
        qf.onComplete {
          case scala.util.Success(suggestions) =>
            senderRef ! AISuggestionsResponse(suggestions)
          case scala.util.Failure(err) =>
            log.error(s"Qwen failed: ${err.getMessage}")
            senderRef ! AIErrorResponse(err.getMessage)
        }

      case (None, Some(gf)) =>
        gf.onComplete {
          case scala.util.Success(suggestions) =>
            senderRef ! AISuggestionsResponse(suggestions)
          case scala.util.Failure(err) =>
            log.error(s"Gemini failed: ${err.getMessage}")
            senderRef ! AIErrorResponse(err.getMessage)
        }

      case (None, None) =>
        log.warning("No AI service configured")
        senderRef ! AIErrorResponse("AI service not configured. Set QWEN_API_KEY or GEMINI_API_KEY")
    }
  }
}

object AISuggestionActor {
  def props(
    qwenService: Option[QwenService],
    geminiService: Option[GeminiService]
  )(implicit ec: ExecutionContext): Props = Props(new AISuggestionActor(qwenService, geminiService))
}
