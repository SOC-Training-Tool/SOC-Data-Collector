package soc.datacollector

final case class GameId(id: Int) extends AnyVal

object domain {

  type MOVE = Unit
  type BOARD = Unit
  type GAME = SOCGame
  type PlayerId = String

  case class SOCGame(gameId: GameId, playerId: List[PlayerId], board: BOARD, moves: List[MOVE])
}



