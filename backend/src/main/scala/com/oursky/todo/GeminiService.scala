package com.oursky.todo

import cats.effect.IO
import io.circe.{Json, parser}
import org.http4s.client.Client
import org.http4s.{Method, Request, Uri, Status}
import org.http4s.circe._
import io.circe.generic.auto._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

class GeminiService(client: Client[IO], apiKey: String) {
  // Gemini 2.5 Flash
  private val model = "gemini-2.5-flash"
  private val geminiUrl = s"https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=$apiKey"
  
  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  def generateSubtaskSuggestions(context: String, isSubtask: Boolean = false): IO[List[String]] = {
    val prompt = if (isSubtask) {
      s"""|You are a helpful task planning assistant for Oursky Todo app.
          |
          |The user wants to break down this SUBTASK into smaller, more specific action steps:
          |"${context}"
          |
          |IMPORTANT: 
          |- Generate 5 specific action steps that are CHILDREN of this subtask
          |- Do NOT suggest sibling tasks or parent-level tasks
          |- Each step should be more granular and actionable than the subtask itself
          |- Return ONLY a JSON array of strings, nothing else
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
          |- Generate 5 major phases/steps to complete this task
          |- Each subtask should be a significant milestone
          |- Order them logically from start to finish
          |- Return ONLY a JSON array of strings, nothing else
          |
          |Example: ["Step 1", "Step 2", "Step 3", "Step 4", "Step 5"]
          |""".stripMargin
    }

    val requestBody = Json.obj(
      "contents" -> Json.arr(
        Json.obj(
          "parts" -> Json.arr(
            Json.obj("text" -> Json.fromString(prompt))
          )
        )
      ),
      "generationConfig" -> Json.obj(
        "temperature" -> Json.fromDoubleOrNull(0.7),
        "topK" -> Json.fromInt(40),
        "topP" -> Json.fromDoubleOrNull(0.9),
        "maxOutputTokens" -> Json.fromInt(512)
      )
    )

    val request = Request[IO](Method.POST, Uri.unsafeFromString(geminiUrl))
      .withEntity(requestBody)

    for {
      _ <- logger.info(s"🤖 Requesting AI suggestions for: $context (isSubtask: $isSubtask)")
      _ <- logger.info(s"📦 Using model: $model")
      
      response <- client.expect[Json](request)
      
      _ <- logger.info(s"✅ Got response from Gemini API")
      suggestions <- IO.fromEither(extractSuggestions(response))
      _ <- logger.info(s"📝 Generated ${suggestions.length} suggestions")
    } yield suggestions
  }.handleErrorWith { error =>
    logger.error(error)(s"❌ AI generation failed, using fallback") *>
    IO.pure(List(
      s"Break down '${context}' into smaller steps",
      s"Research best practices for '${context}'",
      s"Gather required resources and tools",
      s"Create a timeline and schedule",
      s"Execute and track progress"
    ))
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
    // Path 1: Standard Gemini structure
    val text1 = for {
      candidates <- json.hcursor.downField("candidates").downArray.downField("content").downField("parts").downArray.downField("text").as[Option[String]].toOption
      text <- candidates
    } yield text
    
    if (text1.isDefined) return text1
    
    // Path 2: Alternative structure
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