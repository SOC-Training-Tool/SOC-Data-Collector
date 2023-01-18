package soc.datacollector.game

import soc.datacollector.GameId
import soc.datacollector.domain.{BOARD, MOVE}
import soc.datacollector.player.PlayerId

import java.time.OffsetDateTime

final case class StartRecordingGame(playerIds: Map[Int, PlayerId], board: BOARD)

final case class StartGameData(gameId: GameId, platform: String, playerIds: Seq[String], numMoves: Int, board: BOARD, createdAt: OffsetDateTime)

final case class RecordMove(gameId: String, move: MOVE)

final case class CompleteRecordingGame(gameId: GameId)

final case class CompleteRecordingGameResult(numMoves: Int)