package soc.datacollector

import io.github.gaelrenoux.tranzactio.doobie
import io.grpc.ServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import scalapb.zio_grpc.{ServerLayer, ServiceList, Server => ZServer}
import soc.datacollector.game.data.GameDataQueries
import soc.datacollector.game.data.api.{DataAccessEndpoints, GameRecorderEndpoints}
import zio.{Has, ZLayer, ZManaged}
import zio.clock.Clock

object Server {

  def services: ServiceList[doobie.Database.Database with Has[GameDataQueries.Service] with Clock] = ServiceList
    .add(DataAccessEndpoints)
    .add(GameRecorderEndpoints)

  def builder(port: Int): ServerBuilder[_] = {
    ServerBuilder
      .forPort(port)
      .addService(ProtoReflectionService.newInstance())
  }

  val live: ZLayer[doobie.Database.Database with Has[GameDataQueries.Service] with Clock with Has[GrpcServiceConfig], Throwable, Has[ZServer.Service]] = {
    ZManaged.service[GrpcServiceConfig].flatMap { config =>
      ServerLayer.fromServiceList(builder(config.port), services).build
    }.toLayerMany
  }


}
