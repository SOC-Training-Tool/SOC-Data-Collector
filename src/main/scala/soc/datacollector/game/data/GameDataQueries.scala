package soc.datacollector.game.data

import doobie.{Fragments, Get, Meta, Put}
import doobie.implicits.toSqlInterpolator
import doobie.postgres.implicits._
import doobie.util.transactor
import io.github.gaelrenoux.tranzactio.DbException
import io.github.gaelrenoux.tranzactio.doobie.{Connection, TranzactIO, TranzactIOStream, tzio, tzioStream}
import io.soc.core.base.board.BaseBoard
import io.soc.data.game_data_core.MoveEvent
import soc.datacollector.game.{GameInfo, LatestMove, PlayerGameInfo}
import soc.datacollector.player.PlayerId
import soc.datacollector.{Board, GameId, Move, StoreError}
import zio.stream.ZStream
import zio.{Has, Task, ULayer, ZIO, ZLayer}

import java.time.OffsetDateTime

object GameDataQueries {

  final case class MovesOperationResult[A](previousMoveNumber: Int, result: Option[A])

  /**
   * --------------------------------------------------------------------------------------------
   * Data Recording Queries
   * --------------------------------------------------------------------------------------------
   */

  def startRecording(platform: String, playerIds: List[PlayerId], board: Board, currentTime: OffsetDateTime): ZIO[Connection with Has[Service], StoreError, GameId] =
    ZIO.service[Service].flatMap(_.startRecording(platform, playerIds, board, currentTime))

  def insertMove(gameId: GameId, move: Move, previousMoveNumber: Int, currentTime: OffsetDateTime): ZIO[Connection with Has[Service], StoreError, Option[MovesOperationResult[Int]]] =
    ZIO.service[Service].flatMap(_.insertMove(gameId, move, previousMoveNumber, currentTime))

  def moveGameRecording(gameId: GameId, expectedNumMoves: Int, currentTIme: OffsetDateTime): ZIO[Connection with Has[Service], StoreError, Option[MovesOperationResult[List[PlayerId]]]] =
    ZIO.service[Service].flatMap(_.moveGameRecording(gameId, expectedNumMoves, currentTIme))

  def moveRecordedMoves(gameId: GameId): ZStream[Connection with Has[Service], StoreError, Move] =
    ZStream.service[Service].flatMap(_.moveRecordedMoves(gameId))

  def insertPlayerGameInfo(gameId: GameId, playerId: PlayerId, position: Int): ZIO[Connection with Has[Service], StoreError, Int] =
    ZIO.service[Service].flatMap(_.insertPlayerGameInfo(gameId, playerId, position))

  def getLastRecordedMove(gameId: GameId): ZIO[Connection with Has[Service], StoreError, Option[LatestMove]] =
    ZIO.service[Service].flatMap(_.getLastRecordedMove(gameId))

  /***
   * --------------------------------------------------------------------------------------------
   * Data Access Queries
   * --------------------------------------------------------------------------------------------
   */

  def getGameInfoById(gameId: GameId) =
    ZIO.service[Service].flatMap(_.getGameInfoById(gameId))

  def getGameInfoByPlayerId(playerId: PlayerId) =
    ZStream.service[Service].flatMap(_.getGameInfoByPlayerId(playerId))

  def getMoves(gameId: GameId) =
    ZStream.service[Service].flatMap(_.getMoves(gameId))

  type DataQuery[A] = ZIO[Connection, StoreError, A]
  type DataQueryStream[A] = ZStream[Connection, StoreError, A]

  trait Service {

    /***
     * --------------------------------------------------------------------------------------------
     * Data Recording Queries
     * --------------------------------------------------------------------------------------------
     */

    def startRecording(platform: String, playerIds: List[PlayerId], board: Board, currentTime: OffsetDateTime): DataQuery[GameId]

    def insertMove(gameId: GameId, move: Move, previousMoveNumber: Int, currentTime: OffsetDateTime): DataQuery[Option[MovesOperationResult[Int]]]

    def moveGameRecording(gameId: GameId, expectedNumMoves: Int, currentTIme: OffsetDateTime): DataQuery[Option[MovesOperationResult[List[PlayerId]]]]

    def moveRecordedMoves(gameId: GameId): DataQueryStream[Move]

    def insertPlayerGameInfo(gameId: GameId, playerId: PlayerId, position: Int): DataQuery[Int]

    def getLastRecordedMove(gameId: GameId): DataQuery[Option[LatestMove]]

    /***
     * --------------------------------------------------------------------------------------------
     * Data Access Queries
     * --------------------------------------------------------------------------------------------
     */

    def getGameInfoById(gameId: GameId): DataQuery[Option[GameInfo]]

    def getGameInfoByPlayerId(playerId: PlayerId): DataQueryStream[PlayerGameInfo]

    def getMoves(gameId: GameId): DataQueryStream[Move]
  }

  val live: ULayer[Has[Service]] = ZLayer.succeed(new Service {

    implicit val movePut: Put[Move] = Put[List[Byte]].tcontramap[Move](_.data.toByteArray.toList)
    implicit val moveGet: Get[Move] = Get[List[Byte]].tmap[Move](binaryList => Move(MoveEvent.parseFrom(binaryList.toArray)))

    implicit val boardPut: Put[Board] = Put[List[Byte]].tcontramap[Board](b => BaseBoard.toByteArray(b.data).toList)
    implicit val boardGet: Get[Board] = Get[List[Byte]].tmap[Board](bytes => Board(BaseBoard.parseFrom(bytes.toArray)))

    implicit val playerIdMeta: Meta[List[PlayerId]] = Meta[Array[String]].imap(_.toList.map(PlayerId.apply))(_.toArray.map(_.id))

    override def startRecording(platform: String, playerIds: List[PlayerId], board: Board, currentTime: OffsetDateTime): DataQuery[GameId] = {
      tzio {
        sql"""INSERT INTO games_in_progress(platform, player_ids, board, created_at)
            VALUES($platform, $playerIds, $board, $currentTime)
            RETURNING game_id;
         """.stripMargin.query[GameId].unique
      }
    }.toStoreError

    private val selectLatestMoves = {
      sql"""SELECT g.game_id, g.platform, g.player_ids, g.board, g.created_at, m.move_number, m.move_data, m.created_at
            FROM games_in_progress g JOIN moves_in_progress m ON g.game_id = m.game_id AND g.num_moves = m.move_number """.stripMargin
    }

    override def getLastRecordedMove(gameId: GameId): DataQuery[Option[LatestMove]] = tzio {
      (selectLatestMoves ++ Fragments.whereAnd(fr"g.game_id = $gameId")).query[LatestMove].option
    }.toStoreError

    override def insertMove(gameId: GameId, move: Move, previousMoveNumber: Int, currentTime: OffsetDateTime): DataQuery[Option[MovesOperationResult[Int]]] = tzio {
      sql"""WITH select_move_number AS (
              SELECT num_moves AS old_num_moves
              FROM games_in_progress WHERE game_id = $gameId
            ), insert_move AS (
              INSERT INTO moves_in_progress
              (SELECT p.game_id, p.num_moves + 1, $move, $currentTime
                FROM games_in_progress p, select_move_number smn
                WHERE p.game_id = $gameId AND smn.old_num_moves = $previousMoveNumber)
              RETURNING move_number
            ), update_game AS (
              UPDATE games_in_progress
              SET num_moves = (SELECT move_number FROM insert_move)
              WHERE game_id = $gameId
              RETURNING num_moves
            ) SELECT smn.old_num_moves, ug.num_moves FROM select_move_number smn, update_game ug;
         """.stripMargin.query[MovesOperationResult[Int]].option
    }.toStoreError

    def moveGameRecording(gameId: GameId, expectedNumMoves: Int, currentTime: OffsetDateTime): DataQuery[Option[MovesOperationResult[List[PlayerId]]]] = tzio {
      sql"""WITH select_move_number AS (
              SELECT num_moves, game_id
              FROM games_in_progress WHERE game_id = $gameId
            ), recording AS (
              DELETE FROM games_in_progress g
              WHERE EXISTS (
                SELECT 1 FROM select_move_number smn
                WHERE g.game_id = $gameId AND smn.num_moves = $expectedNumMoves)
              RETURNING *
            ), completed AS (
              INSERT INTO games_completed
              SELECT g.game_id, g.platform, g.board, g.num_moves, g.created_at, $currentTime FROM recording g
            ) SELECT smn.num_moves, r.player_ids FROM select_move_number smn
              LEFT JOIN recording r
              ON smn.game_id = r.game_id;
         """.stripMargin.query[MovesOperationResult[List[PlayerId]]].option
    }.toStoreError

    override def moveRecordedMoves(gameId: GameId): DataQueryStream[Move] = tzioStream {
      sql"""WITH record AS (
              DELETE FROM moves_in_progress
              WHERE game_id = $gameId
              RETURNING *
            ), completed AS (
              INSERT INTO moves_completed SELECT * FROM record
            ) SELECT move_data FROM record ORDER BY move_number ASC ;
         """.stripMargin.query[Move].stream
    }.toStoreError

    override def insertPlayerGameInfo(gameId: GameId, playerId: PlayerId, position: Int): DataQuery[Int] = tzio {
      sql"""INSERT INTO player_game_results(game_id, player_id, player_number)
          VALUES($gameId, $playerId, $position);
       """.stripMargin.update.run
    }.toStoreError

    /***
     * --------------------------------------------------------------------------------------------
     * Data Access Queries
     * --------------------------------------------------------------------------------------------
     */

      private val selectGameInfo = {
        sql"""
            SELECT cd.game_id, cd.platform, array_agg(player_id ORDER BY player_number) AS player_ids, cd.board, cd.num_moves, cd.created_at, cd.completed_at
            FROM player_game_results pgr JOIN games_completed cd ON pgr.game_id = cd.game_id GROUP BY cd.game_id
           """.stripMargin
      }

    override def getGameInfoById(gameId: GameId): DataQuery[Option[GameInfo]] = tzio {
      (fr"SELECT * FROM" ++ Fragments.parentheses(selectGameInfo) ++ fr"g WHERE g.game_id = $gameId;").stripMargin.query[GameInfo].option
    }.toStoreError

    override def getGameInfoByPlayerId(playerId: PlayerId): DataQueryStream[PlayerGameInfo] = tzioStream {
      (fr"SELECT  p.player_id, p.player_number, g.game_id, g.platform, g.player_ids, g.board, g.num_moves, g.created_at, g.completed_at FROM" ++
        Fragments.parentheses(selectGameInfo) ++ fr"g JOIN player_game_results p ON g.game_id = p.game_id WHERE p.player_id = $playerId;").query[PlayerGameInfo].stream
    }.toStoreError

    override def getMoves(gameId: GameId): DataQueryStream[Move] = tzioStream {
      sql"""SELECT move_data FROM moves_completed
            ORDER BY move_number ASC
            WHERE game_id = $gameId
         """.stripMargin.query[Move].stream
    }.toStoreError

    private implicit class toStoreErrorZIO[R, A](zio: ZIO[R, DbException, A]) {
      def toStoreError: ZIO[R, StoreError, A] = zio.mapError(StoreError.apply)
    }

    private implicit class toStoreErrorStream[R, A](zio: ZStream[R, DbException, A]) {
      def toStoreError: ZStream[R, StoreError, A] = zio.mapError(StoreError.apply)
    }
  })
}
