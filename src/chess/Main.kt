package chess

import kotlin.math.abs

const val MSG_INVALID_INPUT = "Invalid Input"

enum class Color(val code: String) {
    BLACK("B"),
    WHITE("W");
}

data class Position(val row: Int, val col: Int) {
    init {
        require(row in 0 until Board.SIZE) { "row must be between 0 and ${Board.SIZE - 1} but was $row" }
        require(col in 0 until Board.SIZE) { "column must be between 0 and ${Board.SIZE - 1} but was $col" }
    }

    companion object {
        private val pattern = "([a-h])([1-8])".toRegex()

        fun parse(s: String): Position = pattern.matchEntire(s)
            ?.destructured
            ?.let { (c, r) -> Position(Board.SIZE - r.toInt(), c.first() - 'a') }
            ?: throw IllegalArgumentException("Cannot parse $s")
    }

    override fun toString(): String = "${'a' + col}${Board.SIZE - row}"
}

open class Move(val from: Position, val to: Position) {
    companion object {
        private val movePattern = "([a-h][1-8])([a-h][1-8])".toRegex()

        fun parse(s: String): Move = movePattern.matchEntire(s)
            ?.destructured
            ?.let { (from, to) -> Move(Position.parse(from), Position.parse(to)) }
            ?: throw IllegalArgumentException(MSG_INVALID_INPUT)
    }

    open fun apply(board: Board) {
        board[to]?.let { board.remove(it) }
        board[from]?.move(to)
    }

    override fun equals(other: Any?): Boolean = other is Move && other.from == from && other.to == to

    override fun toString(): String = "$from$to"
}

class EnPassant(from: Position, to: Position, private val captured: Pawn) : Move(from, to) {
    override fun apply(board: Board) {
        board.remove(captured)
        super.apply(board)
    }
}

abstract class Pawn(var position: Position, val color: Color) {
    fun move(newPosition: Position) {
        position = newPosition
    }

    protected abstract fun nextPositions(): List<Position>
    abstract fun hasLastRowReached(): Boolean

    fun validMoves(board: Board) : List<Move> = nextPositions()
        .filter {
            val isForwardMove = position.col == it.col && board[it] == null
            val isCapture = position.col != it.col && board[it] != null && board[it]?.color != color

            isForwardMove || isCapture
        }.map { Move(position, it) }
}

class BlackPawn(position: Position) : Pawn(position, Color.BLACK) {
    override fun nextPositions(): List<Position> = buildList {
        if (position.row + 1 < Board.SIZE)  {
            add(Position(position.row + 1, position.col))
            if (position.col + 1 < Board.SIZE) {
                add(Position(position.row + 1, position.col + 1))
            }
            if (position.col > 0) {
                add(Position(position.row + 1, position.col - 1))
            }
        }
        if (position.row == 1) {
            add(Position(position.row + 2, position.col))
        }
    }

    override fun hasLastRowReached(): Boolean = position.row == 7
}

class WhitePawn(position: Position) : Pawn(position, Color.WHITE) {
    override fun nextPositions(): List<Position> = buildList {
        if (position.row > 0) {
            add(Position(position.row - 1, position.col))
            if (position.col + 1 < Board.SIZE) {
                add(Position(position.row - 1, position.col + 1))
            }
            if (position.col > 0) {
                add(Position(position.row - 1, position.col - 1))
            }
        }
        if (position.row == 6) {
            add(Position(position.row - 2, position.col))
        }
    }

    override fun hasLastRowReached(): Boolean = position.row == 0
}

data class Player(val name: String, val color: Color) {
    fun pawns(board: Board): List<Pawn> = board[color]
}

class Board {
    companion object {
        const val SIZE = 8
    }

    private val pawns = mutableListOf(
        WhitePawn(Position.parse("a2")),
        WhitePawn(Position.parse("b2")),
        WhitePawn(Position.parse("c2")),
        WhitePawn(Position.parse("d2")),
        WhitePawn(Position.parse("e2")),
        WhitePawn(Position.parse("f2")),
        WhitePawn(Position.parse("g2")),
        WhitePawn(Position.parse("h2")),
        BlackPawn(Position.parse("a7")),
        BlackPawn(Position.parse("b7")),
        BlackPawn(Position.parse("c7")),
        BlackPawn(Position.parse("d7")),
        BlackPawn(Position.parse("e7")),
        BlackPawn(Position.parse("f7")),
        BlackPawn(Position.parse("g7")),
        BlackPawn(Position.parse("h7")),
    )

    operator fun get(position: Position): Pawn? = pawns.firstOrNull { it.position == position }
    operator fun get(color: Color): List<Pawn> = pawns.filter { it.color ==  color }

    fun remove(pawn: Pawn): Boolean = pawns.remove(pawn)

    fun contains(position: Position): Boolean = position.row in 0 until SIZE && position.col in 0 until SIZE

    override fun toString() : String = buildString {
        for (row in 0 until SIZE) {
            append("  +---+---+---+---+---+---+---+---+")
            append(System.lineSeparator())
            append("${SIZE - row} |")
            if (row < SIZE) {
                for (col in 0 until SIZE) {
                    append(" ")
                    append(pawns.firstOrNull { it.position == Position(row, col) }?.color?.code ?: " ")
                    append(" |")
                }
                append(System.lineSeparator())
            }
        }
        append("  +---+---+---+---+---+---+---+---+")
        append(System.lineSeparator())
        append("    a   b   c   d   e   f   g   h")
        append(System.lineSeparator())
    }
}

class Game(private val board: Board,
           firstPlayer: Player,
           secondPlayer: Player) {

    private val players = arrayOf(firstPlayer, secondPlayer)
    private val currentPlayer: Player get() = players[0]
    private val nextPlayer: Player get() = players[1]
    private val enPassantProposals = mutableListOf<Move>()

    fun start() {
        println(board)
        while (true) {
            println("${currentPlayer.name}'s turn:")
            when (val command = readln().trim().lowercase()) {
                "exit" -> {
                    println("Bye!")
                    return
                }
                else -> {
                    try {
                        val move = Move.parse(command)
                        val pawn = grabPawn(move)

                        makeMove(move, pawn)
                        handleEnPassant(move, pawn)
                        println(board)

                        if (pawn.hasLastRowReached() || nextPlayer.pawns(board).isEmpty()) {
                            println("${currentPlayer.color.name.lowercase().replaceFirstChar { it.uppercase() }} Wins!")
                            println("Bye!")
                            return
                        }

                        val movesLeft = enPassantProposals + nextPlayer.pawns(board).map { it.validMoves(board) }.flatten()

                        if (movesLeft.isEmpty()) {
                            println("Stalemate!")
                            println("Bye!")
                            return
                        }

                        nextTurn()
                    } catch (e: IllegalArgumentException) {
                        println(e.message)
                        continue
                    }
                }
            }
        }
    }

    private fun grabPawn(move: Move): Pawn {
        val pawn = board[move.from]
        val color = currentPlayer.color

        require(pawn != null && pawn.color == color) { "No ${color.name.lowercase()} pawn at ${move.from}" }

        return pawn
    }

    private fun makeMove(move: Move, pawn: Pawn) {
        val validMove = (enPassantProposals + pawn.validMoves(board)).firstOrNull { move == it }

        require(validMove != null) { MSG_INVALID_INPUT }

        validMove.apply(board)
    }

    private fun handleEnPassant(move: Move, pawn: Pawn) {
        enPassantProposals.clear()

        if (move.from.col != move.to.col || abs(move.to.row - move.from.row) != 2) {
            return
        }

        val color = currentPlayer.color
        enPassantProposals.addAll(buildList {
            if (move.to.col > 0)               add(Position(move.to.row, move.to.col - 1))
            if (move.to.col + 1 < Board.SIZE)  add(Position(move.to.row, move.to.col + 1))
        }
            .filter { board[it] != null && board[it]?.color != color }
            .map {
                val from = it
                val to = Position(if (color == Color.BLACK) move.from.row + 1 else move.from.row - 1, move.from.col)

                EnPassant(from, to, pawn)
            })
    }

    private fun nextTurn() {
        players[0] = players[1].also { players[1] = players[0] }
    }
}

fun main() {
    println("Pawns-Only Chess")
    println("First Player's name:")
    val firstPlayer = Player(readln().trim(), Color.WHITE)
    println("Second Player's name:")
    val secondPlayer = Player(readln().trim(), Color.BLACK)
    Game(Board(), firstPlayer, secondPlayer).start()
}