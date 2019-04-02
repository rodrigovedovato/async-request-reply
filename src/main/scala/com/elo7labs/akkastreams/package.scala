package com.elo7labs

package object akkastreams {
  trait StreamSignal
  case object StreamCompleted extends StreamSignal
}
