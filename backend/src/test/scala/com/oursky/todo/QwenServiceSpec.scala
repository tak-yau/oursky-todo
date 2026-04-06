package com.oursky.todo

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration._
import scala.language.reflectiveCalls
import scala.concurrent.{ExecutionContext, Future}

class QwenServiceSpec extends AnyWordSpec with BeforeAndAfterAll with Matchers {

  implicit val system: ActorSystem = ActorSystem("qwen-test-system")
  implicit val ec: ExecutionContext = system.dispatcher

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  private def createServiceWithMockedResponse(responseBody: String): QwenService = {
    new TestableQwenService("fake-key", responseBody)(system)
  }

  "QwenService" should {

    "parse standard OpenRouter response" in {
      val body = """{"choices":[{"message":{"content":"[\"Step 1\", \"Step 2\", \"Step 3\"]"}}]}"""
      val service = createServiceWithMockedResponse(body)

      val result = service.generateSubtaskSuggestions("test", false)
      val suggestions = scala.concurrent.Await.result(result, 5.seconds)

      suggestions shouldBe List("Step 1", "Step 2", "Step 3")
    }

    "parse response with markdown code fences" in {
      val body = """{"choices":[{"message":{"content":"```json\n[\"Step 1\", \"Step 2\", \"Step 3\"]\n```"}}]}"""
      val service = createServiceWithMockedResponse(body)

      val result = service.generateSubtaskSuggestions("test", false)
      val suggestions = scala.concurrent.Await.result(result, 5.seconds)

      suggestions shouldBe List("Step 1", "Step 2", "Step 3")
    }

    "parse response with extra text around JSON" in {
      val body = """{"choices":[{"message":{"content":"Here are your steps:\n[\"Step 1\", \"Step 2\", \"Step 3\"]\nHope this helps!"}}]}"""
      val service = createServiceWithMockedResponse(body)

      val result = service.generateSubtaskSuggestions("test", false)
      val suggestions = scala.concurrent.Await.result(result, 5.seconds)

      suggestions shouldBe List("Step 1", "Step 2", "Step 3")
    }

    "parse raw JSON array as fallback" in {
      val body = """["Step 1", "Step 2", "Step 3"]"""
      val service = createServiceWithMockedResponse(body)

      val result = service.generateSubtaskSuggestions("test", false)
      val suggestions = scala.concurrent.Await.result(result, 5.seconds)

      suggestions shouldBe List("Step 1", "Step 2", "Step 3")
    }

    "parse line-based fallback when no JSON array" in {
      val body = """{"choices":[{"message":{"content":"- Step 1\n- Step 2\n- Step 3"}}]}"""
      val service = createServiceWithMockedResponse(body)

      val result = service.generateSubtaskSuggestions("test", false)
      val suggestions = scala.concurrent.Await.result(result, 5.seconds)

      suggestions shouldBe List("Step 1", "Step 2", "Step 3")
    }

    "parse numbered line fallback" in {
      val body = """{"choices":[{"message":{"content":"1. Step 1\n2. Step 2\n3. Step 3"}}]}"""
      val service = createServiceWithMockedResponse(body)

      val result = service.generateSubtaskSuggestions("test", false)
      val suggestions = scala.concurrent.Await.result(result, 5.seconds)

      suggestions shouldBe List("Step 1", "Step 2", "Step 3")
    }

    "fail when response has no usable content" in {
      val body = """{"choices":[{"message":{"content":""}}]}"""
      val service = createServiceWithMockedResponse(body)

      val result = service.generateSubtaskSuggestions("test", false)
      assertThrows[Throwable] {
        scala.concurrent.Await.result(result, 5.seconds)
      }
    }

    "parse response with bullet points" in {
      val body = """{"choices":[{"message":{"content":"* Step 1\n* Step 2\n* Step 3"}}]}"""
      val service = createServiceWithMockedResponse(body)

      val result = service.generateSubtaskSuggestions("test", false)
      val suggestions = scala.concurrent.Await.result(result, 5.seconds)

      suggestions shouldBe List("Step 1", "Step 2", "Step 3")
    }
  }
}

class TestableQwenService(apiKey: String, mockResponse: String)(implicit system: ActorSystem) extends QwenService(apiKey)(system, null) {
  import scala.concurrent.Future
  import org.apache.pekko.http.scaladsl.model.HttpRequest

  override protected def fetchResponseBody(request: HttpRequest): Future[String] = {
    Future.successful(mockResponse)
  }
}
