package soc.datacollector.game

import soc.datacollector.GameId
import soc.datacollector.domain.{BOARD, MOVE}
import soc.datacollector.player.PlayerId

final case class StartRecordingGame(playerIds: Map[Int, PlayerId], board: BOARD)

final case class RecordMove(gameId: String, move: MOVE)

final case class CompleteRecordingGame(gameId: GameId)