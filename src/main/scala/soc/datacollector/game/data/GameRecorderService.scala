package soc.datacollector.game.data

import doobie.util.transactor
import io.github.gaelrenoux.tranzactio.DatabaseOps
import io.github.gaelrenoux.tranzactio.doobie.Database
import soc.datacollector.Utils.currentTime
import soc.datacollector.game.{CompleteRecordingGameResult, LatestMove}
import soc.datacollector.player.PlayerId
import soc.datacollector.{AppError, Board, GameId, GameNotFoundError, Move, MoveMismatchError, StoreError, Utils, handler}
import zio.clock.Clock
import zio.{Has, Task, ZIO}

object GameRecorderService {

  def startRecordingGame(platform: String, playerIds: List[PlayerId], board: Board): ZIO[Database.Database with Clock with Has[GameDataQueries.Service], StoreError, GameId] = {
    val transaction = for {
      startTime <- currentTime
      result <- GameDataQueries.startRecording(platform, playerIds, board, startTime)
    } yield result
    Database.transactionR(transaction).mapError {
      case Left(ex) => StoreError(ex)
      case Right(e) => e
    }
  }

  def completeRecordingGame(gameId: GameId, totalMoveCount: Int): ZIO[Database.Database with Clock with Has[GameDataQueries.Service], AppError, CompleteRecordingGameResult] = {
    val transaction = for {
      endTime <- currentTime
      playerIdsOpt <- GameDataQueries.moveGameRecording(gameId, totalMoveCount, endTime)

      result <- ZIO.getOrFailWith(GameNotFoundError(gameId))(playerIdsOpt)
      playerIds <- ZIO.getOrFailWith(
        MoveMismatchError(gameId, result.previousMoveNumber, totalMoveCount))(result.result)
      _ <- GameDataQueries.moveRecordedMoves(gameId).runDrain
      _ <- ZIO.foreach(playerIds.zipWithIndex) { case (id, pos) =>
        GameDataQueries.insertPlayerGameInfo(gameId, id, pos)
      }
    } yield CompleteRecordingGameResult(totalMoveCount)
    Database.transactionR(transaction).mapError {
      case Left(ex) => StoreError(ex)
      case Right(e) => e
    }
  }

  def recordMove(gameId: GameId, previousMoveNumber: Int, move: Move): ZIO[Database.Database with Clock with Has[GameDataQueries.Service], AppError, Int] = {
    val transaction = for {
      moveTime <- currentTime
      moveNumberOpt <- GameDataQueries.insertMove(gameId, move, previousMoveNumber, moveTime)
      moveNumber <- ZIO.getOrFailWith(GameNotFoundError(gameId))(moveNumberOpt)
      result <- ZIO.getOrFailWith(
        MoveMismatchError(gameId, moveNumber.previousMoveNumber, previousMoveNumber))(moveNumber.result)
    } yield result
    Database.transactionR(transaction).mapError {
      case Left(ex) => StoreError(ex)
      case Right(e) => e
    }
  }

  def getLastRecordedMove(gameId: GameId): ZIO[Database.Database with Has[GameDataQueries.Service], AppError, LatestMove] = {
    val transaction = for {
      lastMoveOpt <- GameDataQueries.getLastRecordedMove(gameId)
      lastMove <- ZIO.getOrFailWith(GameNotFoundError(gameId))(lastMoveOpt)
    } yield lastMove
    Database.transactionR(transaction).mapError {
      case Left(ex) => StoreError(ex)
      case Right(e) => e
    }
  }
}
