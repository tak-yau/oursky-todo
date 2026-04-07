package com.oursky.todo

import io.circe.{Json, parser}
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration

class QwenService(client: HttpClient, apiKey: String) {
  private val model = "qwen/qwen3.6-plus:free"
  private val apiUrl = "https://openrouter.ai/api/v1/chat/completions"

  def generateSubtaskSuggestions(context: String, isSubtask: Boolean = false): List[String] = {
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

    val requestBody = s"""{
      "model": "$model",
      "messages": [{"role": "user", "content": ${circeToJson(prompt)}}],
      "temperature": 0.9,
      "max_tokens": 512
    }"""

    val request = HttpRequest.newBuilder()
      .uri(URI.create(apiUrl))
      .timeout(Duration.ofSeconds(30))
      .header("Content-Type", "application/json")
      .header("Authorization", s"Bearer $apiKey")
      .header("HTTP-Referer", "https://github.com/tak-yau/oursky-todo")
      .header("X-Title", "Oursky Todo App")
      .POST(HttpRequest.BodyPublishers.ofString(requestBody))
      .build()

    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    val json = parser.parse(response.body()).getOrElse(Json.Null)
    extractSuggestions(json) match {
      case Right(suggestions) => suggestions
      case Left(err) => throw err
    }
  }

  private def circeToJson(s: String): String = {
    val escaped = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
    s""""$escaped""""
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
    json.hcursor
      .downField("choices")
      .downArray
      .downField("message")
      .downField("content")
      .as[Option[String]]
      .toOption
      .flatten
  }

  private def cleanLine(line: String): String = {
    line.replaceAll("^[-*•]\\s*", "")
        .replaceAll("^\\d+\\.\\s*", "")
        .replaceAll("^\"|\"$", "")
        .trim
  }
}
