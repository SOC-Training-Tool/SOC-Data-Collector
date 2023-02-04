package soc.datacollector

import io.soc.recorder.game_recorder.MoveEvent

final case class GameId(id: Int) extends AnyVal

final case class Move(data: MoveEvent) extends AnyVal

final case class Board(data: Unit) extends AnyVal




