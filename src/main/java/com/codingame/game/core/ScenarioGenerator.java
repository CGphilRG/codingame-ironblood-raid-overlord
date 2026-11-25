package com.codingame.game.core;

import com.codingame.game.core.GameConstants.HeroTemplate;
import com.codingame.game.core.GameConstants.UnitTemplate;
import com.codingame.game.core.RaidModels.Enemy;
import com.codingame.game.core.RaidModels.Hero;
import com.codingame.game.core.RaidModels.RaidState;
import com.codingame.game.core.RaidModels.RiverTile;
import com.codingame.game.core.RaidModels.Zone;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Random;

/**
 * Procedural deterministic generator producing balanced Zone graphs, Hero squads,
 * secret Captive locations, and Overlord Runic Rivers from (seed, tier).
 */
public final class ScenarioGenerator {
    private ScenarioGenerator() {
    }

    /**
     * Parses CodinGame testIn lines ("seed tier") safely with fallback defaults.
     */
    public static long[] parseSeedAndTier(List<String> lines) {
        long seed = 42L;
        int tier = 1;
        if (lines != null && !lines.isEmpty()) {
            String first = lines.get(0).trim();
            if (!first.isEmpty()) {
                String[] parts = first.split("\\s+");
                seed = Long.parseLong(parts[0]);
                if (parts.length >= 2) {
                    tier = Math.max(1, Math.min(3, Integer.parseInt(parts[1])));
                }
            }
        }
        return new long[]{seed, tier};
    }

    /**
     * Generates a validated initial RaidState for the given (seed, tier).
     */
    public static RaidState generate(long seed, int tier) {
        int clampedTier = Math.max(1, Math.min(3, tier));
        Random rng = new Random(seed * 6364136223846793005L + clampedTier * 1442695040888963407L);

        RaidState state = new RaidState();
        state.seed = seed;
        state.tier = clampedTier;

        configureDimensions(state, rng);
        buildZonesAndPassages(state, rng);
        placePointsOfInterest(state, rng);
        buildHeroes(state, rng);
        buildRiverAndEnemies(state, rng);

        return state;
    }

    private static void configureDimensions(RaidState state, Random rng) {
        if (state.tier == 1) {
            state.cols = 4;
            state.rows = 3;
            state.maxRounds = 8;
            state.overlordTotalGems = 14;
            state.overlordReserve = 8;
            state.overlordFatigue = 6;
            state.recoverRate = 6;
            state.reinforcePower = 4;
        } else if (state.tier == 2) {
            boolean wide = rng.nextBoolean();
            state.cols = wide ? 5 : 4;
            state.rows = 3;
            state.maxRounds = wide ? 9 : 8;
            state.overlordTotalGems = 15;
            state.overlordReserve = 9;
            state.overlordFatigue = 6;
            state.recoverRate = 7;
            state.reinforcePower = 4;
        } else {
            boolean square4 = rng.nextBoolean();
            state.cols = square4 ? 4 : 5;
            state.rows = square4 ? 4 : 3;
            state.maxRounds = 9;
            state.overlordTotalGems = 16;
            state.overlordReserve = 9;
            state.overlordFatigue = 7;
            state.recoverRate = 7;
            state.reinforcePower = 5;
        }
    }

    private static void buildZonesAndPassages(RaidState state, Random rng) {
        int cols = state.cols;
        int rows = state.rows;
        int zoneCount = cols * rows;

        for (int gy = 0; gy < rows; gy++) {
            for (int gx = 0; gx < cols; gx++) {
                int id = gy * cols + gx;
                state.zones.add(new Zone(id, gx, gy, GameConstants.ZONE_CLEARING));
            }
        }

        state.exitZone = (rows / 2) * cols;
        state.zones.get(state.exitZone).type = GameConstants.ZONE_EXIT;

        state.sanctumZone = (rows / 2) * cols + (cols - 1);
        state.zones.get(state.sanctumZone).type = GameConstants.ZONE_SANCTUM;

        // Reinforcement anchors on top-right and (for Tier >= 2) bottom-mid flank
        int reinfA = cols - 1;
        if (reinfA != state.sanctumZone) {
            state.zones.get(reinfA).type = GameConstants.ZONE_REINFORCEMENT;
        }
        if (state.tier >= 2) {
            int reinfB = (rows - 1) * cols + (cols / 2);
            if (state.zones.get(reinfB).type == GameConstants.ZONE_CLEARING) {
                state.zones.get(reinfB).type = GameConstants.ZONE_REINFORCEMENT;
            }
        }

        // Start with full orthogonal grid connectivity
        for (int gy = 0; gy < rows; gy++) {
            for (int gx = 0; gx < cols; gx++) {
                int id = gy * cols + gx;
                Zone z = state.zones.get(id);
                if (gy > 0) z.nNorth = (gy - 1) * cols + gx;
                if (gx + 1 < cols) z.nEast = gy * cols + (gx + 1);
                if (gy + 1 < rows) z.nSouth = (gy + 1) * cols + gx;
                if (gx > 0) z.nWest = gy * cols + (gx - 1);
                refreshNeighborList(z);
            }
        }

        // Collect internal horizontal & vertical candidate walls (skip border of EXIT)
        List<int[]> wallCandidates = new ArrayList<>();
        for (int gy = 0; gy < rows; gy++) {
            for (int gx = 0; gx < cols; gx++) {
                int u = gy * cols + gx;
                if (gx + 1 < cols) {
                    int v = u + 1;
                    if (u != state.exitZone && v != state.exitZone) {
                        wallCandidates.add(new int[]{u, v});
                    }
                }
                if (gy + 1 < rows) {
                    int v = u + cols;
                    if (u != state.exitZone && v != state.exitZone) {
                        wallCandidates.add(new int[]{u, v});
                    }
                }
            }
        }
        Collections.shuffle(wallCandidates, rng);

        int targetWalls = (state.tier == 1) ? (2 + rng.nextInt(2)) : (3 + rng.nextInt(3));
        int wallsAdded = 0;
        for (int[] edge : wallCandidates) {
            if (wallsAdded >= targetWalls) {
                break;
            }
            int u = edge[0];
            int v = edge[1];
            Zone zu = state.zones.get(u);
            Zone zv = state.zones.get(v);
            if (zu.neighbors.size() <= 2 || zv.neighbors.size() <= 2) {
                continue;
            }
            disconnectZones(zu, zv);
            if (isGraphValid(state, zoneCount)) {
                wallsAdded++;
            } else {
                reconnectZones(state, zu, zv);
            }
        }
    }

    private static void placePointsOfInterest(RaidState state, Random rng) {
        List<Integer> hutCandidates = new ArrayList<>();
        for (Zone z : state.zones) {
            if (z.type == GameConstants.ZONE_CLEARING && z.gx >= 1) {
                hutCandidates.add(z.id);
            }
        }
        Collections.shuffle(hutCandidates, rng);

        int targetHuts = (state.tier == 1) ? 3 : (state.tier == 2 ? (3 + rng.nextInt(2)) : 4);
        List<Integer> huts = new ArrayList<>();
        for (int id : hutCandidates) {
            if (huts.size() >= targetHuts) {
                break;
            }
            Zone z = state.zones.get(id);
            z.type = GameConstants.ZONE_HUT;
            huts.add(id);
        }

        // Choose secret hut holding the Captive (prefer mid-to-far huts so raid feels epic)
        int[] distFromExit = bfsDistances(state, state.exitZone);
        huts.sort((a, b) -> Integer.compare(distFromExit[b], distFromExit[a]));
        int pickIdx = Math.min(huts.size() - 1, rng.nextInt(Math.max(1, Math.min(huts.size(), 3))));
        state.secretCaptiveHutZone = huts.get(pickIdx);
        state.captiveState = GameConstants.CAPTIVE_HIDDEN;
        state.captiveZone = -1;

        // Place treasure chests: 1 in Sanctum, optional 1 in non-captive hut
        state.zones.get(state.sanctumZone).hasChest = true;
        if (state.tier >= 2) {
            for (int hId : huts) {
                if (hId != state.secretCaptiveHutZone && rng.nextBoolean()) {
                    state.zones.get(hId).hasChest = true;
                    break;
                }
            }
        }
    }

    private static void buildHeroes(RaidState state, Random rng) {
        List<HeroTemplate> companions = new ArrayList<>(Arrays.asList(
            GameConstants.HERO_SCOUT,
            GameConstants.HERO_HUNTRESS,
            GameConstants.HERO_MYSTIC
        ));
        Collections.shuffle(companions, rng);

        List<HeroTemplate> roster = new ArrayList<>();
        roster.add(GameConstants.HERO_BARBARIAN);
        int heroCount = (state.tier == 1) ? 2 : (state.tier == 2 ? (2 + rng.nextInt(2)) : 3);
        for (int i = 0; i < heroCount - 1; i++) {
            roster.add(companions.get(i));
        }

        for (int i = 0; i < roster.size(); i++) {
            int initFatigue = (state.tier >= 2 && i == 1) ? (state.tier == 3 ? 2 : rng.nextInt(2)) : 0;
            state.heroes.add(new Hero(i, roster.get(i), state.exitZone, initFatigue));
        }
    }

    private static void buildRiverAndEnemies(RaidState state, Random rng) {
        List<UnitTemplate> deck = new ArrayList<>();
        deck.add(GameConstants.UNIT_GUARD);
        deck.add(GameConstants.UNIT_ARCHER);
        deck.add(GameConstants.UNIT_BEAST);
        deck.add(GameConstants.UNIT_GUARD);
        if (state.tier >= 2) {
            deck.add(GameConstants.UNIT_BRUTE);
        }
        if (state.tier >= 3) {
            deck.add(rng.nextBoolean() ? GameConstants.UNIT_ARCHER : GameConstants.UNIT_BEAST);
        }

        List<Integer> minionSpawnPool = collectMinionSpawnZones(state);
        Collections.shuffle(minionSpawnPool, rng);

        int slumberTarget = (state.tier == 1) ? 0 : (state.tier == 2 ? 1 : (1 + rng.nextInt(2)));
        List<RiverTile> minionTiles = new ArrayList<>();
        int tileId = 0;
        for (UnitTemplate tpl : deck) {
            boolean sleep = tileId < slumberTarget;
            RiverTile tile = new RiverTile(tileId++, tpl,
                sleep ? GameConstants.TILE_SLUMBERING : GameConstants.TILE_READY);
            tile.spawnZoneHint = minionSpawnPool.get(tile.id % minionSpawnPool.size());
            minionTiles.add(tile);
        }

        RiverTile bossTile = new RiverTile(tileId++, GameConstants.UNIT_BOSS, GameConstants.TILE_READY);
        bossTile.spawnZoneHint = state.sanctumZone;
        state.bossTileId = bossTile.id;

        RiverTile eventTile = new RiverTile(tileId++, GameConstants.UNIT_EVENT, GameConstants.TILE_READY);

        state.allTiles.addAll(minionTiles);
        state.allTiles.add(bossTile);
        state.allTiles.add(eventTile);

        // Arrange initial River order cleanly: shuffle minions, insert BOSS & EVENT at strategic middle slots
        List<Integer> order = new ArrayList<>();
        for (RiverTile mt : minionTiles) {
            order.add(mt.id);
        }
        Collections.shuffle(order, rng);
        int bossSlot = Math.min(order.size(), 2 + rng.nextInt(Math.max(1, order.size() - 1)));
        order.add(bossSlot, bossTile.id);
        int eventSlot = Math.min(order.size(), 2 + rng.nextInt(Math.max(1, order.size() - 1)));
        order.add(eventSlot, eventTile.id);
        state.riverOrder.addAll(order);

        // Instantiate concrete enemy figures
        int enemyId = 0;
        for (RiverTile tile : state.allTiles) {
            if (tile.maxModels <= 0) {
                continue;
            }
            boolean slumber = (tile.state == GameConstants.TILE_SLUMBERING);
            for (int m = 0; m < tile.maxModels; m++) {
                int z;
                if ("BOSS".equals(tile.unitType)) {
                    z = state.sanctumZone;
                } else {
                    z = minionSpawnPool.get((enemyId + m) % minionSpawnPool.size());
                }
                Enemy enemy = new Enemy(enemyId++, tile, z, slumber);
                state.enemies.add(enemy);
                if ("BOSS".equals(tile.unitType)) {
                    state.bossEnemyId = enemy.id;
                }
            }
        }
    }

    private static List<Integer> collectMinionSpawnZones(RaidState state) {
        List<Integer> zones = new ArrayList<>();
        for (Zone z : state.zones) {
            if (z.id == state.exitZone) {
                continue;
            }
            if (z.gx >= 2) {
                zones.add(z.id);
            }
        }
        if (zones.isEmpty()) {
            zones.add(state.sanctumZone);
        }
        return zones;
    }

    private static boolean isGraphValid(RaidState state, int zoneCount) {
        int[] dist = bfsDistances(state, state.exitZone);
        for (int i = 0; i < zoneCount; i++) {
            if (dist[i] < 0) {
                return false;
            }
        }
        if (dist[state.sanctumZone] < state.cols - 1) {
            return false;
        }
        for (Zone z : state.zones) {
            if (z.type == GameConstants.ZONE_REINFORCEMENT && dist[z.id] > state.cols + 2) {
                return false;
            }
        }
        return true;
    }

    /**
     * Computes shortest path hop distances across Zone edges from the source zone.
     */
    public static int[] bfsDistances(RaidState state, int startZone) {
        int n = state.zones.size();
        int[] dist = new int[n];
        Arrays.fill(dist, -1);
        Queue<Integer> q = new ArrayDeque<>();
        dist[startZone] = 0;
        q.add(startZone);
        while (!q.isEmpty()) {
            int cur = q.poll();
            for (int nxt : state.zones.get(cur).neighbors) {
                if (dist[nxt] < 0) {
                    dist[nxt] = dist[cur] + 1;
                    q.add(nxt);
                }
            }
        }
        return dist;
    }

    private static void disconnectZones(Zone a, Zone b) {
        if (a.nNorth == b.id) a.nNorth = -1;
        if (a.nEast == b.id) a.nEast = -1;
        if (a.nSouth == b.id) a.nSouth = -1;
        if (a.nWest == b.id) a.nWest = -1;
        if (b.nNorth == a.id) b.nNorth = -1;
        if (b.nEast == a.id) b.nEast = -1;
        if (b.nSouth == a.id) b.nSouth = -1;
        if (b.nWest == a.id) b.nWest = -1;
        refreshNeighborList(a);
        refreshNeighborList(b);
    }

    private static void reconnectZones(RaidState state, Zone a, Zone b) {
        if (a.gx == b.gx && a.gy == b.gy + 1) {
            a.nNorth = b.id;
            b.nSouth = a.id;
        } else if (a.gx == b.gx && a.gy + 1 == b.gy) {
            a.nSouth = b.id;
            b.nNorth = a.id;
        } else if (a.gx + 1 == b.gx && a.gy == b.gy) {
            a.nEast = b.id;
            b.nWest = a.id;
        } else if (a.gx == b.gx + 1 && a.gy == b.gy) {
            a.nWest = b.id;
            b.nEast = a.id;
        }
        refreshNeighborList(a);
        refreshNeighborList(b);
    }

    private static void refreshNeighborList(Zone z) {
        z.neighbors.clear();
        if (z.nNorth >= 0) z.neighbors.add(z.nNorth);
        if (z.nEast >= 0) z.neighbors.add(z.nEast);
        if (z.nSouth >= 0) z.neighbors.add(z.nSouth);
        if (z.nWest >= 0) z.neighbors.add(z.nWest);
    }
}
