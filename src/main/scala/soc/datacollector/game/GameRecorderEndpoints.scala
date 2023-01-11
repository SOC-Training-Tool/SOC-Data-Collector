package soc.datacollector.game

import io.grpc.Status
import io.soc.store.data_store.{GameEvent, MoveEvent, RecordGameResponse, StartGame}
import io.soc.store.data_store.ZioDataStore.ZGameStore
import zio.{Ref, ZIO, stream}

class GameRecorderEndpoints[R] extends ZGameStore[R, Any]{
  override def recordGame(request: stream.Stream[Status, GameEvent]): ZIO[R, Status, RecordGameResponse] = {
    Ref.make[Option[String]](None).flatMap { gameIdRef =>
      val gameId = gameIdRef.get.someOrFail(Status.FAILED_PRECONDITION)
      request.tap {
        case GameEvent(GameEvent.Event.StartGame(start), _) =>
          gameIdRef.get.filterOrFail(_.isDefined)(Status.INVALID_ARGUMENT) <*
            startGame(start).flatMap(Some.apply[String].andThen(gameIdRef.set))
        case GameEvent(GameEvent.Event.RecordMove(move), _) =>
          gameId.flatMap(recordMove(_, move))
        case _ => ZIO.fail(Status.INVALID_ARGUMENT)
      }.runCount.zipLeft(gameId.flatMap(completeGame)).as(RecordGameResponse())
    }
  }

  def startGame(start: StartGame) = ZIO.succeed("gameId")

  def recordMove(gameId: String, move: MoveEvent) = ZIO.unit

  def completeGame(gameId: String) = ZIO.unit
}
