package soc.datacollector.game.store

import io.github.gaelrenoux.tranzactio.doobie.{Connection, Database, tzio}
import doobie._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.postgres.pgisimplicits._
import doobie.implicits.toSqlInterpolator
import io.github.gaelrenoux.tranzactio.DbException
import io.soc.recorder.game_recorder.MoveEvent.Move
import soc.datacollector.GameId
import soc.datacollector.domain.{BOARD, MOVE, PlayerId}
import soc.datacollector.game.store.GameRecorderStore.Service
import soc.datacollector.game.store.PostgresGameRecorderStore.PgEnv
import soc.datacollector.game.{CompleteRecordingGameResult, StartGameData}
import zio.console.Console
import zio.{Has, Tag, Task, ULayer, ZIO, ZLayer, console}

object PostgresGameRecorderStore {

  type PgEnv = Database.Database with Console

  object implicits {
    implicit val movePut: Put[MOVE] = Put[List[Byte]].tcontramap[MOVE](_ => List[Byte](1, 0, 1))
    implicit val moveGet: Get[MOVE] = Get[List[Byte]].tmap(_ => ())

//    implicit val boardPut: Put[BOARD] = Put[List[Byte]].tcontramap[BOARD](_ => List[Byte](1, 0, 1))
//    implicit val boardGet: Get[BOARD] = Get[List[Byte]].tmap(_ => ())

    implicit val playerIdMeta: Meta[Seq[PlayerId]] = Meta[Array[PlayerId]].imap(_.toSeq)(_.toArray)
  }


}

class PostgresGameRecorderStore extends GameRecorderStore.Service[PgEnv] {

  import PostgresGameRecorderStore.implicits._

  override def startRecording(platform: String, playerIds: Seq[String], board: BOARD): ZIO[PgEnv, Throwable, GameId] = {
    val transaction = tzio(insertInProgressGameQuery(platform, playerIds, board).unique)
    Database.transactionOrWidenR(transaction)
  }

  override def completeRecording(gameId: GameId, totalMoveCount: Int): ZIO[PgEnv, Throwable, CompleteRecordingGameResult] = {
    val transaction = for {
      delete <- tzio(deleteInProgressGameQuery(gameId).unique)
      _ <- console.putStrLn(delete.toString)
      numMoves <- ZIO.cond(delete.numMoves == totalMoveCount, delete.numMoves, DbException.Wrapped(new Exception("boom!")))
      _ <- console.putStrLn(numMoves.toString)
      _ <- tzio(insertCompletedGameQuery(delete).run)
      _ <- ZIO.collectAll_(delete.playerIds.zipWithIndex.map { case (playerId, position) =>
        tzio(insertPlayerGameResultQuery(delete.gameId, playerId, position).run)
      })
    } yield CompleteRecordingGameResult(numMoves)

    Database.transactionOrWidenR(transaction)
  }

  def recordMove(gameId: GameId, previousMoveNumber: Int, move: MOVE): ZIO[PgEnv, Throwable, Int] = {
    val transaction = for {
      numMove <- tzio(insertMoveRecord(gameId, move, previousMoveNumber).unique)
      _ <- tzio(updateMoveNumInProgressGameQuery(gameId, numMove).run)
    } yield numMove

    Database.transactionOrWidenR(transaction)
  }

  private def insertInProgressGameQuery(platform: String, playerIds: Seq[PlayerId], board: BOARD): doobie.Query0[GameId] =
    sql"""INSERT INTO games_in_progress(platform, player_ids, board, num_moves)
        VALUES($platform, $playerIds, $board, 0)
        RETURNING game_id;
     """.stripMargin.query[GameId]

  private def insertMoveRecord(gameId: GameId, move: MOVE, previousMoveNum: Int) = {
    sql"""INSERT INTO moves
          (SELECT p.game_id, p.num_moves + 1, $move FROM games_in_progress AS p
          WHERE game_id = $gameId AND num_moves = $previousMoveNum)
          RETURNING move_number;
       """.stripMargin.query[Int]
  }

  private def updateMoveNumInProgressGameQuery(gameId: GameId, moveNumber: Int) = {
    sql"""UPDATE games_in_progress
          SET num_moves = $moveNumber
          WHERE game_id = $gameId;
       """.stripMargin.update
  }

  private def deleteInProgressGameQuery(gameId: GameId): doobie.Query0[StartGameData] = {
    sql"""DELETE FROM games_in_progress *
        WHERE game_id = $gameId
        RETURNING *;
     """.stripMargin.query[StartGameData]
  }

  private def insertCompletedGameQuery(game: StartGameData) = {
    val StartGameData(gameId, platform, _, numMoves, board, createdAt) = game
    sql"""INSERT INTO completed_games(game_id, platform, board, num_moves, created_at)
        VALUES($gameId, $platform, $board, $numMoves, $createdAt);
     """.stripMargin.update
  }

  private def insertPlayerGameResultQuery(gameId: GameId, playerId: PlayerId, position: Int) = {
    sql"""INSERT INTO player_game_results(game_id, player_id, player_number)
        VALUES($gameId, $playerId, $position);
     """.stripMargin.update
  }

}
