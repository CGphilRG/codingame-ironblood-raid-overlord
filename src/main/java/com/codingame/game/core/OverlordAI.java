package com.codingame.game.core;

import com.codingame.game.core.RaidModels.Enemy;
import com.codingame.game.core.RaidModels.Hero;
import com.codingame.game.core.RaidModels.RaidState;
import com.codingame.game.core.RaidModels.RiverTile;
import com.codingame.game.core.RaidModels.Zone;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic tactical AI for the Overlord player (used as the opponent in Heroes Opti
 * and as a baseline heuristic for Overlord playtesting).
 */
public final class OverlordAI {
    private OverlordAI() {
    }

    /**
     * Computes a legal semicolon-separated Overlord command sequence using lookahead gem & River valuation.
     */
    public static String decideCommand(RaidEngine engine) {
        RaidState state = engine.getState();
        int reserveAfterRecovery = state.overlordReserve + Math.min(state.overlordFatigue, state.recoverRate);
        int gemsLeft = reserveAfterRecovery;

        List<String> cmds = new ArrayList<>();

        // 1. Consider dredging a dead tile clogging slots 1..2 when dead models block cheap activations
        List<Integer> simRiver = new ArrayList<>(state.riverOrder);
        if (gemsLeft >= GameConstants.DREDGE_COST + 3) {
            for (int idx = 0; idx < Math.min(2, simRiver.size()); idx++) {
                RiverTile tile = engine.getTileById(simRiver.get(idx));
                if (tile.state == GameConstants.TILE_DEAD && tile.reinforceCost > state.reinforcePower) {
                    cmds.add("DREDGE " + tile.id);
                    gemsLeft -= GameConstants.DREDGE_COST;
                    simRiver.remove(idx);
                    break;
                }
            }
        }

        // 2. Round 1 sounds the alarm (1 activation); subsequent rounds up to 2 activations from slots 1..5
        List<Integer> chosenTileIds = new ArrayList<>();
        int maxPicks = (state.round == 1) ? 1 : GameConstants.MAX_ACTIVATIONS_PER_TURN;
        for (int pick = 0; pick < maxPicks; pick++) {
            int bestTileId = -1;
            int bestSlotCost = -1;
            double bestValue = -1e9;

            for (int idx = 0; idx < Math.min(5, simRiver.size()); idx++) {
                int cost = idx + 1;
                if (cost > gemsLeft) {
                    continue;
                }
                int tileId = simRiver.get(idx);
                if (chosenTileIds.contains(tileId)) {
                    continue;
                }
                RiverTile tile = engine.getTileById(tileId);
                if (tile.state == GameConstants.TILE_DEAD) {
                    continue;
                }
                double value = scoreTileForActivation(engine, state, tile, cost);
                if (value > bestValue) {
                    bestValue = value;
                    bestTileId = tileId;
                    bestSlotCost = cost;
                }
            }

            if (bestTileId < 0 || bestValue < 5.0) {
                break;
            }
            chosenTileIds.add(bestTileId);
            cmds.add("ACTIVATE " + bestTileId);
            gemsLeft -= bestSlotCost;
            simRiver.remove(Integer.valueOf(bestTileId));
            simRiver.add(bestTileId);
        }

        // 3. Emit REINFORCE if EVENT tile was activated, then tactical MOVE + ATTACK for each activated combat tile
        int reinforceBudget = 0;
        for (int tileId : chosenTileIds) {
            RiverTile tile = engine.getTileById(tileId);
            if ("EVENT".equals(tile.unitType)) {
                reinforceBudget += state.reinforcePower;
                emitReinforcements(engine, state, reinforceBudget, cmds);
                continue;
            }
            for (Enemy e : state.enemies) {
                if (e.alive && e.tileId == tileId) {
                    planEnemyActions(engine, state, e, cmds);
                }
            }
        }

        if (cmds.isEmpty()) {
            return "PASS; MSG Holding reserve (" + reserveAfterRecovery + ")";
        }
        cmds.add("MSG Raid defense round " + state.round);
        return String.join("; ", cmds);
    }

    private static double scoreTileForActivation(RaidEngine engine, RaidState state, RiverTile tile, int slotCost) {
        if ("EVENT".equals(tile.unitType)) {
            int deadReinforceable = countDeadReinforceableUnits(state, tile);
            if (deadReinforceable < 2) {
                return -10.0;
            }
            return deadReinforceable * 38.0 - slotCost * 8.0;
        }

        Hero priorityHero = chooseTargetHero(state);
        if (priorityHero == null) {
            return -100.0;
        }
        int[] distFromPriority = ScenarioGenerator.bfsDistances(state, priorityHero.zone);
        double score = 0.0;

        for (Enemy e : state.enemies) {
            if (!e.alive || e.tileId != tile.id) {
                continue;
            }
            int d = distFromPriority[e.zone];
            boolean canHitNow = false;
            int bestWounds = 0;
            for (Hero h : state.heroes) {
                if (!h.alive) continue;
                int w = estimateWounds(engine, e, h);
                if (canReachAndAttack(engine, state, e, h)) {
                    canHitNow = true;
                    bestWounds = Math.max(bestWounds, w + (h.carryingCaptive ? 2 : 0));
                }
            }
            if (canHitNow) {
                score += 45.0 + bestWounds * 32.0;
            } else if (d >= 0) {
                score += Math.max(4.0, 26.0 - d * 5.0);
            }
            if (e.slumbering) {
                score += 12.0;
            }
        }
        return score - slotCost * 7.5;
    }

    private static void emitReinforcements(RaidEngine engine, RaidState state, int reinforceBudget, List<String> cmds) {
        List<Integer> reinfZones = new ArrayList<>();
        for (Zone z : state.zones) {
            if (z.type == GameConstants.ZONE_REINFORCEMENT) {
                reinfZones.add(z.id);
            }
        }
        if (reinfZones.isEmpty()) {
            return;
        }

        Hero focusHero = chooseTargetHero(state);
        int targetZone = (focusHero != null) ? focusHero.zone : state.sanctumZone;
        int[] distToHero = ScenarioGenerator.bfsDistances(state, targetZone);
        int bestSpawn = reinfZones.get(0);
        for (int rz : reinfZones) {
            if (distToHero[rz] < distToHero[bestSpawn]) {
                bestSpawn = rz;
            }
        }

        int budget = reinforceBudget;
        for (RiverTile t : state.allTiles) {
            if (t.dredged || "BOSS".equals(t.unitType) || "EVENT".equals(t.unitType)) {
                continue;
            }
            int aliveCount = engine.countAliveModelsOnTile(t.id);
            int missing = t.maxModels - aliveCount;
            while (missing > 0 && budget >= t.reinforceCost) {
                cmds.add("REINFORCE " + t.id + " " + bestSpawn);
                budget -= t.reinforceCost;
                missing--;
            }
        }
    }

    private static void planEnemyActions(RaidEngine engine, RaidState state, Enemy e, List<String> cmds) {
        int curZone = e.zone;
        int movesLeft = e.movePoints;

        Hero bestTargetNow = pickBestHeroInRange(engine, state, e, curZone);
        if (bestTargetNow != null && (e.attackRange >= 2 || !"ARCHER".equals(e.unitType))) {
            // Attack first if already positioned cleanly, or move closer to block/shoot
            if (e.attackRange >= 2 && engine.countAliveHeroesInZone(curZone) > 0) {
                // Step back if affordable to clear engagement, otherwise fire point-blank
            }
            cmds.add("ATTACK " + e.id + " " + bestTargetNow.id);
            // If unit still has moves left and target is not carrier in same zone, hold zone to inflict Hindrance
            return;
        }

        // Advance toward highest priority hero / captive choke point
        Hero targetHero = chooseTargetHero(state);
        if (targetHero == null) {
            return;
        }
        int goalZone = chooseGoalZoneForEnemy(state, e, targetHero);
        int[] dist = ScenarioGenerator.bfsDistances(state, goalZone);

        while (movesLeft > 0 && curZone != goalZone) {
            int stepCost = 1 + engine.countAliveHeroesInZone(curZone);
            if (movesLeft < stepCost) {
                break;
            }
            int nextZone = -1;
            int bestD = dist[curZone];
            for (int nb : state.zones.get(curZone).neighbors) {
                if (dist[nb] >= 0 && dist[nb] < bestD) {
                    bestD = dist[nb];
                    nextZone = nb;
                }
            }
            if (nextZone < 0) {
                break;
            }
            cmds.add("MOVE " + e.id + " " + nextZone);
            curZone = nextZone;
            movesLeft -= stepCost;

            Hero hitAfterStep = pickBestHeroInRange(engine, state, e, curZone);
            if (hitAfterStep != null && (e.attackRange == 1 || curZone != hitAfterStep.zone)) {
                cmds.add("ATTACK " + e.id + " " + hitAfterStep.id);
                return;
            }
        }

        Hero fallbackHit = pickBestHeroInRange(engine, state, e, curZone);
        if (fallbackHit != null) {
            cmds.add("ATTACK " + e.id + " " + fallbackHit.id);
        }
    }

    private static int chooseGoalZoneForEnemy(RaidState state, Enemy e, Hero targetHero) {
        if (e.attackRange >= 2) {
            // Find closest zone with LoS to targetHero (avoiding same-zone entanglement if possible)
            int[] distFromEnemy = ScenarioGenerator.bfsDistances(state, e.zone);
            int bestZ = targetHero.zone;
            int bestDist = Integer.MAX_VALUE;
            for (Zone z : state.zones) {
                if (z.id != targetHero.zone && RaidEngine.hasLineOfSight(state, z.id, targetHero.zone)) {
                    if (distFromEnemy[z.id] >= 0 && distFromEnemy[z.id] < bestDist) {
                        bestDist = distFromEnemy[z.id];
                        bestZ = z.id;
                    }
                }
            }
            return bestZ;
        }
        return targetHero.zone;
    }

    private static boolean canReachAndAttack(RaidEngine engine, RaidState state, Enemy e, Hero h) {
        if (pickHeroDirectMatch(state, e, e.zone, h)) {
            return true;
        }
        int stepCost = 1 + engine.countAliveHeroesInZone(e.zone);
        if (e.movePoints < stepCost) {
            return false;
        }
        int[] dist = ScenarioGenerator.bfsDistances(state, e.zone);
        for (Zone z : state.zones) {
            if (dist[z.id] >= 1 && dist[z.id] <= e.movePoints && pickHeroDirectMatch(state, e, z.id, h)) {
                return true;
            }
        }
        return false;
    }

    private static boolean pickHeroDirectMatch(RaidState state, Enemy e, int fromZone, Hero h) {
        if (!h.alive) return false;
        if (e.attackRange == 1) {
            return fromZone == h.zone;
        }
        return RaidEngine.hasLineOfSight(state, fromZone, h.zone);
    }

    private static Hero pickBestHeroInRange(RaidEngine engine, RaidState state, Enemy e, int fromZone) {
        Hero best = null;
        double bestScore = -1e9;
        int[] dist = ScenarioGenerator.bfsDistances(state, fromZone);
        for (Hero h : state.heroes) {
            if (!pickHeroDirectMatch(state, e, fromZone, h)) {
                continue;
            }
            int d = dist[h.zone] >= 0 ? dist[h.zone] : 10;
            // Prefer closest Hero (same-zone threat), tie-break carrier then lowest heroId
            double s = (h.zone == fromZone ? 40.0 : -d * 10.0)
                + (h.carryingCaptive ? 12.0 : 0.0)
                + (5.0 - h.id);
            if (s > bestScore) {
                bestScore = s;
                best = h;
            }
        }
        return best;
    }

    private static int estimateWounds(RaidEngine engine, Enemy e, Hero h) {
        int guard = 0;
        for (Hero ally : engine.getState().heroes) {
            if (ally.alive && ally.id != h.id && ally.zone == h.zone && "GUARD".equals(ally.skill)) {
                guard = 1;
                break;
            }
        }
        int rest = (h.stance == GameConstants.STANCE_REST) ? 1 : 0;
        return Math.max(0, e.attackPower - (h.armor + rest + guard));
    }

    private static Hero chooseTargetHero(RaidState state) {
        Hero best = null;
        double bestPriority = -1e9;
        for (Hero h : state.heroes) {
            if (!h.alive) continue;
            double p = (h.carryingCaptive ? 120.0 : 0.0) + (20.0 - h.armor * 5.0 - h.remainingGems());
            if (p > bestPriority) {
                bestPriority = p;
                best = h;
            }
        }
        return best;
    }

    private static int countDeadReinforceableUnits(RaidState state, RiverTile eventTile) {
        int c = 0;
        for (Enemy e : state.enemies) {
            if (!e.alive && !"BOSS".equals(e.unitType) && !state.allTiles.get(e.tileId).dredged) {
                c++;
            }
        }
        return c;
    }
}
