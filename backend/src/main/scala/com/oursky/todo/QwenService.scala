package com.oursky.todo

import sttp.client4.*
import upickle.default.*

class QwenService(backend: SyncBackend, apiKey: String):
  private val model = "qwen/qwen3.6-plus:free"
  private val apiUrl = uri"https://openrouter.ai/api/v1/chat/completions"

  def generateSubtaskSuggestions(context: String, isSubtask: Boolean = false): List[String] =
    val prompt = if isSubtask then
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
    else
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

    val requestBody = ujson.Obj(
      "model" -> model,
      "messages" -> ujson.Arr(
        ujson.Obj("role" -> "user", "content" -> prompt)
      ),
      "temperature" -> 0.9,
      "max_tokens" -> 512
    )

    val request = basicRequest
      .post(apiUrl)
      .header("Authorization", s"Bearer $apiKey")
      .header("HTTP-Referer", "https://github.com/tak-yau/oursky-todo")
      .header("X-Title", "Oursky Todo App")
      .body(ujson.write(requestBody))
      .contentType("application/json")

    val response = request.send(backend)
    response.body match
      case Right(jsonStr) =>
        val json = upickle.default.read[ujson.Value](jsonStr)
        extractSuggestions(json) match
          case Right(suggestions) => suggestions
          case Left(err) => throw err
      case Left(err) =>
        throw new RuntimeException(s"Qwen API request failed: ${err}")

  private def extractSuggestions(json: ujson.Value): Either[Throwable, List[String]] =
    try
      val text = extractTextFromResponse(json)
      text match
        case Some(content) =>
          val jsonStart = content.indexOf("[")
          val jsonEnd = content.lastIndexOf("]") + 1
          if jsonStart >= 0 && jsonEnd > jsonStart then
            val jsonString = content.substring(jsonStart, jsonEnd)
            try
              val parsed = upickle.default.read[List[String]](jsonString)
              if parsed.nonEmpty then Right(parsed)
              else Left(new Throwable("Empty suggestions list"))
            catch
              case e: Exception => Left(new Throwable(s"JSON parse error: ${e.getMessage}"))
          else
            val lines = content.split("\n").map(_.trim).filter(line =>
              line.startsWith("\"") || line.startsWith("-") || line.startsWith("*") || line.matches("\\d+\\.")
            ).take(5).toList
            if lines.nonEmpty then Right(lines.map(cleanLine))
            else Left(new Throwable("No JSON array or list found in response"))
        case None =>
          Left(new Throwable("No text content in response"))
    catch
      case e: Exception => Left(new Throwable(s"Extraction error: ${e.getMessage}"))

  private def extractTextFromResponse(json: ujson.Value): Option[String] =
    try
      Some(json("choices")(0)("message")("content").str)
    catch
      case _: Exception => None

  private def cleanLine(line: String): String =
    line.replaceAll("^[-*•]\\s*", "")
        .replaceAll("^\\d+\\.\\s*", "")
        .replaceAll("^\"|\"$", "")
        .trim
