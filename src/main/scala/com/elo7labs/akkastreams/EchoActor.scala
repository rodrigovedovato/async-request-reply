package com.elo7labs.akkastreams
import akka.actor.Actor

class EchoActor extends Actor {
  override def receive: Receive = {
    case v: String => println(v)
    case StreamCompleted => println("Stream Completed")
  }
}
