package com.oursky.todo

import sttp.client4.*
import upickle.default.*

class GeminiService(backend: SyncBackend, apiKey: String):
  private val model = "gemini-2.5-flash"
  private val geminiUrl = uri"https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=$apiKey"

  def generateSubtaskSuggestions(context: String, isSubtask: Boolean = false): List[String] =
    val prompt = if isSubtask then
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
    else
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

    val requestBody = ujson.Obj(
      "contents" -> ujson.Arr(
        ujson.Obj(
          "parts" -> ujson.Arr(
            ujson.Obj("text" -> prompt)
          )
        )
      ),
      "generationConfig" -> ujson.Obj(
        "temperature" -> 0.9,
        "topK" -> 40,
        "topP" -> 0.9,
        "maxOutputTokens" -> 512
      )
    )

    val request = basicRequest
      .post(geminiUrl)
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
        throw new RuntimeException(s"Gemini API request failed: ${err}")

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
      Some(json("candidates")(0)("content")("parts")(0)("text").str)
    catch
      case _: Exception => None

  private def cleanLine(line: String): String =
    line.replaceAll("^[-*•]\\s*", "")
        .replaceAll("^\\d+\\.\\s*", "")
        .replaceAll("^\"|\"$", "")
        .trim
