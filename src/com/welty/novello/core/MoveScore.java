package com.welty.novello.core;

/**
 * A move with an evaluation
*/
public class MoveScore {
    public final int sq;
    public final int score;

    /**
     * @param sq square index of the move
     * @param score evaluation, from mover's point of view, in centidisks
     */
    public MoveScore(int sq, int score) {
        this.sq = sq;
        this.score = score;
    }

    /**
     * @param squareText square of move, in text format; for example "D5"
     * @param score evaluation, from mover's point of view, in centidisks
     */
    public MoveScore(String squareText, int score) {
        this(BitBoardUtils.textToSq(squareText), score);
    }

    @Override public String toString() {
        return BitBoardUtils.sqToText(sq)+"/"+score;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MoveScore moveScore = (MoveScore) o;

        return score == moveScore.score && sq == moveScore.sq;

    }

    @Override
    public int hashCode() {
        int result = sq;
        result = 31 * result + score;
        return result;
    }
}
