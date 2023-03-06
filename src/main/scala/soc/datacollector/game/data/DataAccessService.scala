package soc.datacollector.game.data

import doobie.util.transactor
import io.github.gaelrenoux.tranzactio.DatabaseOps
import io.github.gaelrenoux.tranzactio.doobie.Database
import soc.datacollector.game.{GameInfo, PlayerGameInfo}
import soc.datacollector.{AppError, GameId, GameNotFoundError, Move, StoreError}
import soc.datacollector.player.PlayerId
import zio.{Has, Task, ZIO, ZManaged}
import zio.stream.ZStream

object DataAccessService {

  def getGameInfoById(gameId: GameId): ZIO[Database.Database with Has[GameDataQueries.Service], AppError, GameInfo] = {
    val transaction = for {
      result <- GameDataQueries.getGameInfoById(gameId)
      gameInfo <- ZIO.getOrFailWith(GameNotFoundError(gameId))(result)
    } yield gameInfo
    Database.transactionR(transaction).mapError {
      case Left(ex) => StoreError(ex)
      case Right(e) => e
    }
  }

  def getGameInfoByPlayerId(playerId: PlayerId): ZStream[Database.Database with Has[GameDataQueries.Service], StoreError, PlayerGameInfo] = {
    val result = Database.transactionR(
      GameDataQueries
        .getGameInfoByPlayerId(playerId)
        .runCollect)
      .mapError {
        case Left(ex) => StoreError(ex)
        case Right(e) => e
      }
    ZStream.apply(ZManaged.effectTotal(result.mapError(Some.apply)))
  }

  def getMoves(gameId: GameId): ZStream[Database.Database with Has[GameDataQueries.Service], StoreError, Move] = {
    val result = Database.transactionR(
      GameDataQueries
        .getMoves(gameId)
        .runCollect)
      .mapError {
        case Left(ex) => StoreError(ex)
        case Right(e) => e
      }
    ZStream.apply(ZManaged.effectTotal(result.mapError(Some.apply)))
  }
}
