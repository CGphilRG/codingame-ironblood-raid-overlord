package com.codingame.game.core;

import com.codingame.game.core.RaidModels.Enemy;
import com.codingame.game.core.RaidModels.Hero;
import com.codingame.game.core.RaidModels.RaidState;
import com.codingame.game.core.RaidModels.Zone;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic tactical AI for the Hero raiding party (used as the opponent in Overlord Opti
 * and as a strong reference Hero baseline).
 */
public final class HeroAI {
    private HeroAI() {
    }

    /**
     * Computes a legal semicolon-separated Hero turn command string.
     */
    public static String decideCommand(RaidEngine engine) {
        RaidState state = engine.getState();
        List<String> cmds = new ArrayList<>();
        int deadHeroes = state.heroes.size() - engine.countAliveHeroes();

        boolean bossAlive = engine.isBossAlive();
        int[] simHeroZones = new int[state.heroes.size()];
        int[] simHeroReserve = new int[state.heroes.size()];
        int[] simHeroFatigue = new int[state.heroes.size()];
        boolean[] simHeroRest = new boolean[state.heroes.size()];
        for (Hero h : state.heroes) {
            simHeroZones[h.id] = h.zone;
            simHeroReserve[h.id] = h.reserve;
            simHeroFatigue[h.id] = h.fatigue;
        }

        // Track remaining simulated enemy HP cleanly so multiple Heroes coordinate focus-fire
        int[] simEnemyHp = new int[state.enemies.size()];
        boolean[] simEnemyAlive = new boolean[state.enemies.size()];
        for (Enemy e : state.enemies) {
            simEnemyHp[e.id] = e.hp;
            simEnemyAlive[e.id] = e.alive;
        }

        boolean simCaptiveCarried = (state.captiveState == GameConstants.CAPTIVE_CARRIED);
        int simCaptiveZone = state.captiveZone;

        // 1. Bold adventurer stances: REST only when very low on reserve with substantial fatigue
        for (Hero h : state.heroes) {
            if (!h.alive) continue;
            boolean criticalStrike = canFinishObjectiveNow(engine, state, h, bossAlive, simCaptiveCarried);
            boolean lowReserve = h.reserve <= 2 && h.fatigue >= 5;
            if (lowReserve && !criticalStrike) {
                simHeroRest[h.id] = true;
                int rec = Math.min(h.fatigue, GameConstants.HERO_REST_RECOVER + deadHeroes);
                simHeroReserve[h.id] += rec;
                simHeroFatigue[h.id] -= rec;
                cmds.add("STANCE " + h.id + " REST");
            } else {
                simHeroRest[h.id] = false;
                int rec = Math.min(h.fatigue, GameConstants.HERO_ACTIVE_RECOVER + deadHeroes);
                simHeroReserve[h.id] += rec;
                simHeroFatigue[h.id] -= rec;
                cmds.add("STANCE " + h.id + " ACTIVE");
            }
        }

        // 2. Assign each Active Hero an objective and plan legal moves, attacks, and manipulations
        Set<Integer> simSearchedHuts = new HashSet<>();
        for (Zone z : state.zones) {
            if (z.type == GameConstants.ZONE_HUT && z.hutSearched) {
                simSearchedHuts.add(z.id);
            }
        }
        Set<Integer> simLootedChests = new HashSet<>();
        boolean hutClaimedThisTurn = false;

        for (Hero h : state.heroes) {
            if (!h.alive || simHeroRest[h.id]) continue;

            int freeLeft = Math.max(0, h.freeMove - (h.carryingCaptive ? 1 : 0));
            int moveGemsLeft = h.exertMove;
            int meleeExertLeft = h.exertMelee;
            int rangedExertLeft = h.exertRanged;
            int manipGemsLeft = h.exertManip;

            int targetZone = chooseTargetZone(state, h, bossAlive, simCaptiveCarried, simCaptiveZone, hutClaimedThisTurn);
            if (targetZone >= 0 && state.zones.get(targetZone).type == GameConstants.ZONE_HUT && !h.carryingCaptive) {
                hutClaimedThisTurn = true;
            }

            // Post-move strikes only: bold adventurers advance toward goals before fighting
            if (targetZone >= 0 && simHeroZones[h.id] != targetZone) {
                int[] dist = ScenarioGenerator.bfsDistances(state, targetZone);
                while (simHeroZones[h.id] != targetZone) {
                    int activeEnemiesHere = countSimActiveEnemies(state, simHeroZones[h.id], simEnemyAlive);
                    int evasive = "EVASIVE".equals(h.skill) ? 1 : 0;
                    int stepCost = 1 + Math.max(0, activeEnemiesHere - evasive);
                    int needFromGems = Math.max(0, stepCost - freeLeft);
                    if (needFromGems > moveGemsLeft || needFromGems > simHeroReserve[h.id]) {
                        break;
                    }
                    int nextHop = bestNextHop(state, simHeroZones[h.id], dist, simEnemyAlive);
                    if (nextHop < 0) break;

                    cmds.add("MOVE " + h.id + " " + nextHop);
                    int usedFree = Math.min(freeLeft, stepCost);
                    freeLeft -= usedFree;
                    moveGemsLeft -= needFromGems;
                    simHeroReserve[h.id] -= needFromGems;
                    simHeroZones[h.id] = nextHop;

                    Zone curZ = state.zones.get(nextHop);
                    if (curZ.type == GameConstants.ZONE_HUT && !simSearchedHuts.contains(curZ.id)
                        && !simCaptiveCarried && manipGemsLeft >= 1 && simHeroReserve[h.id] >= 1) {
                        cmds.add("MANIP " + h.id);
                        simSearchedHuts.add(curZ.id);
                        manipGemsLeft--;
                        simHeroReserve[h.id]--;
                        break;
                    }
                }
            }

            // Manipulate at destination (Hut search, Dropped Captive pickup, Chest loot)
            Zone endZone = state.zones.get(simHeroZones[h.id]);
            if (manipGemsLeft >= 1 && simHeroReserve[h.id] >= 1) {
                boolean canSearch = endZone.type == GameConstants.ZONE_HUT && !simSearchedHuts.contains(endZone.id) && !simCaptiveCarried;
                boolean canPickDropped = state.captiveState == GameConstants.CAPTIVE_DROPPED && simCaptiveZone == endZone.id;
                boolean canChest = endZone.hasChest && !simLootedChests.contains(endZone.id) && (!bossAlive || endZone.id != state.sanctumZone);
                if (canSearch || canPickDropped || canChest) {
                    cmds.add("MANIP " + h.id);
                    if (canSearch) simSearchedHuts.add(endZone.id);
                    if (canChest) simLootedChests.add(endZone.id);
                    if (canPickDropped) simCaptiveCarried = true;
                    manipGemsLeft--;
                    simHeroReserve[h.id]--;
                }
            }

            // Straightforward melee burst (1 gem on minions, up to 2 on Boss)
            Enemy meleeTarget = pickMeleeTargetInZone(state, simHeroZones[h.id], simEnemyAlive, simEnemyHp);
            if (meleeTarget != null && h.meleeTier > 0 && meleeExertLeft > 0 && simHeroReserve[h.id] >= 1) {
                int keepReserve = ("SANCTUM".equals(targetLabel(state, simHeroZones[h.id])) || meleeTarget.id == state.bossEnemyId) ? 0 : 1;
                int avail = Math.min(meleeExertLeft, simHeroReserve[h.id] - keepReserve);
                int g = Math.min(avail, "BOSS".equals(meleeTarget.unitType) ? 2 : 1);
                if (g >= 1 && g <= simHeroReserve[h.id]) {
                    cmds.add("MELEE " + h.id + " " + meleeTarget.id + " " + g);
                    meleeExertLeft -= g;
                    simHeroReserve[h.id] -= g;
                    int dmg = Math.max(0, GameConstants.calcHits(h.meleeTier, g) - meleeTarget.armor);
                    applySimMeleeDamage(state, h, meleeTarget, dmg, simEnemyHp, simEnemyAlive);
                    if (state.bossEnemyId >= 0 && !simEnemyAlive[state.bossEnemyId]) bossAlive = false;
                }
            }
        }

        if (cmds.isEmpty()) {
            return "WAIT";
        }
        cmds.add("MSG Squad strike round " + state.round);
        return String.join("; ", cmds);
    }

    private static boolean canFinishObjectiveNow(RaidEngine engine, RaidState state, Hero h,
                                                 boolean bossAlive, boolean captiveCarried) {
        if (h.carryingCaptive && !bossAlive && h.zone != state.exitZone) {
            return true;
        }
        if (bossAlive && h.zone == state.zones.get(state.sanctumZone).id && h.reserve >= 2) {
            return true;
        }
        Zone cur = state.zones.get(h.zone);
        return !captiveCarried && cur.type == GameConstants.ZONE_HUT && !cur.hutSearched && h.reserve >= 1;
    }

    private static int chooseTargetZone(RaidState state, Hero h, boolean bossAlive,
                                        boolean captiveCarried, int captiveZone, boolean hutAlreadyAssigned) {
        if (h.carryingCaptive) {
            if (!bossAlive) return state.exitZone;
            Enemy boss = null;
            for (Enemy e : state.enemies) {
                if (e.id == state.bossEnemyId) boss = e;
            }
            return boss != null ? boss.zone : state.sanctumZone;
        }
        if (state.captiveState == GameConstants.CAPTIVE_DROPPED && captiveZone >= 0) {
            return captiveZone;
        }
        // Scouts/Huntresses check nearest unsearched Hut when Captive is still hidden
        boolean explorer = "SCOUT".equals(h.archetype) || "HUNTRESS".equals(h.archetype) || (!captiveCarried && h.id > 0);
        if (!captiveCarried && explorer) {
            int nearestHut = findNearestUnsearchedHut(state, h.zone);
            if (nearestHut >= 0) {
                return nearestHut;
            }
        }
        if (bossAlive) {
            Enemy boss = null;
            for (Enemy e : state.enemies) {
                if (e.id == state.bossEnemyId) boss = e;
            }
            return boss != null ? boss.zone : state.sanctumZone;
        }
        if (!captiveCarried) {
            int nearestHut = findNearestUnsearchedHut(state, h.zone);
            if (nearestHut >= 0) {
                return nearestHut;
            }
        }
        // Escort carrier by staying near EXIT or clearing enemies around carrier
        for (Hero ally : state.heroes) {
            if (ally.alive && ally.carryingCaptive) {
                return ally.zone;
            }
        }
        return state.exitZone;
    }

    private static int findNearestUnsearchedHut(RaidState state, int fromZone) {
        int[] dist = ScenarioGenerator.bfsDistances(state, fromZone);
        int best = -1;
        int bestD = Integer.MAX_VALUE;
        for (Zone z : state.zones) {
            if (z.type == GameConstants.ZONE_HUT && !z.hutSearched && dist[z.id] >= 0 && dist[z.id] < bestD) {
                bestD = dist[z.id];
                best = z.id;
            }
        }
        return best;
    }

    private static int bestNextHop(RaidState state, int curZone, int[] distToGoal, boolean[] simEnemyAlive) {
        int bestHop = -1;
        int bestDist = distToGoal[curZone];
        for (int nb : state.zones.get(curZone).neighbors) {
            if (distToGoal[nb] >= 0 && distToGoal[nb] < bestDist) {
                bestDist = distToGoal[nb];
                bestHop = nb;
            }
        }
        return bestHop;
    }

    private static Enemy pickRangedTarget(RaidState state, Hero h, int fromZone,
                                          boolean[] simEnemyAlive, int[] simEnemyHp) {
        Enemy best = null;
        double bestScore = -1e9;
        for (Enemy e : state.enemies) {
            if (!simEnemyAlive[e.id] || !RaidEngine.hasLineOfSight(state, fromZone, e.zone)) {
                continue;
            }
            boolean isBoss = "BOSS".equals(e.unitType);
            boolean sameOrAdj = (e.zone == fromZone || state.zones.get(fromZone).neighbors.contains(e.zone));
            if (!isBoss && !sameOrAdj) {
                continue;
            }
            double s = (isBoss ? 100.0 : 20.0) - simEnemyHp[e.id] * 2.0;
            if (s > bestScore) {
                bestScore = s;
                best = e;
            }
        }
        return best;
    }

    private static Enemy pickMeleeTargetInZone(RaidState state, int zoneId,
                                               boolean[] simEnemyAlive, int[] simEnemyHp) {
        Enemy best = null;
        double bestScore = -1e9;
        for (Enemy e : state.enemies) {
            if (!simEnemyAlive[e.id] || e.zone != zoneId) {
                continue;
            }
            double s = ("BOSS".equals(e.unitType) ? 100.0 : 25.0) - e.armor * 4.0 - simEnemyHp[e.id];
            if (s > bestScore) {
                bestScore = s;
                best = e;
            }
        }
        return best;
    }

    private static int minGemsToDealDamage(int tier, int targetArmor, int targetHp, int maxGems) {
        if (maxGems <= 0) return 0;
        for (int g = 1; g <= maxGems; g++) {
            int dmg = GameConstants.calcHits(tier, g) - targetArmor;
            if (dmg >= targetHp) {
                return g;
            }
        }
        // Otherwise spend highest count that still pierces armor
        for (int g = maxGems; g >= 1; g--) {
            if (GameConstants.calcHits(tier, g) - targetArmor > 0) {
                return g;
            }
        }
        return 0;
    }

    private static void applySimMeleeDamage(RaidState state, Hero h, Enemy target, int dmg,
                                            int[] simEnemyHp, boolean[] simEnemyAlive) {
        if (dmg <= 0) return;
        int overflow = Math.max(0, dmg - simEnemyHp[target.id]);
        simEnemyHp[target.id] = Math.max(0, simEnemyHp[target.id] - dmg);
        if (simEnemyHp[target.id] == 0) simEnemyAlive[target.id] = false;
        if (overflow > 0 && "CLEAVE".equals(h.skill)) {
            for (Enemy other : state.enemies) {
                if (other.id != target.id && simEnemyAlive[other.id] && other.zone == target.zone) {
                    simEnemyHp[other.id] = Math.max(0, simEnemyHp[other.id] - overflow);
                    if (simEnemyHp[other.id] == 0) simEnemyAlive[other.id] = false;
                    break;
                }
            }
        }
    }

    private static int countSimActiveEnemies(RaidState state, int zoneId, boolean[] simEnemyAlive) {
        int c = 0;
        for (Enemy e : state.enemies) {
            if (simEnemyAlive[e.id] && !e.slumbering && e.zone == zoneId) {
                c++;
            }
        }
        return c;
    }

    private static String targetLabel(RaidState state, int zoneId) {
        return state.zones.get(zoneId).type == GameConstants.ZONE_SANCTUM ? "SANCTUM" : "OTHER";
    }
}
