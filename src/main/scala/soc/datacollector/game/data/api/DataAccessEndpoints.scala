package soc.datacollector.game.data.api

import io.github.gaelrenoux.tranzactio.doobie
import io.grpc.Status
import io.soc.data.game_data_service._
import io.soc.data.{game_data_core => api}
import soc.datacollector.game.data.{DataAccessService, GameDataQueries}
import soc.datacollector.player.PlayerId
import soc.datacollector.{GameId, handler}
import zio.stream.ZStream
import zio.{Has, ZIO}

object DataAccessEndpoints extends ZioGameDataService.ZGameDataService[doobie.Database.Database with Has[GameDataQueries.Service], Any] {

  override def getGameById(request: GetGameInfoByIdRequest): ZIO[doobie.Database.Database with Has[GameDataQueries.Service] with Any, Status, GetGameInfoResponse] = {
    for {
      gameId <- ZIO.getOrFailWith(Status.INVALID_ARGUMENT)(request.id).map(gId => GameId(gId.id.toInt))
      gameInfo <- DataAccessService.getGameInfoById(gameId).mapError(handler.mapError)
      responseGameId = Some(api.GameId(gameInfo.gameId.id.toString))
      (index, playerIds) = gameInfo.playerIds.zipWithIndex.unzip.swap
      responsePlayerIds = index.zip(playerIds.map(i => api.PlayerId(gameInfo.platform, i.id))).toMap
    } yield GetGameInfoResponse(responseGameId, gameInfo.platform, responsePlayerIds, Some(gameInfo.board.data))
  }

  override def getGamesByPlayer(request: GetGameInfoForPlayerRequest): ZStream[doobie.Database.Database with Has[GameDataQueries.Service] with Any, Status, GetGameInfoResponse] = {
    val playerId = ZIO.getOrFailWith(Status.INVALID_ARGUMENT)(request.id).map(pId => PlayerId(pId.userId))
    for {
      playerId <- ZStream.fromEffect(playerId)
      game <- DataAccessService.getGameInfoByPlayerId(playerId).mapError(handler.mapError)
      gameInfo = game.gameInfo

      responseGameId = Some(api.GameId(gameInfo.gameId.id.toString))
      (index, playerIds) = gameInfo.playerIds.zipWithIndex.unzip.swap
      responsePlayerIds = index.zip(playerIds.map(i => api.PlayerId(gameInfo.platform, i.id))).toMap
    } yield GetGameInfoResponse(responseGameId, gameInfo.platform, responsePlayerIds, Some(gameInfo.board.data))
  }

  override def getGameRawMoves(request: GetGameRawMovesRequest): ZStream[doobie.Database.Database with Has[GameDataQueries.Service] with Any, Status, GetGameRawMovesResponse] = {
    val gameId = ZIO.getOrFailWith(Status.INVALID_ARGUMENT)(request.id).map(gId => GameId(gId.id.toInt))
    for {
      gameId <- ZStream.fromEffect(gameId)
      move <- DataAccessService.getMoves(gameId).mapError(handler.mapError)
    } yield GetGameRawMovesResponse(Some(move.data))
  }
}
