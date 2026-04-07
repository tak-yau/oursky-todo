package com.oursky.todo

import io.circe.{Json, parser}
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration

class GeminiService(client: HttpClient, apiKey: String) {
  private val model = "gemini-2.5-flash"
  private val geminiUrl = s"https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=$apiKey"

  def generateSubtaskSuggestions(context: String, isSubtask: Boolean = false): List[String] = {
    val prompt = if (isSubtask) {
      s"""|You are a helpful task planning assistant for Oursky Todo app.
          |
          |The user wants to break down this SUBTASK into smaller, more specific action steps:
          |"${context}"
          |
          |IMPORTANT:
          |- Generate exactly 5 specific action steps that are CHILDREN of this subtask
          |- Do NOT suggest sibling tasks or parent-level tasks
          |- Each step should be more granular and actionable than the subtask itself
          |- Return ONLY a valid JSON array of exactly 5 strings, nothing else
          |- Do not include any markdown, explanation, or text outside the JSON array
          |
          |Example: ["Step 1", "Step 2", "Step 3", "Step 4", "Step 5"]
          |""".stripMargin
    } else {
      s"""|You are a helpful task planning assistant for Oursky Todo app.
          |
          |The user wants to break down this TASK into major subtasks:
          |"${context}"
          |
          |IMPORTANT:
          |- Generate exactly 5 major phases/steps to complete this task
          |- Each subtask should be a significant milestone
          |- Order them logically from start to finish
          |- Return ONLY a valid JSON array of exactly 5 strings, nothing else
          |- Do not include any markdown, explanation, or text outside the JSON array
          |
          |Example: ["Step 1", "Step 2", "Step 3", "Step 4", "Step 5"]
          |""".stripMargin
    }

    val requestBody = s"""{
      "contents": [{
        "parts": [{"text": ${circeToJson(prompt)}}]
      }],
      "generationConfig": {
        "temperature": 0.9,
        "topK": 40,
        "topP": 0.9,
        "maxOutputTokens": 512
      }
    }"""

    val request = HttpRequest.newBuilder()
      .uri(URI.create(geminiUrl))
      .timeout(Duration.ofSeconds(30))
      .header("Content-Type", "application/json")
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
    val text1 = for {
      candidates <- json.hcursor.downField("candidates").downArray.downField("content").downField("parts").downArray.downField("text").as[Option[String]].toOption
      text <- candidates
    } yield text

    if (text1.isDefined) return text1

    val text2 = json.hcursor
      .downField("candidates")
      .downArray
      .downField("content")
      .downField("parts")
      .downArray
      .downField("text")
      .as[Option[String]]
      .toOption
      .flatten

    if (text2.isDefined) return text2

    None
  }

  private def cleanLine(line: String): String = {
    line.replaceAll("^[-*•]\\s*", "")
        .replaceAll("^\\d+\\.\\s*", "")
        .replaceAll("^\"|\"$", "")
        .trim
  }
}
