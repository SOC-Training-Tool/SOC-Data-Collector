package soc.datacollector.game.data.api

import io.github.gaelrenoux.tranzactio.doobie.Database
import io.grpc.Status
import io.soc.data.{game_data_core => api}
import io.soc.data.game_recorder_service.{CompleteRecordingRequest, CompleteRecordingResponse, GetLastRecordedMoveRequest, GetLastRecordedMoveResponse, RecordMoveRequest, RecordMoveResponse, StartRecordingRequest, ZioGameRecorderService}
import soc.datacollector.{Board, GameId, Move, handler}
import soc.datacollector.game.data.{GameDataQueries, GameRecorderService}
import soc.datacollector.player.PlayerId
import zio.clock.Clock
import zio.{Has, ZIO}

object GameRecorderEndpoints extends ZioGameRecorderService.ZGameRecorderService[Database.Database with Has[GameDataQueries.Service] with Clock, Any] {
  override def startRecording(request: StartRecordingRequest): ZIO[Database.Database with Has[GameDataQueries.Service] with Clock with Any, Status, api.GameId] = {
    for {
      board <- ZIO.getOrFailWith(Status.INVALID_ARGUMENT)(request.board)
      playerIds = request.playerPositionIdMap.toList.sortBy(_._1).map(i => PlayerId(i._2.userId))
      gameId <- GameRecorderService.startRecordingGame("", playerIds, Board(board)).mapError(handler.mapError)
    } yield api.GameId(gameId.id.toString)
  }

  override def recordMove(request: RecordMoveRequest): ZIO[Database.Database with Has[GameDataQueries.Service] with Clock with Any, Status, RecordMoveResponse] = {
    for {
      gameId <- ZIO.getOrFailWith(Status.INVALID_ARGUMENT)(request.id.map(gId => GameId(gId.id.toInt)))
      move <- ZIO.getOrFailWith(Status.INVALID_ARGUMENT)(request.move)
      result <- GameRecorderService.recordMove(gameId, request.previousMoveNumber, Move(move)).mapError(handler.mapError)
    } yield RecordMoveResponse(result)
  }

  override def completeRecording(request: CompleteRecordingRequest): ZIO[Database.Database with Has[GameDataQueries.Service] with Clock with Any, Status, CompleteRecordingResponse] = {
    for {
      gameId <- ZIO.getOrFailWith(Status.INVALID_ARGUMENT)(request.id.map(id => GameId(id.id.toInt)))
      result <- GameRecorderService.completeRecordingGame(gameId, request.numMoves).mapError(handler.mapError)
    } yield CompleteRecordingResponse(result.numMoves)
  }

  override def getLastRecordedMove(request: GetLastRecordedMoveRequest): ZIO[Database.Database with Has[GameDataQueries.Service] with Any, Status, GetLastRecordedMoveResponse] = {
    for {
      gameId <- ZIO.getOrFailWith(Status.INVALID_ARGUMENT)(request.id.map(id => GameId(id.id.toInt)))
      result <- GameRecorderService.getLastRecordedMove(gameId).mapError(handler.mapError)
    } yield GetLastRecordedMoveResponse(result.latestMoveNumber, Some(result.move.data))
  }
}
