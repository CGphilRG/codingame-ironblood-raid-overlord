package com.codingame.game;

import com.codingame.gameengine.core.AbstractSoloPlayer;

/**
 * CodinGame Solo Optimization player expecting one semicolon-delimited command line per turn.
 */
public class Player extends AbstractSoloPlayer {
    @Override
    public int getExpectedOutputLines() {
        return 1;
    }
}
