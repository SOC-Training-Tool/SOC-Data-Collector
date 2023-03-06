package soc.datacollector

import io.github.gaelrenoux.tranzactio.doobie
import io.grpc.ServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import scalapb.zio_grpc.{ManagedServer, Server, ServerLayer, ServerMain, ServiceList}
import soc.datacollector.game.data.GameDataQueries
import soc.datacollector.game.data.api.{DataAccessEndpoints, GameRecorderEndpoints}
import zio.{Has, ZLayer}
import zio.clock.Clock

object Server {

  val port = 9000

  def services: ServiceList[doobie.Database.Database with Has[GameDataQueries.Service] with Clock] = ServiceList
    .add(DataAccessEndpoints)
    .add(GameRecorderEndpoints)

  def builder: ServerBuilder[_] = {
    ServerBuilder
      .forPort(port)
      .addService(ProtoReflectionService.newInstance())
  }

  def live: ZLayer[doobie.Database.Database with Has[GameDataQueries.Service] with Clock, Throwable, Server] =
    ServerLayer.fromServiceList(builder, services)




}
