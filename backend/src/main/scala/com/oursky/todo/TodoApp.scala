package com.oursky.todo

import cats.effect.*
import com.comcast.ip4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.server.middleware.CORS

object TodoApp extends IOApp.Simple {
  def run: IO[Unit] = {
    val apiKey = sys.env.getOrElse("GEMINI_API_KEY", "")
    
    if (apiKey.nonEmpty) {
      EmberClientBuilder.default[IO].build.use { client =>
        val todoService = TodoService()
        val geminiService = GeminiService(client, apiKey)
        val todoRoutes = TodoRoutes(todoService, Some(geminiService))
        
        EmberServerBuilder.default[IO]
          .withHost(host"0.0.0.0")
          .withPort(port"8080")
          .withHttpApp(CORS.policy(todoRoutes.routes.orNotFound))
          .build
          .use { server =>
            IO.println(s"🚀 Oursky Todo Backend started at http://${server.address.getHostString}:${server.address.getPort}") *>
            IO.println(s"🤖 AI suggestions enabled") *>
            IO.never
          }
      }
    } else {
      val todoService = TodoService()
      val todoRoutes = TodoRoutes(todoService, None)
      
      EmberServerBuilder.default[IO]
        .withHost(host"0.0.0.0")
        .withPort(port"8080")
        .withHttpApp(CORS.policy(todoRoutes.routes.orNotFound))
        .build
        .use { server =>
          IO.println(s"🚀 Oursky Todo Backend started at http://${server.address.getHostString}:${server.address.getPort}") *>
          IO.println(s"⚠️  GEMINI_API_KEY not set - AI suggestions disabled") *>
          IO.never
        }
    }
  }
}
