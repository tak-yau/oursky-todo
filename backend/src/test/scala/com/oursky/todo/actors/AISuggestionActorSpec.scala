package com.oursky.todo.actors

import org.apache.pekko.actor.{ActorSystem, Props}
import org.apache.pekko.testkit.{TestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import com.oursky.todo.{QwenService, GeminiService, AICommand, AIResponse}
import org.mockito.Mockito.{mock, when}
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration._
import scala.language.reflectiveCalls

class AISuggestionActorSpec extends AnyWordSpec with BeforeAndAfterAll with Matchers {

  implicit val system: ActorSystem = ActorSystem("ai-suggestion-test-system")
  implicit val ec: ExecutionContext = system.dispatcher

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  "AISuggestionActor" should {

    "return error when no AI service configured" in {
      val actor = system.actorOf(AISuggestionActor.props(None, None), "ai1")
      val probe = TestProbe()

      actor.tell(AICommand.GetAISuggestions("test context", false), probe.ref)

      probe.expectMsgPF(5.seconds) {
        case response: AIResponse.AIErrorResponse =>
          response.error should include("not configured")
      }
    }

    "return suggestions from Qwen service" in {
      val mockQwen = mock(classOf[QwenService])
      when(mockQwen.generateSubtaskSuggestions("test", false)).thenReturn(Future.successful(List("Step 1", "Step 2", "Step 3")))

      val actor = system.actorOf(AISuggestionActor.props(Some(mockQwen), None), "ai2")
      val probe = TestProbe()

      actor.tell(AICommand.GetAISuggestions("test", false), probe.ref)

      probe.expectMsgPF(5.seconds) {
        case AIResponse.AISuggestionsResponse(suggestions) =>
          suggestions shouldBe List("Step 1", "Step 2", "Step 3")
      }
    }

    "return suggestions from Gemini service" in {
      val mockGemini = mock(classOf[GeminiService])
      when(mockGemini.generateSubtaskSuggestions("test", false)).thenReturn(Future.successful(List("A", "B", "C")))

      val actor = system.actorOf(AISuggestionActor.props(None, Some(mockGemini)), "ai3")
      val probe = TestProbe()

      actor.tell(AICommand.GetAISuggestions("test", false), probe.ref)

      probe.expectMsgPF(5.seconds) {
        case AIResponse.AISuggestionsResponse(suggestions) =>
          suggestions shouldBe List("A", "B", "C")
      }
    }

    "return suggestions from Qwen when both services configured" in {
      val mockQwen = mock(classOf[QwenService])
      val mockGemini = mock(classOf[GeminiService])
      when(mockQwen.generateSubtaskSuggestions("test", false)).thenReturn(Future.successful(List("Qwen1", "Qwen2")))
      when(mockGemini.generateSubtaskSuggestions("test", false)).thenReturn(Future.successful(List("Gemini1", "Gemini2")))

      val actor = system.actorOf(AISuggestionActor.props(Some(mockQwen), Some(mockGemini)), "ai4")
      val probe = TestProbe()

      actor.tell(AICommand.GetAISuggestions("test", false), probe.ref)

      probe.expectMsgPF(5.seconds) {
        case AIResponse.AISuggestionsResponse(suggestions) =>
          suggestions shouldBe List("Qwen1", "Qwen2")
      }
    }

    "fallback to Gemini when Qwen fails" in {
      val mockQwen = mock(classOf[QwenService])
      val mockGemini = mock(classOf[GeminiService])
      when(mockQwen.generateSubtaskSuggestions("test", false)).thenReturn(Future.failed(new RuntimeException("Qwen error")))
      when(mockGemini.generateSubtaskSuggestions("test", false)).thenReturn(Future.successful(List("Fallback1", "Fallback2")))

      val actor = system.actorOf(AISuggestionActor.props(Some(mockQwen), Some(mockGemini)), "ai5")
      val probe = TestProbe()

      actor.tell(AICommand.GetAISuggestions("test", false), probe.ref)

      probe.expectMsgPF(5.seconds) {
        case AIResponse.AISuggestionsResponse(suggestions) =>
          suggestions shouldBe List("Fallback1", "Fallback2")
      }
    }

    "return error when both services fail" in {
      val mockQwen = mock(classOf[QwenService])
      val mockGemini = mock(classOf[GeminiService])
      when(mockQwen.generateSubtaskSuggestions("test", false)).thenReturn(Future.failed(new RuntimeException("Qwen down")))
      when(mockGemini.generateSubtaskSuggestions("test", false)).thenReturn(Future.failed(new RuntimeException("Gemini down")))

      val actor = system.actorOf(AISuggestionActor.props(Some(mockQwen), Some(mockGemini)), "ai6")
      val probe = TestProbe()

      actor.tell(AICommand.GetAISuggestions("test", false), probe.ref)

      probe.expectMsgPF(5.seconds) {
        case AIResponse.AIErrorResponse(error) =>
          error should include("AI services unavailable")
      }
    }

    "return error when Qwen fails and Gemini not configured" in {
      val mockQwen = mock(classOf[QwenService])
      when(mockQwen.generateSubtaskSuggestions("test", false)).thenReturn(Future.failed(new RuntimeException("Qwen error")))

      val actor = system.actorOf(AISuggestionActor.props(Some(mockQwen), None), "ai7")
      val probe = TestProbe()

      actor.tell(AICommand.GetAISuggestions("test", false), probe.ref)

      probe.expectMsgPF(5.seconds) {
        case AIResponse.AIErrorResponse(error) =>
          error shouldBe "Qwen error"
      }
    }

    "return error when Gemini fails and Qwen not configured" in {
      val mockGemini = mock(classOf[GeminiService])
      when(mockGemini.generateSubtaskSuggestions("test", false)).thenReturn(Future.failed(new RuntimeException("Gemini error")))

      val actor = system.actorOf(AISuggestionActor.props(None, Some(mockGemini)), "ai8")
      val probe = TestProbe()

      actor.tell(AICommand.GetAISuggestions("test", false), probe.ref)

      probe.expectMsgPF(5.seconds) {
        case AIResponse.AIErrorResponse(error) =>
          error shouldBe "Gemini error"
      }
    }
  }
}
