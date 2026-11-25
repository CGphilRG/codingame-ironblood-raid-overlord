package com.codingame.game;

import com.codingame.game.core.GameConstants;
import com.codingame.game.core.HeroAI;
import com.codingame.game.core.RaidEngine;
import com.codingame.game.core.RaidModels.RaidState;
import com.codingame.game.core.RaidViewer;
import com.codingame.game.core.ScenarioGenerator;
import com.codingame.gameengine.core.AbstractPlayer.TimeoutException;
import com.codingame.gameengine.core.AbstractReferee;
import com.codingame.gameengine.core.GameManager;
import com.codingame.gameengine.core.SoloGameManager;
import com.codingame.gameengine.module.entities.GraphicEntityModule;
import com.codingame.gameengine.module.toggle.ToggleModule;
import com.codingame.gameengine.module.tooltip.TooltipModule;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;

/**
 * CodinGame Solo Optimization Referee where the player commands the Overlord (Runic River)
 * to repel the deterministic HeroAI raiding party.
 */
public class Referee extends AbstractReferee {
    @Inject private SoloGameManager<Player> gameManager;
    @Inject private GraphicEntityModule graphicEntityModule;
    @Inject private TooltipModule tooltipModule;
    @Inject private ToggleModule toggleModule;

    private RaidEngine engine;
    private RaidViewer viewer;

    public RaidViewer getViewer() {
        return viewer;
    }

    @Override
    public void init() {
        long[] cfg = ScenarioGenerator.parseSeedAndTier(gameManager.getTestCaseInput());
        RaidState state = ScenarioGenerator.generate(cfg[0], (int) cfg[1]);
        engine = new RaidEngine(state);

        gameManager.setMaxTurns(state.maxRounds);
        gameManager.setFirstTurnMaxTime(1000);
        gameManager.setTurnMaxTime(50);
        gameManager.setFrameDuration(650);

        viewer = new RaidViewer(graphicEntityModule, tooltipModule, toggleModule, engine, GameConstants.ROLE_OVERLORD);
        viewer.initView("IRONBLOOD CHRONICLES — OVERLORD");

        Player player = gameManager.getPlayer();
        for (String line : engine.formatInitInputs(GameConstants.ROLE_OVERLORD)) {
            player.sendInputLine(line);
        }
    }

    @Override
    public void gameTurn(int round) {
        RaidState state = engine.getState();
        state.round = round;

        // Hero AI strikes first at the start of each round
        List<String> heroLogs = new ArrayList<>();
        String heroCmd = HeroAI.decideCommand(engine);
        engine.executeHeroTurn(heroCmd, heroLogs);
        for (String log : heroLogs) {
            gameManager.addToGameSummary(GameManager.formatErrorMessage(log));
        }

        if (engine.areHeroesVictorious()) {
            viewer.refreshState(false, true);
            int score = engine.computeOverlordScore();
            gameManager.putMetadata("Score", score);
            gameManager.loseGame("Heroes defeated the Boss and escaped with the Captive!");
            return;
        }

        viewer.refreshState(true, true);

        // Overlord player turn
        Player player = gameManager.getPlayer();
        for (String line : engine.formatTurnInputs(GameConstants.ROLE_OVERLORD)) {
            player.sendInputLine(line);
        }
        player.execute();

        List<String> overlordLogs = new ArrayList<>();
        try {
            String cmd = player.getOutputs().get(0).trim();
            engine.executeOverlordTurn(cmd, overlordLogs);
        } catch (TimeoutException e) {
            gameManager.putMetadata("Score", 0);
            gameManager.loseGame("Overlord timed out!");
            return;
        } catch (IllegalArgumentException badCmd) {
            gameManager.putMetadata("Score", 0);
            gameManager.loseGame("Invalid Overlord command: " + badCmd.getMessage());
            return;
        }

        for (String log : overlordLogs) {
            gameManager.addToGameSummary(GameManager.formatSuccessMessage(log));
        }

        viewer.refreshState(false, true);

        if (engine.areAllHeroesDead()) {
            int score = engine.computeOverlordScore();
            gameManager.putMetadata("Score", score);
            gameManager.winGame("Overlord annihilated the raiding party! Score: " + score);
        } else if (round >= state.maxRounds) {
            int score = engine.computeOverlordScore();
            gameManager.putMetadata("Score", score);
            gameManager.winGame("Overlord held the fortress until dark! Score: " + score);
        }
    }
}
