package com.bcrabbe.skat.client;

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import scala.io.StdIn

object SkatClient {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("SkatClient")

    // // implicit val httpSystem = ActorSystem(Behaviors.empty, "my-system")
    // // needed for the future flatMap/onComplete in the end
    // // implicit val executionContext = system.executionContext
    // implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

    // val route =
    //   path("hello") {
    //     get {
    //       complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>"))
    //     }
    //   }

    // val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)


    system.actorOf(PlayerActor.props)
    // println(s"Server now online. Please navigate to http://localhost:8080/hello\nPress RETURN to stop...")
    // StdIn.readLine() // let it run until user presses return
    // bindingFuture
    //   .flatMap(_.unbind()) // trigger unbinding from the port
    //   .onComplete(_ => system.terminate()) // and shutdown when done
  }

}
