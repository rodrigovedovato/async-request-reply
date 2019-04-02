package com.elo7labs.akkastreams

import akka.NotUsed
import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, Sink, Source}
import akka.stream.{ActorMaterializer, FlowShape}

import scala.concurrent.duration._
import scala.concurrent.Await

object AkkaStreamsTests extends App {
  implicit val actorSystem: ActorSystem = ActorSystem("AsyncRequestReply")
  implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()

  val element = "Han"

  val source: Source[String, NotUsed] = Source.single(element)
  val outputSink = Sink.actorRef(actorSystem.actorOf(Props[EchoActor]), StreamCompleted)

  val swApiFlow = Http().outgoingConnectionHttps("swapi.co")
  val omdbFlow = Http().outgoingConnection( "www.omdbapi.com")
  val punkApiFlow = Http().outgoingConnectionHttps("api.punkapi.com")

  val g = Flow.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
    import GraphDSL.Implicits._

    val ports: Int = 3

    val bcast = builder.add(Broadcast[String](ports))
    val merge = builder.add(Merge[String](ports))

    val swEndpointFlow = Flow[String].map(query => HttpRequest(uri = s"https://swapi.co/api/people/?format=json&search=$query"))
    val omdbEndpointFlow = Flow[String].map(query => HttpRequest(uri = s"http://www.omdbapi.com/?apikey=7a8b24ac&s=$query"))
    val punkApiEndpointFlow = Flow[String].map(query => HttpRequest(uri = s"https://api.punkapi.com/v2/beers?beer_name=$query"))

    val decodeFlow = Flow[HttpResponse].map(response => {
      Await.result(Unmarshal(response.entity).to[String], 5 seconds)
    })

    bcast ~> swEndpointFlow ~> swApiFlow ~> decodeFlow ~> merge
    bcast ~> omdbEndpointFlow ~> omdbFlow ~> decodeFlow ~> merge
    bcast ~> punkApiEndpointFlow ~> punkApiFlow ~> decodeFlow ~> merge

    FlowShape(bcast.in, merge.out)
  })

  source
    .via(g)
    .to(outputSink)
    .run()
}
