package soc.datacollector

import io.github.gaelrenoux.tranzactio.DbException
import io.grpc.Status

sealed trait AppError

final case class GameNotFoundError(gameId: GameId) extends AppError

final case class MoveMismatchError(gameId: GameId, expectedMoveNumber: Int, providedMoveNumber: Int) extends AppError

final case class StoreError(ex: DbException) extends AppError

object handler {

  def mapError = (_: AppError) match {
    case GameNotFoundError(gameId) => Status.NOT_FOUND
    case StoreError(_) => Status.INTERNAL
  }

}