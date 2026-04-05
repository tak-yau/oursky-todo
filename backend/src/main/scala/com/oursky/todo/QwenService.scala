package com.oursky.todo

import cats.effect.IO
import io.circe.{Json, parser}
import org.http4s.client.Client
import org.http4s.{Method, Request, Uri, Headers, Header}
import org.http4s.headers.Authorization
import org.http4s.circe._
import io.circe.generic.auto._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

class QwenService(client: Client[IO], apiKey: String) {
  private val model = "qwen/qwen3.6-plus:free"
  private val apiUrl = "https://openrouter.ai/api/v1/chat/completions"

  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  def generateSubtaskSuggestions(context: String, isSubtask: Boolean = false): IO[List[String]] = {
    val prompt = if (isSubtask) {
      s"""You are a helpful task planning assistant for Oursky Todo app.

The user wants to break down this SUBTASK into smaller, more specific action steps:
"${context}"

IMPORTANT:
- Generate exactly 5 specific action steps that are CHILDREN of this subtask
- Do NOT suggest sibling tasks or parent-level tasks
- Each step should be more granular and actionable than the subtask itself
- Return ONLY a valid JSON array of exactly 5 strings, nothing else
- Do not include any markdown, explanation, or text outside the JSON array

Example: ["Step 1", "Step 2", "Step 3", "Step 4", "Step 5"]"""
    } else {
      s"""You are a helpful task planning assistant for Oursky Todo app.

The user wants to break down this TASK into major subtasks:
"${context}"

IMPORTANT:
- Generate exactly 5 major phases/steps to complete this task
- Each subtask should be a significant milestone
- Order them logically from start to finish
- Return ONLY a valid JSON array of exactly 5 strings, nothing else
- Do not include any markdown, explanation, or text outside the JSON array

Example: ["Step 1", "Step 2", "Step 3", "Step 4", "Step 5"]"""
    }

    val requestBody = Json.obj(
      "model" -> Json.fromString(model),
      "messages" -> Json.arr(
        Json.obj(
          "role" -> Json.fromString("user"),
          "content" -> Json.fromString(prompt)
        )
      ),
      "temperature" -> Json.fromDoubleOrNull(0.7),
      "max_tokens" -> Json.fromInt(512)
    )

    val request = Request[IO](Method.POST, Uri.unsafeFromString(apiUrl))
      .withHeaders(
        Header.Raw(org.typelevel.ci.CIString("Authorization"), s"Bearer $apiKey"),
        Header.Raw(org.typelevel.ci.CIString("HTTP-Referer"), "https://github.com/tak-yau/oursky-todo"),
        Header.Raw(org.typelevel.ci.CIString("X-Title"), "Oursky Todo App")
      )
      .withEntity(requestBody)

    for {
      _ <- logger.info(s"🤖 Requesting AI suggestions via Qwen for: $context (isSubtask: $isSubtask)")
      _ <- logger.info(s"📦 Using model: $model")

      response <- client.expect[Json](request)

      _ <- logger.info(s"✅ Got response from Qwen/OpenRouter API")
      suggestions <- IO.fromEither(extractSuggestions(response))
      _ <- logger.info(s"📝 Qwen generated ${suggestions.length} suggestions")
    } yield suggestions
  }

  private def extractSuggestions(json: Json): Either[Throwable, List[String]] = {
    try {
      val text = extractTextFromResponse(json)

      text match {
        case Some(content) =>
          val jsonStart = content.indexOf("[")
          val jsonEnd = content.lastIndexOf("]") + 1

          if (jsonStart >= 0 && jsonEnd > jsonStart) {
            val jsonString = content.substring(jsonStart, jsonEnd)
            parser.parse(jsonString).flatMap(_.as[List[String]]) match {
              case Right(list) if list.nonEmpty => Right(list)
              case Right(_) => Left(new Throwable("Empty suggestions list"))
              case Left(err) => Left(new Throwable(s"JSON parse error: ${err.getMessage}"))
            }
          } else {
            val lines = content.split("\n").map(_.trim).filter(line =>
              line.startsWith("\"") || line.startsWith("-") || line.startsWith("*") || line.matches("\\d+\\.")
            ).take(5).toList

            if (lines.nonEmpty) {
              Right(lines.map(cleanLine))
            } else {
              Left(new Throwable("No JSON array or list found in response"))
            }
          }

        case None =>
          Left(new Throwable("No text content in response"))
      }
    } catch {
      case e: Exception =>
        Left(new Throwable(s"Extraction error: ${e.getMessage}"))
    }
  }

  private def extractTextFromResponse(json: Json): Option[String] = {
    val text1 = for {
      choices <- json.hcursor.downField("choices").downArray.downField("message").downField("content").as[Option[String]].toOption
      t <- choices
    } yield t

    text1.orElse {
      json.hcursor
        .downField("choices")
        .downArray
        .downField("message")
        .downField("content")
        .as[Option[String]]
        .toOption
        .flatten
    }
  }

  private def cleanLine(line: String): String = {
    line.replaceAll("^[-*•]\\s*", "")
        .replaceAll("^\\d+\\.\\s*", "")
        .replaceAll("^\"|\"$", "")
        .trim
  }
}
