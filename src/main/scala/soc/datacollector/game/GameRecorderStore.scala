package soc.datacollector.game

import doobie.implicits.toSqlInterpolator
import doobie.util.transactor
import io.github.gaelrenoux.tranzactio.{DatabaseOps, DbException}
import io.github.gaelrenoux.tranzactio.doobie._
import soc.datacollector.GameId
import soc.datacollector.domain.{BOARD, MOVE, PlayerId}
import zio.{Has, IO, Task, ZIO}

object GameRecorderStore {

  trait Service {

    def startRecording(platform: String, playerIds: Seq[String], board: BOARD): IO[Throwable, GameId]

    def recordMove(gameId: GameId, move: MOVE): IO[Throwable, Unit] = ZIO.unit

    def completeRecording(gameId: GameId): IO[Throwable, CompleteRecordingGameResult]
  }

  class PostgresService(db: Has[DatabaseOps.ServiceOps[Connection]]) extends Service {

    override def startRecording(platform: String, playerIds: Seq[String], board: BOARD): IO[Throwable, GameId] = {

    }


    override def completeRecording(gameId: GameId): IO[Throwable, CompleteRecordingGameResult] = {
      Database.transactionOrWiden(for {
        delete <- tzio(deleteInProgressGameQuery(gameId).unique)
        numMoves = 0
        _ <- tzio(insertCompletedGameQuery(delete, numMoves).run)
        _ <- ZIO.collectAll_(delete.playerIds.zipWithIndex.map { case (playerId, position) =>
          tzio(insertPlayerGameResultQuery(delete.gameId, playerId, position).run)
        })
      } yield CompleteRecordingGameResult(numMoves)).provide(db)
    }

    private def insertInProgressGameQuery(platform: String, playerIds: Seq[PlayerId], board: BOARD): doobie.Query0[GameId] =
      sql"""INSERT INTO games_in_progress(platform, player_ids, board)
          VALUES($platform, $playerIds, $board)
          RETURNING game_id;
       """.stripMargin.query[GameId]

    private def insertMoveRecord(gameId: GameId, move: MOVE) = {
      sql"""INSERT INTO moves(game_id, move_data)
            VALUES($gameId, $move)
            RETURNING COUNT(S

         """
    }

    private def deleteInProgressGameQuery(gameId: GameId): doobie.Query0[StartGameData] = {
      sql"""DELETE FROM games_in_progress *
          WHERE game_id = $gameId
          RETURNING *;
       """.stripMargin.query[StartGameData]
    }

    private def insertCompletedGameQuery(game: StartGameData, numMoves: Int) = {
      val StartGameData(gameId, platform, _, board, createdAt) = game
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
}
