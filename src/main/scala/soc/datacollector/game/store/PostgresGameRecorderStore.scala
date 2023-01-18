package soc.datacollector.game.store

import io.github.gaelrenoux.tranzactio.doobie.{Database, tzio}
import Database.{Database => Db}
import doobie.Meta
import doobie.implicits.toSqlInterpolator
import soc.datacollector.GameId
import soc.datacollector.domain.{BOARD, MOVE, PlayerId}
import soc.datacollector.game.store.GameRecorderStore.Service
import soc.datacollector.game.{CompleteRecordingGameResult, StartGameData}
import zio.{Has, ULayer, ZIO, ZLayer}

object PostgresGameRecorderStore {

  val live: ULayer[Has[Service[Db]]] = ZLayer.succeed(new PostgresGameRecorderStore())

  implicit val moveMeta: Meta[MOVE] = Meta[Int].timap[Unit](_ => ())( _ => 1)
  implicit val boardMeta: Meta[BOARD] = Meta[Int].timap[Unit](_ => ())( _ => 1)


}

class PostgresGameRecorderStore extends GameRecorderStore.Service[Db] {
  override def startRecording(platform: String, playerIds: Seq[String], board: BOARD): ZIO[Db, Throwable, GameId] = {
    Database.transactionOrWiden(tzio(insertInProgressGameQuery(platform, playerIds, board).unique))
  }

  override def completeRecording(gameId: GameId, totalMoveCount: Int): ZIO[Db, Throwable, CompleteRecordingGameResult] = {
    Database.transactionOrWiden(for {
      delete <- tzio(deleteInProgressGameQuery(gameId).unique)
      numMoves <- ZIO.cond(delete.numMoves == totalMoveCount, delete.numMoves, new Exception("boom!"))
      _ <- tzio(insertCompletedGameQuery(delete).run)
      _ <- ZIO.collectAll_(delete.playerIds.zipWithIndex.map { case (playerId, position) =>
        tzio(insertPlayerGameResultQuery(delete.gameId, playerId, position).run)
      })
    } yield CompleteRecordingGameResult(numMoves))
  }

  def recordMove(gameId: GameId, previousMoveNumber: Int, move: MOVE) = {
    Database.transactionOrWiden(for {
      numMoveOpt <- tzio(insertMoveRecord(gameId, move, previousMoveNumber).option)
      numMove <- ZIO.getOrFailWith(new Exception("boom!"))(numMoveOpt)
      _ <- tzio(updateMoveNumInProgressGameQuery(gameId, numMove).run)
    } yield numMove)
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
