package soc.datacollector.game.data

import io.github.gaelrenoux.tranzactio.doobie
import io.github.gaelrenoux.tranzactio.doobie.Database
import io.github.scottweaver.zio.aspect.DbMigrationAspect
import io.github.scottweaver.zio.testcontainers.postgres.ZPostgreSQLContainer
import io.soc.core.base.board.{BaseBoard, Vertex}
import io.soc.core.base.moves.BuildSettlement
import io.soc.data.game_data_core.MoveEvent
import io.soc.data.game_data_core.MoveEvent.Move.BuildSettlementMove
import soc.datacollector.Utils.currentTime
import soc.datacollector.game.GameInfo
import soc.datacollector.game.data.GameDataQueries.{MovesOperationResult, insertMove}
import soc.datacollector.player.PlayerId
import soc.datacollector.{Board, ConnectionPool, DbConfig, GameId, Move, PgConfig}
import zio.{Chunk, ZIO, ZLayer}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.test.Assertion.{equalTo, hasField, isNone, isRight, isSome}
import zio.test.environment.TestEnvironment
import zio.test.{Assertion, DefaultRunnableSpec, TestFailure, ZSpec, assert, assertM}

object GameDataQueriesSpec extends DefaultRunnableSpec{

  private val invalidGameId = GameId(-1)
  private val InitialGameId = GameId(0)
  private val DefaultGameId = GameId(1)

  private val InitialPreviousMoveNumber = 0

  private val testPlatform = "test"

  private val playerId = PlayerId("player1")
  private val playerIds = List("player1", "player2", "player3", "player4").map(PlayerId.apply)

  private val board = Board(BaseBoard.defaultInstance)
  private val move: Move = Move(MoveEvent(BuildSettlementMove(BuildSettlement(Some(Vertex("1"))))))

  private val layer = {

    val postgres = ZPostgreSQLContainer.Settings.default >>> ZPostgreSQLContainer.live

    //val dbConfig = ZLayer.succeed(DbConfig(PgConfig("jdbc:postgresql://localhost:5432/mydb", "postgres", "postgres"), None))

    //val dataSource = (dbConfig ++ Blocking.any) >>> ConnectionPool.live
    val databaseLayer: ZLayer[Blocking with Clock, Throwable, doobie.Database.Database] = (postgres ++ Blocking.any ++ Clock.any) >>> Database.fromDatasource
    //val databaseLayer: ZLayer[Blocking with Clock, Throwable, doobie.Database.Database] = (dataSource ++ Blocking.any ++ Clock.any) >>> Database.fromDatasource

    val storeLayer = GameDataQueries.live

    (postgres ++ databaseLayer ++ storeLayer).mapError(TestFailure.fail)
    //(databaseLayer ++ storeLayer).mapError(TestFailure.fail)
  }

  override def spec: ZSpec[TestEnvironment, Any] = (suite("GameDataQueries")(
    dataRecordingSuite, dataAccessSuite) @@ DbMigrationAspect.migrateOnce("filesystem:db/migration")()).provideSomeLayerShared[TestEnvironment](layer)
    //dataRecordingSuite, dataAccessSuite)).provideSomeLayerShared[TestEnvironment](layer)

  private val startRecordingSuite = suite("startRecording")()

  private val insertMoveSuite = suite("insertMove")(
    testM("returns None if game recording has not started") {
      val transaction = for {
        time <- currentTime
        result <- GameDataQueries.insertMove(invalidGameId, move, 0, time).either
      } yield assert(result)(isRight(isNone))
      Database.transactionOrDieR(transaction)
    },
    testM("returns None if game recording has been completed") {
      val transaction = for {
        startTime <- currentTime
        gameId <- GameDataQueries.startRecording(testPlatform, playerIds, board, startTime)
        _ <- GameDataQueries.moveGameRecording(gameId, 0, startTime)
        result <- GameDataQueries.insertMove(gameId, move, 0, startTime).either
      } yield  assert(result)(isRight(isNone))
      Database.transactionOrDieR(transaction)
    },
    testM("returns Some if previous move number does not match last move number") {
      val transaction = for {
        startTime <- currentTime
        gameId <- GameDataQueries.startRecording(testPlatform, playerIds, board, startTime)
        result <- GameDataQueries.insertMove(gameId, move, 1, startTime)
      } yield assert(result)(isSome(
        hasField[MovesOperationResult[Int], Int]("previousMoveNumber", _.previousMoveNumber, equalTo(0)) &&
          hasField("updatedMoveNumber", _.result, isNone)
      ))
      Database.transactionOrDieR(transaction)
    },
    testM("returns correct updatedMoveNumber") {
      val transaction = for {
        startTime <- currentTime
        gameId <- GameDataQueries.startRecording(testPlatform, playerIds, board, startTime)
        _ <- GameDataQueries.insertMove(gameId, move, 0, startTime)
        result <- GameDataQueries.insertMove(gameId, move, 1, startTime)
      } yield assert(result)(isSome(hasField("updatedMoveNumber", _.result, isSome(equalTo(2)))))
      Database.transactionOrDieR(transaction)
    })

  private val moveGameRecordingSuite = suite("moveGameRecording")(
    testM("returns None for invalid gameId") {
      val transaction = for {
        time <- currentTime
        result <- GameDataQueries.moveGameRecording(invalidGameId, 0, time)
      } yield assert(result)(isNone)
      Database.transactionOrDieR(transaction)
    },
    testM("returns None for completed gameId") {
      val transaction = for {
        startTime <- currentTime
        gameId <- GameDataQueries.startRecording(testPlatform, playerIds, board, startTime)
        _ <- GameDataQueries.moveGameRecording(gameId, 0, startTime)
        result <- GameDataQueries.moveGameRecording(gameId, 0, startTime)
      } yield assert(result)(isNone)
      Database.transactionOrDieR(transaction)
    },
    testM("returns some if expected num moves does not match last move number") {
      val transaction = for {
        startTime <- currentTime
        gameId <- GameDataQueries.startRecording(testPlatform, playerIds, board, startTime)
        _ <- GameDataQueries.insertMove(gameId, move, 0, startTime)
        result <- GameDataQueries.moveGameRecording(gameId, 2, startTime)
      } yield assert(result)(isSome(
        hasField[MovesOperationResult[List[PlayerId]], Int]("previousMoveNumber", _.previousMoveNumber, equalTo(1)) &&
          hasField("updatedMoveNumber", _.result, isNone)
      ))
      Database.transactionOrDieR(transaction)
    },
    testM("returns game playerIds for moved game") {
      val transaction = for {
        startTime <- currentTime
        gameId <- GameDataQueries.startRecording(testPlatform, playerIds, board, startTime)
        _ <- GameDataQueries.insertMove(gameId, move, 0, startTime)
        result <- GameDataQueries.moveGameRecording(gameId, 1, startTime)
      } yield assert(result)(isSome(
        hasField[MovesOperationResult[List[PlayerId]], Int]("numMoves", _.previousMoveNumber, equalTo(1)) &&
        hasField("result", _.result, isSome(equalTo(playerIds)))
      ))
      Database.transactionOrDieR(transaction)
    }
  )

  private val moveRecordedMovesSuite = suite("moveRecordedMoves")(
    testM("returns empty stream when no moves have been recorded") {
      val transaction = for {
        startTime <- currentTime
        gameId <- GameDataQueries.startRecording(testPlatform, playerIds, board, startTime)
        result <- GameDataQueries.moveRecordedMoves(gameId).runCollect
      } yield assert(result)(Assertion.isEmpty)
      Database.transactionOrDieR(transaction)
    },
    testM("returns moves for gameId in order") {
      val transaction = for {
        startTime <- currentTime
        gameId <- GameDataQueries.startRecording(testPlatform, playerIds, board, startTime)
        moves = Chunk(move, move, move, move)
        _ <- ZIO.foreach(moves.zipWithIndex) { case (m, i) =>
          insertMove(gameId, m, i, startTime)
        }
        result <- GameDataQueries.moveRecordedMoves(gameId).runCollect
      } yield assert(result)(equalTo(moves))
      Database.transactionOrDieR(transaction)
    }
  )

  private val insertPlayerGameInfoSuite = suite("insertPlayerGameInfo")(

  )

  private val getLastRecordedMoveSuite = suite("getLastRecordedMove") (

  )

  private val dataRecordingSuite = suite("data recording")(startRecordingSuite, insertMoveSuite, moveGameRecordingSuite, moveRecordedMovesSuite, insertPlayerGameInfoSuite, getLastRecordedMoveSuite)

  private val getGameInfoByIdSuite = suite("getGameInfoById")(
    testM("returns None for invalid gameId") {
      val transaction = for {
        result <- GameDataQueries.getGameInfoById(invalidGameId)
      } yield assert(result)(isNone)
      Database.transactionOrDieR(transaction)
    },
    testM("returns None when game has not been completed") {
      val transaction = for {
        time <- currentTime
        gameId <- GameDataQueries.startRecording(testPlatform, playerIds, board, time)
        result <- GameDataQueries.getGameInfoById(gameId)
      } yield assert(result)(isNone)
      Database.transactionOrDieR(transaction)
    },
    testM("returns GameInfo when games has been completed") {
      val transaction = for {
        time <- currentTime
        gameId <- GameDataQueries.startRecording(testPlatform, playerIds, board, time)
        _ <- GameDataQueries.moveGameRecording(gameId, 0, time)
        _ <- GameDataQueries.insertPlayerGameInfo(gameId, playerId, 0)
        result <- GameDataQueries.getGameInfoById(gameId)

        expectedGameInfo = GameInfo(gameId, testPlatform, List(playerId), board, 0, time, time)
      } yield assert(result)(isSome(equalTo(expectedGameInfo)))
      Database.transactionOrDieR(transaction)
    }
  )

  private val getGameInfoByPlayerIdSuite = suite("getGameInfoByPlayerId")(
    testM("retrieves each game played by the PlayerId") {
      val n = 10
      val player = PlayerId("testPlayer")

      val gameTransaction = for {
        time <- currentTime
        gameId <- GameDataQueries.startRecording(testPlatform, player :: playerIds.take(3), board, time)
        _ <- GameDataQueries.insertMove(gameId, move, 0, time)
        playerIdsOpt <- GameDataQueries.moveGameRecording(gameId, 1, time)
        pIds <- ZIO.getOrFail(playerIdsOpt.flatMap(_.result))
        _ <- GameDataQueries.moveRecordedMoves(gameId).runDrain
        _ <- ZIO.foreach(pIds.zipWithIndex)(id => GameDataQueries.insertPlayerGameInfo(gameId, id._1, id._2))
      } yield gameId

      val transaction = for {
        _ <- ZIO.foreach((1 to n).toList)(_ => gameTransaction)
        playerGames <- GameDataQueries.getGameInfoByPlayerId(player).runCount
      } yield assert(playerGames.toInt)(equalTo(n))
      Database.transactionOrDieR(transaction)
    }
  )

  private val getMovesSuite = suite("getMoves") (

  )

  private val dataAccessSuite = suite("data access") (getGameInfoByIdSuite, getGameInfoByPlayerIdSuite, getMovesSuite)
}
