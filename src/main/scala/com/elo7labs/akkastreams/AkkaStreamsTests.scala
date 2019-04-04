package com.elo7labs.akkastreams

import akka.NotUsed
import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, MergePreferred, Sink, Source, ZipWith}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, FlowShape, Supervision}

import scala.concurrent.duration._
import scala.concurrent.Await

object AkkaStreamsTests extends App {
  implicit val actorSystem: ActorSystem = ActorSystem("AsyncRequestReply")

  implicit val actorMaterializer: ActorMaterializer = ActorMaterializer(
    ActorMaterializerSettings(actorSystem).withSupervisionStrategy(_ => Supervision.Resume)
  )

  val element = "Daredevil"

  val source: Source[String, NotUsed] = Source.single(element)
  val outputSink = Sink.actorRef(actorSystem.actorOf(Props[EchoActor]), StreamCompleted)

  val swApiFlow = Http().outgoingConnectionHttps("swapi.co")
  val omdbFlow = Http().outgoingConnection( "www.omdbapi.com")
  // val punkApiFlow = Http().outgoingConnectionHttps("api.punkapi.com")

  val g = Flow.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
    import GraphDSL.Implicits._

    val ports: Int = 2

    val bcast = builder.add(Broadcast[String](ports))
    val fallback = builder.add(MergePreferred[String](ports - 1))

    // val zipper = builder.add(ZipWith[String, String, String, String]((s1, s2, s3) => s1.concat(s2).concat(s3)))
    // val merge = builder.add(Merge[String](ports))

    //val swEndpointFlow = Flow[String].map(query => HttpRequest(uri = s"https://swapi.co/api/people/?format=json&search=$query"))
    val seriesEndpointFlow = Flow[String].map(query => HttpRequest(uri = s"http://www.omdbapi.com/?apikey=7a8b24ac&s=$query&type=series"))
    val moviesEndpointFlow = Flow[String].map(query => HttpRequest(uri = s"http://www.omdbapi.com/?apikey=7a8b24ac&s=$query&type=movies"))
    // val punkApiEndpointFlow = Flow[String].map(query => HttpRequest(uri = s"https://api.punkapi.com/v2/beers?beer_name=$query"))

    val decodeFlow = Flow[HttpResponse].map(response => {
      Await.result(Unmarshal(response.entity).to[String], 5 seconds)
    })

    //bcast ~> swEndpointFlow ~> swApiFlow ~> decodeFlow ~> fallback.preferred

    bcast ~> moviesEndpointFlow ~> omdbFlow ~> decodeFlow ~> fallback.preferred
    bcast ~> seriesEndpointFlow ~> omdbFlow ~> decodeFlow ~> fallback.inlets.head

    // bcast ~> punkApiEndpointFlow ~> punkApiFlow ~> decodeFlow ~> zipper.in2

    FlowShape(bcast.in, fallback.out)
  })

  source
    .via(g)
    .log("Logger")
    .to(outputSink)
    .run()
}
