package soc.datacollector.game.store

import doobie._
import doobie.implicits.toSqlInterpolator
import doobie.postgres.implicits._
import io.github.gaelrenoux.tranzactio.DbException
import io.github.gaelrenoux.tranzactio.doobie.{Database, tzio}
import io.soc.recorder.game_recorder.MoveEvent
import soc.datacollector.game.store.PostgresGameRecorderStore.PgEnv
import soc.datacollector.game.{CompleteRecordingGameResult, LatestMove, StartGameData}
import soc.datacollector.player.PlayerId
import soc.datacollector.{Board, GameId, Move}
import zio.ZIO
import zio.clock.Clock
import zio.console.Console

import java.time.{OffsetDateTime, ZoneId}

object PostgresGameRecorderStore {

  type PgEnv = Database.Database with Clock with Console

  object implicits {
    implicit val movePut: Put[Move] = Put[List[Byte]].tcontramap[Move](_.data.toByteArray.toList)
    implicit val moveGet: Get[Move] = Get[List[Byte]].tmap[Move](binaryList => Move(MoveEvent.parseFrom(binaryList.toArray)))

    implicit val boardPut: Put[Board] = Put[List[Byte]].tcontramap[Board](_ => List[Byte](1, 0, 1))
    implicit val boardGet: Get[Board] = Get[List[Byte]].tmap[Board](_ => Board())

    implicit def playerIdMeta: Meta[Seq[PlayerId]] = Meta[Array[String]].imap(_.toSeq.map(PlayerId.apply))(_.toArray.map(_.id))
  }
}

class PostgresGameRecorderStore extends GameRecorderStore.Service[PgEnv] {

  import PostgresGameRecorderStore.implicits._

  private val currentTime = zio.clock.instant.map(OffsetDateTime.ofInstant(_, ZoneId.systemDefault()))

  override def startRecording(platform: String, playerIds: Seq[PlayerId], board: Board): ZIO[PgEnv, Throwable, GameId] = {

    val transaction = for {
      startTime <- currentTime
      result <- tzio(insertInProgressGameQuery(platform, playerIds, board, startTime).unique)
    } yield result
    Database.transactionOrWidenR(transaction)
  }

  override def completeRecording(gameId: GameId, totalMoveCount: Int): ZIO[PgEnv, Throwable, CompleteRecordingGameResult] = {
    val transaction = for {
      endTime <- currentTime
      delete <- tzio(deleteInProgressGameQuery(gameId).unique)
      numMoves <- ZIO.cond(delete.numMoves == totalMoveCount, delete.numMoves, DbException.Wrapped(new Exception("boom!")))
      _ <- tzio(insertCompletedGameQuery(delete, endTime).run)
      _ <- ZIO.collectAll_(delete.playerIds.zipWithIndex.map { case (playerId, position) =>
        tzio(insertPlayerGameResultQuery(delete.gameId, playerId, position).run)
      })
    } yield CompleteRecordingGameResult(numMoves)

    Database.transactionOrWidenR(transaction)
  }

  def recordMove(gameId: GameId, previousMoveNumber: Int, move: Move): ZIO[PgEnv, Throwable, Int] = {
    val transaction = for {
      time <- currentTime
      numMove <- tzio(insertMoveRecord(gameId, move, previousMoveNumber, time).unique)
      _ <- tzio(updateMoveNumInProgressGameQuery(gameId, numMove).run)
    } yield numMove

    Database.transactionOrWidenR(transaction)
  }

  override def getLastRecordedMove(gameId: GameId): ZIO[PgEnv, Throwable, Option[LatestMove]] = {
    val transaction = for {
      result <- tzio(getLatestMoveForGameId(gameId).option)
    } yield result
    Database.transactionOrWidenR(transaction)
  }


  private def insertInProgressGameQuery(platform: String, playerIds: Seq[PlayerId], board: Board, startTime: OffsetDateTime): doobie.Query0[GameId] =
    sql"""INSERT INTO games_in_progress(platform, player_ids, board, created_at)
        VALUES($platform, $playerIds, $board, $startTime)
        RETURNING game_id;
     """.stripMargin.query[GameId]

  private def insertMoveRecord(gameId: GameId, move: Move, previousMoveNum: Int, currentTime: OffsetDateTime) = {
    sql"""INSERT INTO moves
          (SELECT p.game_id, p.num_moves + 1, $move, $currentTime FROM games_in_progress AS p
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
    sql"""DELETE FROM games_in_progress * AS g
        WHERE g.game_id = $gameId
        RETURNING g.game_id, g.platform, g.player_ids, g.board, g.num_moves, g.created_at;
     """.stripMargin.query[StartGameData]
  }

  private def insertCompletedGameQuery(game: StartGameData, endTime: OffsetDateTime) = {
    val StartGameData(gameId, platform, _, board, numMoves, createdAt) = game
    sql"""INSERT INTO completed_games(game_id, platform, board, num_moves, created_at, completed_at)
        VALUES($gameId, $platform, $board, $numMoves, $createdAt, $endTime);
     """.stripMargin.update
  }

  private def insertPlayerGameResultQuery(gameId: GameId, playerId: PlayerId, position: Int) = {
    sql"""INSERT INTO player_game_results(game_id, player_id, player_number)
        VALUES($gameId, $playerId, $position);
     """.stripMargin.update
  }

  private val selectLatestMoves = {
    sql"""SELECT g.game_id, g.platform, g.player_ids, g.board, g.created_at, m.move_number, m.move_data, m.created_at
          FROM games_in_progress g JOIN moves m ON g.game_id = m.game_id AND g.num_moves = m.move_number """.stripMargin
  }

  private def getLatestMoveForGameId(gameId: GameId) = (selectLatestMoves ++ Fragments.whereAnd(fr"g.game_id = $gameId")).query[LatestMove]

  //private def getStaleLatestMoves(staleMoveTime: OffsetDateTime) = (selectLatestMoves ++ Fragments.whereAnd(fr"m.created_at < $staleMoveTime")).query[LatestMove]

}
