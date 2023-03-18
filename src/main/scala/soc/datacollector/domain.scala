package soc.datacollector

import io.soc.core.base.board.BaseBoard
import io.soc.data.game_data_core.MoveEvent

final case class GameId(id: Int) extends AnyVal

final case class Move(data: MoveEvent) extends AnyVal

final case class Board(data: BaseBoard) extends AnyVal




