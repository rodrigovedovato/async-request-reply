package com.elo7labs

import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer.DrainingControl
import akka.kafka.{ConsumerSettings, ProducerMessage, ProducerSettings, Subscriptions}
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Keep
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import com.ovoenergy.kafka.serialization.circe._
import io.circe.generic.auto._
import org.apache.kafka.clients.consumer.ConsumerConfig

object AsyncRequestReply extends App {
  implicit val actorSystem: ActorSystem = ActorSystem("AsyncRequestReply")
  implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()

  val consumerConfig = actorSystem.settings.config.getConfig("akka.kafka.consumer")
  val producerConfig = actorSystem.settings.config.getConfig("akka.kafka.producer")

  case class Numbers(firstNumber: Int, secondNumber: Int, sum: Option[Int])

  val consumerSettings =
    ConsumerSettings(consumerConfig, new StringDeserializer, circeJsonDeserializer[Numbers])
      .withBootstrapServers("127.0.0.1:9092")
      .withGroupId("pandarequestreplygroup")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  val producerSettings =
    ProducerSettings(producerConfig, new StringSerializer, circeJsonSerializer[Numbers])
      .withBootstrapServers("127.0.0.1:9092")

  val control =
    Consumer
      .committableSource(consumerSettings, Subscriptions.topics("panda-request-topic"))
      .map { msg =>
        val oldNumber = msg.record.value()
        val newNumber =
          msg.record.value().copy(sum = Some((oldNumber.firstNumber + oldNumber.secondNumber) + 1))

        val r = new ProducerRecord("panda-requestreply-topic",
                                   null,
                                   null,
                                   msg.record.key,
                                   newNumber,
                                   msg.record.headers())

        ProducerMessage.single(r, msg.committableOffset)
      }
      .toMat(Producer.committableSink(producerSettings))(Keep.both)
      .mapMaterializedValue(DrainingControl.apply)
      .run()
}
