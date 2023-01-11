package soc.datacollector

final case class GameId(id: String) extends AnyVal

object domain {

  type MOVE = Any
  type BOARD = Any
  type GAME = SOCGame
  type PlayerId = String

  case class SOCGame(gameId: GameId, playerId: List[PlayerId], board: BOARD, moves: List[MOVE])
}



