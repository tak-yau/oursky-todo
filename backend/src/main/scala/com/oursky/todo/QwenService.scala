package com.oursky.todo

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.apache.pekko.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken, RawHeader}
import io.circe.{Json, parser}
import scala.concurrent.{Future, ExecutionContext}
import org.apache.pekko.stream.Materializer
import org.slf4j.LoggerFactory

class QwenService(apiKey: String)(implicit system: ActorSystem, mat: Materializer) {
  private val logger = LoggerFactory.getLogger(classOf[QwenService])
  private val model = "qwen/qwen3.6-plus:free"
  private val apiUrl = "https://openrouter.ai/api/v1/chat/completions"

  implicit val ec: ExecutionContext = system.dispatcher

  def generateSubtaskSuggestions(context: String, isSubtask: Boolean = false): Future[List[String]] = {
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
      "temperature" -> Json.fromDoubleOrNull(0.9),
      "max_tokens" -> Json.fromInt(512)
    )

    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = apiUrl,
      entity = HttpEntity(ContentTypes.`application/json`, requestBody.noSpaces),
      headers = Seq(
        Authorization(OAuth2BearerToken(apiKey)),
        RawHeader("HTTP-Referer", "https://github.com/tak-yau/oursky-todo"),
        RawHeader("X-Title", "Oursky Todo App")
      )
    )

    for {
      body <- fetchResponseBody(request)
      json <- Future.fromTry(parser.parse(body).toTry)
      suggestions <- Future.fromTry(extractSuggestions(json, body))
    } yield suggestions
  }

  protected def fetchResponseBody(request: HttpRequest): Future[String] = {
    for {
      response <- Http().singleRequest(request)
      body <- Unmarshal(response).to[String]
    } yield body
  }

  private def extractSuggestions(json: Json, rawBody: String): scala.util.Try[List[String]] = {
    try {
      val text = extractTextFromResponse(json)

      text match {
        case Some(content) =>
          parseContent(content)

        case None =>
          logger.warn("Qwen: No text from standard path, trying fallbacks. Body: ${rawBody.take(200)}")
          tryFallbackExtraction(json, rawBody)
      }
    } catch {
      case e: Exception =>
        logger.error(s"Qwen extraction error: ${e.getMessage}")
        scala.util.Failure(new Throwable(s"Extraction error: ${e.getMessage}"))
    }
  }

  private def parseContent(content: String): scala.util.Try[List[String]] = {
    val cleaned = stripMarkdownFences(content)
    val jsonStart = cleaned.indexOf("[")
    val jsonEnd = cleaned.lastIndexOf("]") + 1

    if (jsonStart >= 0 && jsonEnd > jsonStart) {
      val jsonString = cleaned.substring(jsonStart, jsonEnd)
      parser.parse(jsonString).flatMap(_.as[List[String]]) match {
        case Right(list) if list.nonEmpty => scala.util.Success(list)
        case Right(_) => scala.util.Failure(new Throwable("Empty suggestions list"))
        case Left(_) => parseFallbackLines(cleaned)
      }
    } else {
      parseFallbackLines(cleaned)
    }
  }

  private def extractTextFromResponse(json: Json): Option[String] = {
    json.hcursor
      .downField("choices")
      .downArray
      .downField("message")
      .downField("content")
      .as[Option[String]]
      .toOption
      .flatten
  }

  private def tryFallbackExtraction(json: Json, rawBody: String): scala.util.Try[List[String]] = {
    val paths = Seq(
      () => json.hcursor.downField("output").as[Option[String]].toOption.flatten,
      () => json.hcursor.downField("text").as[Option[String]].toOption.flatten,
      () => json.hcursor.downField("response").as[Option[String]].toOption.flatten,
      () => json.hcursor.downField("result").as[Option[String]].toOption.flatten,
      () => json.hcursor.downField("message").downField("content").as[Option[String]].toOption.flatten
    )

    val pathResult = paths.flatMap(extract => extract()).find(_.nonEmpty).flatMap(text => parseContent(text).toOption)
    pathResult match {
      case Some(result) => scala.util.Success(result)
      case None =>
        val bracketStart = rawBody.indexOf("[")
        val bracketEnd = rawBody.lastIndexOf("]") + 1
        if (bracketStart >= 0 && bracketEnd > bracketStart && bracketEnd <= rawBody.length) {
          val candidate = rawBody.substring(bracketStart, bracketEnd)
          parser.parse(candidate).flatMap(_.as[List[String]]) match {
            case Right(list) if list.nonEmpty => scala.util.Success(list)
            case _ => fallbackFailure(rawBody)
          }
        } else {
          fallbackFailure(rawBody)
        }
    }
  }

  private def fallbackFailure(rawBody: String): scala.util.Try[List[String]] = {
    logger.error(s"Qwen: All extraction paths failed. Raw body: $rawBody")
    scala.util.Failure(new Throwable("No text content in response"))
  }

  private def parseFallbackLines(content: String): scala.util.Try[List[String]] = {
    val lines = content.split("\n").map(_.trim).filter { line =>
      val cleaned = line.stripPrefix("```json").stripPrefix("```").trim
      cleaned.startsWith("\"") || cleaned.startsWith("-") || cleaned.startsWith("*") || cleaned.matches("\\d+\\..*")
    }.take(5).toList

    if (lines.nonEmpty) {
      scala.util.Success(lines.map(cleanLine))
    } else {
      scala.util.Failure(new Throwable("No JSON array or list found in response"))
    }
  }

  private def stripMarkdownFences(content: String): String = {
    content
      .replaceFirst("```json\\n?", "")
      .replaceFirst("```\\n?", "")
      .replaceAll("\\n?```\\s*$", "")
      .trim
  }

  private def cleanLine(line: String): String = {
    line.replaceAll("^[-*•]\\s*", "")
        .replaceAll("^\\d+\\.\\s*", "")
        .replaceAll("^\"|\"$", "")
        .trim
  }
}