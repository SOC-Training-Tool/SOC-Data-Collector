package soc.datacollector

import zio.{URIO}
import zio.clock.Clock

import java.time.{OffsetDateTime, ZoneId}

object Utils {

  val currentTime: URIO[Clock, OffsetDateTime] = zio.clock.instant.map(OffsetDateTime.ofInstant(_, ZoneId.of("UTC")))

}
