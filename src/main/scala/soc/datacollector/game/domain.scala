package soc.datacollector.game

import soc.datacollector.{Board, GameId, Move}
import soc.datacollector.player.PlayerId

import java.time.OffsetDateTime

final case class StartRecordingGame(playerIds: Map[Int, PlayerId], board: Board)

final case class StartGameData(gameId: GameId, platform: String, playerIds: Seq[PlayerId], board: Board, numMoves: Int, createdAt: OffsetDateTime)

final case class RecordMove(gameId: String, move: Move)

final case class CompleteRecordingGame(gameId: GameId)

final case class CompleteRecordingGameResult(numMoves: Int)

final case class LatestMove(gameId: GameId, platform: String, playerIds: Seq[PlayerId], board: Board, createdAt: OffsetDateTime, latestMoveNumber: Int, move: Move, played: OffsetDateTime)