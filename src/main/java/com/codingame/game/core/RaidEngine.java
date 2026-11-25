package com.codingame.game.core;

import com.codingame.game.core.RaidModels.Enemy;
import com.codingame.game.core.RaidModels.EventFx;
import com.codingame.game.core.RaidModels.Hero;
import com.codingame.game.core.RaidModels.RaidState;
import com.codingame.game.core.RaidModels.RiverTile;
import com.codingame.game.core.RaidModels.Zone;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic rules engine resolving Hero stances, Exertion Limits, Hindrance,
 * Wound-to-gem attrition, Runic River conveyor activations, Dredging, and Reinforcements.
 */
public final class RaidEngine {
    private final RaidState state;

    public RaidEngine(RaidState state) {
        this.state = state;
    }

    public RaidState getState() {
        return state;
    }

    public boolean isBossAlive() {
        Enemy boss = getEnemyById(state.bossEnemyId);
        return boss != null && boss.alive;
    }

    public int countAliveHeroes() {
        int count = 0;
        for (Hero h : state.heroes) {
            if (h.alive) count++;
        }
        return count;
    }

    public boolean areHeroesVictorious() {
        if (isBossAlive()) {
            return false;
        }
        if (state.captiveState == GameConstants.CAPTIVE_ESCAPED) {
            return true;
        }
        for (Hero h : state.heroes) {
            if (h.alive && h.carryingCaptive && h.zone == state.exitZone) {
                state.captiveState = GameConstants.CAPTIVE_ESCAPED;
                return true;
            }
        }
        return false;
    }

    public boolean areAllHeroesDead() {
        return countAliveHeroes() == 0;
    }

    /**
     * Checks Line of Sight between two zones: same zone, orthogonal passage, or 2-step open clearing path.
     */
    public static boolean hasLineOfSight(RaidState st, int z1, int z2) {
        if (z1 == z2) {
            return true;
        }
        Zone a = st.zones.get(z1);
        Zone b = st.zones.get(z2);
        if (a.neighbors.contains(z2)) {
            return true;
        }
        if (b.type == GameConstants.ZONE_HUT) {
            return false;
        }
        for (int midId : a.neighbors) {
            Zone mid = st.zones.get(midId);
            if (mid.type != GameConstants.ZONE_HUT && mid.neighbors.contains(z2)) {
                return true;
            }
        }
        return false;
    }

    public int countActiveEnemiesInZone(int zoneId) {
        int c = 0;
        for (Enemy e : state.enemies) {
            if (e.alive && !e.slumbering && e.zone == zoneId) {
                c++;
            }
        }
        return c;
    }

    public int countAliveHeroesInZone(int zoneId) {
        int c = 0;
        for (Hero h : state.heroes) {
            if (h.alive && h.zone == zoneId) {
                c++;
            }
        }
        return c;
    }

    public int countAliveModelsOnTile(int tileId) {
        int c = 0;
        for (Enemy e : state.enemies) {
            if (e.alive && e.tileId == tileId) {
                c++;
            }
        }
        return c;
    }

    public Hero getHeroById(int heroId) {
        if (heroId < 0 || heroId >= state.heroes.size()) {
            return null;
        }
        return state.heroes.get(heroId);
    }

    public Enemy getEnemyById(int enemyId) {
        if (enemyId < 0 || enemyId >= state.enemies.size()) {
            return null;
        }
        return state.enemies.get(enemyId);
    }

    public RiverTile getTileById(int tileId) {
        if (tileId < 0 || tileId >= state.allTiles.size()) {
            return null;
        }
        return state.allTiles.get(tileId);
    }

    public int getRiverSlotCost(int tileId) {
        int idx = state.riverOrder.indexOf(tileId);
        return idx < 0 ? -1 : idx + 1;
    }

    /**
     * Parses and resolves a complete Hero turn. Throws IllegalArgumentException only on malformed syntax.
     */
    public void executeHeroTurn(String commandLine, List<String> logs) {
        state.fxQueue.clear();
        state.lastHeroMsg = "";
        List<String> segments = splitCommands(commandLine);

        for (Hero h : state.heroes) {
            h.resetTurnTrackers();
            h.stance = GameConstants.STANCE_ACTIVE;
        }

        // First validate syntax of ALL segments up front before applying state changes
        List<String[]> parsed = new ArrayList<>();
        for (String seg : segments) {
            if (seg.startsWith("MSG ")) {
                state.lastHeroMsg = seg.substring(4).trim();
                continue;
            }
            if ("MSG".equals(seg)) {
                state.lastHeroMsg = "";
                continue;
            }
            String[] tok = seg.split("\\s+");
            validateHeroCommandSyntax(tok);
            parsed.add(tok);
        }

        // Phase 1: Stances & Gem recovery
        Set<Integer> stanceSet = new HashSet<>();
        for (String[] tok : parsed) {
            if ("STANCE".equals(tok[0])) {
                int hId = Integer.parseInt(tok[1]);
                int stanceCode = "REST".equals(tok[2]) ? GameConstants.STANCE_REST : GameConstants.STANCE_ACTIVE;
                Hero h = getHeroById(hId);
                if (h == null || !h.alive) {
                    logs.add("Ignored STANCE for dead/invalid hero " + hId);
                    continue;
                }
                if (stanceSet.add(hId)) {
                    h.stance = stanceCode;
                }
            }
        }

        int deadHeroBonus = state.heroes.size() - countAliveHeroes();
        for (Hero h : state.heroes) {
            if (!h.alive) continue;
            int baseRec = (h.stance == GameConstants.STANCE_REST)
                ? GameConstants.HERO_REST_RECOVER
                : GameConstants.HERO_ACTIVE_RECOVER;
            int rec = Math.min(h.fatigue, baseRec + deadHeroBonus);
            h.fatigue -= rec;
            h.reserve += rec;
            if (h.stance == GameConstants.STANCE_ACTIVE) {
                int penalty = h.carryingCaptive ? 1 : 0;
                h.freeMoveLeft = Math.max(0, h.freeMove - penalty);
            } else {
                h.freeMoveLeft = 0;
                if ("WARD".equals(h.skill)) {
                    applyWardSkill(h, logs);
                }
            }
        }

        // Phase 2: Sequential Hero actions
        for (String[] tok : parsed) {
            String cmd = tok[0];
            if ("STANCE".equals(cmd) || "WAIT".equals(cmd)) {
                continue;
            }
            if ("MOVE".equals(cmd)) {
                handleHeroMove(Integer.parseInt(tok[1]), Integer.parseInt(tok[2]), logs);
            } else if ("MELEE".equals(cmd)) {
                handleHeroMelee(Integer.parseInt(tok[1]), Integer.parseInt(tok[2]), Integer.parseInt(tok[3]), logs);
            } else if ("RANGED".equals(cmd)) {
                handleHeroRanged(Integer.parseInt(tok[1]), Integer.parseInt(tok[2]), Integer.parseInt(tok[3]), logs);
            } else if ("MANIP".equals(cmd)) {
                handleHeroManip(Integer.parseInt(tok[1]), logs);
            }
        }

        areHeroesVictorious();
    }

    private void validateHeroCommandSyntax(String[] tok) {
        String cmd = tok[0];
        switch (cmd) {
            case "WAIT" -> {
                if (tok.length != 1) throw new IllegalArgumentException("Invalid WAIT syntax");
            }
            case "STANCE" -> {
                if (tok.length != 3) throw new IllegalArgumentException("Invalid STANCE syntax");
                Integer.parseInt(tok[1]);
                if (!"ACTIVE".equals(tok[2]) && !"REST".equals(tok[2])) {
                    throw new IllegalArgumentException("Unknown stance: " + tok[2]);
                }
            }
            case "MOVE" -> {
                if (tok.length != 3) throw new IllegalArgumentException("Invalid MOVE syntax");
                Integer.parseInt(tok[1]);
                Integer.parseInt(tok[2]);
            }
            case "MELEE", "RANGED" -> {
                if (tok.length != 4) throw new IllegalArgumentException("Invalid attack syntax: " + cmd);
                Integer.parseInt(tok[1]);
                Integer.parseInt(tok[2]);
                Integer.parseInt(tok[3]);
            }
            case "MANIP" -> {
                if (tok.length != 2) throw new IllegalArgumentException("Invalid MANIP syntax");
                Integer.parseInt(tok[1]);
            }
            default -> throw new IllegalArgumentException("Unknown Hero command: " + cmd);
        }
    }

    private void applyWardSkill(Hero mystic, List<String> logs) {
        for (Hero ally : state.heroes) {
            if (ally.alive && ally.id != mystic.id && ally.zone == mystic.zone && ally.fatigue > 0) {
                ally.fatigue -= 1;
                ally.reserve += 1;
                state.fxQueue.add(new EventFx("HEAL", mystic.zone, ally.zone));
                logs.add(mystic.archetype + " WARD restored 1 gem to " + ally.archetype);
                break;
            }
        }
    }

    private void handleHeroMove(int heroId, int targetZone, List<String> logs) {
        Hero h = getHeroById(heroId);
        if (h == null || !h.alive || h.stance == GameConstants.STANCE_REST) {
            logs.add("Ignored MOVE for hero " + heroId);
            return;
        }
        if (targetZone < 0 || targetZone >= state.zones.size() || !state.zones.get(h.zone).neighbors.contains(targetZone)) {
            logs.add("Ignored non-adjacent MOVE " + h.zone + " -> " + targetZone);
            return;
        }
        int activeEnemies = countActiveEnemiesInZone(h.zone);
        int evasiveBypass = "EVASIVE".equals(h.skill) ? 1 : 0;
        int hindrance = Math.max(0, activeEnemies - evasiveBypass);
        int stepCost = 1 + hindrance;

        int useFree = Math.min(h.freeMoveLeft, stepCost);
        int gemsNeeded = stepCost - useFree;
        if (gemsNeeded > h.exertMove - h.moveGemsSpent || gemsNeeded > h.reserve) {
            logs.add("Ignored MOVE: insufficient movement/gems for hero " + heroId);
            return;
        }

        h.freeMoveLeft -= useFree;
        spendHeroGems(h, gemsNeeded);
        h.moveGemsSpent += gemsNeeded;
        h.zone = targetZone;
        if (h.carryingCaptive) {
            state.captiveZone = h.id;
        }
        if (h.carryingCaptive && h.zone == state.exitZone && !isBossAlive()) {
            state.captiveState = GameConstants.CAPTIVE_ESCAPED;
        }
    }

    private void handleHeroMelee(int heroId, int enemyId, int gems, List<String> logs) {
        Hero h = getHeroById(heroId);
        Enemy target = getEnemyById(enemyId);
        if (h == null || !h.alive || h.stance == GameConstants.STANCE_REST || h.meleeTier == GameConstants.TIER_NONE) {
            logs.add("Ignored MELEE: hero unavailable or no melee tier");
            return;
        }
        if (target == null || !target.alive || target.zone != h.zone) {
            logs.add("Ignored MELEE: invalid or non-same-zone target " + enemyId);
            return;
        }
        if (gems <= 0 || gems > h.exertMelee - h.meleeGemsSpent || gems > h.reserve) {
            logs.add("Ignored MELEE: invalid/unaffordable gem count " + gems);
            return;
        }

        spendHeroGems(h, gems);
        h.meleeGemsSpent += gems;
        int hits = GameConstants.calcHits(h.meleeTier, gems);
        int dmg = Math.max(0, hits - target.armor);
        state.fxQueue.add(new EventFx("SLASH", h.zone, target.zone));

        int overflow = Math.max(0, dmg - target.hp);
        applyDamageToEnemy(target, dmg, logs);

        if (overflow > 0 && "CLEAVE".equals(h.skill)) {
            for (Enemy other : state.enemies) {
                if (other.alive && other.id != target.id && other.zone == h.zone) {
                    applyDamageToEnemy(other, overflow, logs);
                    logs.add("CLEAVE spilled " + overflow + " damage to enemy " + other.id);
                    break;
                }
            }
        }
    }

    private void handleHeroRanged(int heroId, int enemyId, int gems, List<String> logs) {
        Hero h = getHeroById(heroId);
        Enemy target = getEnemyById(enemyId);
        if (h == null || !h.alive || h.stance == GameConstants.STANCE_REST || h.rangedTier == GameConstants.TIER_NONE) {
            logs.add("Ignored RANGED: hero unavailable or no ranged tier");
            return;
        }
        if (target == null || !target.alive || !hasLineOfSight(state, h.zone, target.zone)) {
            logs.add("Ignored RANGED: target invalid or no Line of Sight");
            return;
        }
        if (gems <= 0 || gems > h.exertRanged - h.rangedGemsSpent || gems > h.reserve) {
            logs.add("Ignored RANGED: invalid/unaffordable gem count " + gems);
            return;
        }

        spendHeroGems(h, gems);
        h.rangedGemsSpent += gems;
        int hits = GameConstants.calcHits(h.rangedTier, gems);
        int dmg = Math.max(0, hits - target.armor);
        state.fxQueue.add(new EventFx("FIREBALL", h.zone, target.zone));
        applyDamageToEnemy(target, dmg, logs);
    }

    private void handleHeroManip(int heroId, List<String> logs) {
        Hero h = getHeroById(heroId);
        if (h == null || !h.alive || h.stance == GameConstants.STANCE_REST) {
            logs.add("Ignored MANIP: hero unavailable");
            return;
        }
        if (h.exertManip - h.manipGemsSpent < 1 || h.reserve < 1) {
            logs.add("Ignored MANIP: no exertion or reserve gem left");
            return;
        }
        Zone z = state.zones.get(h.zone);
        boolean didSomething = false;

        if (z.type == GameConstants.ZONE_HUT && !z.hutSearched) {
            spendHeroGems(h, 1);
            h.manipGemsSpent += 1;
            z.hutSearched = true;
            didSomething = true;
            if (z.id == state.secretCaptiveHutZone && state.captiveState == GameConstants.CAPTIVE_HIDDEN) {
                state.captiveState = GameConstants.CAPTIVE_CARRIED;
                state.captiveZone = h.id;
                h.carryingCaptive = true;
                state.fxQueue.add(new EventFx("HEAL", z.id, z.id));
                logs.add(h.archetype + " rescued the Captive in hut " + z.id + "!");
            } else {
                logs.add(h.archetype + " searched hut " + z.id + " (empty).");
            }
        } else if (state.captiveState == GameConstants.CAPTIVE_DROPPED && state.captiveZone == z.id) {
            spendHeroGems(h, 1);
            h.manipGemsSpent += 1;
            state.captiveState = GameConstants.CAPTIVE_CARRIED;
            state.captiveZone = h.id;
            h.carryingCaptive = true;
            didSomething = true;
            logs.add(h.archetype + " picked up the Captive in zone " + z.id + ".");
        } else if (z.hasChest) {
            spendHeroGems(h, 1);
            h.manipGemsSpent += 1;
            z.hasChest = false;
            state.treasuresCollected++;
            didSomething = true;
            logs.add(h.archetype + " looted a Treasure Chest!");
        }

        if (!didSomething) {
            logs.add("Ignored MANIP: nothing interactable in zone " + z.id);
        }
    }

    private void applyDamageToEnemy(Enemy e, int dmg, List<String> logs) {
        if (dmg <= 0 || !e.alive) {
            return;
        }
        e.hp = Math.max(0, e.hp - dmg);
        if (e.hp == 0) {
            e.alive = false;
            e.slumbering = false;
            state.enemiesKilled++;
            RiverTile tile = getTileById(e.tileId);
            if (tile != null && countAliveModelsOnTile(tile.id) == 0) {
                tile.state = GameConstants.TILE_DEAD;
            }
            logs.add(e.unitType + " #" + e.id + " eliminated!");
        }
    }

    private void spendHeroGems(Hero h, int count) {
        h.reserve -= count;
        h.fatigue += count;
    }

    /**
     * Parses and resolves a complete Overlord turn (Recovery, Dredging, max 2 River Activations, Move/Attack/Reinforce).
     */
    public void executeOverlordTurn(String commandLine, List<String> logs) {
        state.fxQueue.clear();
        state.lastOverlordMsg = "";

        // Start-of-turn Overlord recovery & reset
        int recovered = Math.min(state.overlordFatigue, state.recoverRate);
        state.overlordFatigue -= recovered;
        state.overlordReserve += recovered;
        state.activationsUsed = 0;
        state.reinforcePointsLeft = 0;
        for (Enemy e : state.enemies) {
            e.movesLeft = 0;
            e.attackedThisTurn = false;
        }

        List<String> segments = splitCommands(commandLine);
        List<String[]> parsed = new ArrayList<>();
        for (String seg : segments) {
            if (seg.startsWith("MSG ")) {
                state.lastOverlordMsg = seg.substring(4).trim();
                continue;
            }
            if ("MSG".equals(seg)) {
                state.lastOverlordMsg = "";
                continue;
            }
            String[] tok = seg.split("\\s+");
            validateOverlordCommandSyntax(tok);
            parsed.add(tok);
        }

        Set<Integer> activatedTilesThisTurn = new HashSet<>();
        for (String[] tok : parsed) {
            String cmd = tok[0];
            if ("PASS".equals(cmd) || "WAIT".equals(cmd)) {
                continue;
            }
            if ("DREDGE".equals(cmd)) {
                handleOverlordDredge(Integer.parseInt(tok[1]), logs);
            } else if ("ACTIVATE".equals(cmd)) {
                handleOverlordActivate(Integer.parseInt(tok[1]), activatedTilesThisTurn, logs);
            } else if ("MOVE".equals(cmd)) {
                handleOverlordMove(Integer.parseInt(tok[1]), Integer.parseInt(tok[2]), logs);
            } else if ("ATTACK".equals(cmd)) {
                handleOverlordAttack(Integer.parseInt(tok[1]), Integer.parseInt(tok[2]), activatedTilesThisTurn, logs);
            } else if ("REINFORCE".equals(cmd)) {
                handleOverlordReinforce(Integer.parseInt(tok[1]), Integer.parseInt(tok[2]), logs);
            }
        }
    }

    private void validateOverlordCommandSyntax(String[] tok) {
        String cmd = tok[0];
        switch (cmd) {
            case "PASS", "WAIT" -> {
                if (tok.length != 1) throw new IllegalArgumentException("Invalid PASS/WAIT syntax");
            }
            case "DREDGE", "ACTIVATE" -> {
                if (tok.length != 2) throw new IllegalArgumentException("Invalid tile syntax: " + cmd);
                Integer.parseInt(tok[1]);
            }
            case "MOVE", "ATTACK", "REINFORCE" -> {
                if (tok.length != 3) throw new IllegalArgumentException("Invalid command syntax: " + cmd);
                Integer.parseInt(tok[1]);
                Integer.parseInt(tok[2]);
            }
            default -> throw new IllegalArgumentException("Unknown Overlord command: " + cmd);
        }
    }

    private void handleOverlordDredge(int tileId, List<String> logs) {
        RiverTile tile = getTileById(tileId);
        int idx = state.riverOrder.indexOf(tileId);
        if (tile == null || idx < 0 || tile.state != GameConstants.TILE_DEAD || state.overlordReserve < GameConstants.DREDGE_COST) {
            logs.add("Ignored DREDGE on tile " + tileId);
            return;
        }
        state.overlordReserve -= GameConstants.DREDGE_COST;
        state.overlordFatigue += GameConstants.DREDGE_COST;
        tile.dredged = true;
        state.riverOrder.remove(idx);
        logs.add("Dredged dead tile " + tile.unitType + " (#" + tileId + ") for 2 gems.");
    }

    private void handleOverlordActivate(int tileId, Set<Integer> activatedTilesThisTurn, List<String> logs) {
        RiverTile tile = getTileById(tileId);
        int idx = state.riverOrder.indexOf(tileId);
        if (tile == null || idx < 0 || state.activationsUsed >= GameConstants.MAX_ACTIVATIONS_PER_TURN) {
            logs.add("Ignored ACTIVATE on tile " + tileId + " (invalid or activation limit reached)");
            return;
        }
        if (tile.state == GameConstants.TILE_DEAD || activatedTilesThisTurn.contains(tileId)) {
            logs.add("Ignored ACTIVATE: tile " + tileId + " dead or already activated");
            return;
        }
        int cost = idx + 1;
        if (state.overlordReserve < cost) {
            logs.add("Ignored ACTIVATE: cost " + cost + " exceeds reserve " + state.overlordReserve);
            return;
        }

        state.overlordReserve -= cost;
        state.overlordFatigue += cost;
        state.activationsUsed++;
        activatedTilesThisTurn.add(tileId);

        // Conveyor shift: slide tiles right of idx left by 1, send tileId to rightmost slot
        state.riverOrder.remove(idx);
        state.riverOrder.add(tileId);

        if ("EVENT".equals(tile.unitType)) {
            state.reinforcePointsLeft += state.reinforcePower;
            logs.add("Activated EVENT tile (cost " + cost + ") -> +" + state.reinforcePower + " Reinforce Points!");
            return;
        }

        tile.state = GameConstants.TILE_READY;
        for (Enemy e : state.enemies) {
            if (e.alive && e.tileId == tileId) {
                e.slumbering = false;
                e.movesLeft = e.movePoints;
                e.attackedThisTurn = false;
            }
        }
        logs.add("Activated " + tile.unitType + " (#" + tileId + ") for " + cost + " gems.");
    }

    private void handleOverlordMove(int enemyId, int targetZone, List<String> logs) {
        Enemy e = getEnemyById(enemyId);
        if (e == null || !e.alive || e.slumbering || e.movesLeft <= 0) {
            logs.add("Ignored Overlord MOVE for enemy " + enemyId);
            return;
        }
        if (targetZone < 0 || targetZone >= state.zones.size() || !state.zones.get(e.zone).neighbors.contains(targetZone)) {
            logs.add("Ignored Overlord MOVE: non-adjacent zone " + targetZone);
            return;
        }
        int stepCost = 1 + countAliveHeroesInZone(e.zone);
        if (e.movesLeft < stepCost) {
            logs.add("Ignored Overlord MOVE: Hindrance cost " + stepCost + " > movesLeft " + e.movesLeft);
            return;
        }
        e.movesLeft -= stepCost;
        e.zone = targetZone;
    }

    private void handleOverlordAttack(int enemyId, int heroId, Set<Integer> activatedTilesThisTurn, List<String> logs) {
        Enemy e = getEnemyById(enemyId);
        Hero h = getHeroById(heroId);
        if (e == null || !e.alive || e.slumbering || e.attackedThisTurn || !activatedTilesThisTurn.contains(e.tileId)) {
            logs.add("Ignored Overlord ATTACK: enemy " + enemyId + " not ready");
            return;
        }
        if (h == null || !h.alive) {
            logs.add("Ignored Overlord ATTACK: hero " + heroId + " invalid or dead");
            return;
        }
        boolean inRange = (e.attackRange == 1)
            ? (e.zone == h.zone)
            : hasLineOfSight(state, e.zone, h.zone);
        if (!inRange) {
            logs.add("Ignored Overlord ATTACK: target out of range / blocked LoS");
            return;
        }

        e.attackedThisTurn = true;
        int guardArmor = hasAllyWithSkillInZone(h, "GUARD") ? 1 : 0;
        int restArmor = (h.stance == GameConstants.STANCE_REST) ? 1 : 0;
        int totalArmor = h.armor + restArmor + guardArmor;
        int wounds = Math.max(0, e.attackPower - totalArmor);

        state.fxQueue.add(new EventFx(e.attackRange >= 2 ? "FIREBALL" : "SLASH", e.zone, h.zone));
        if (wounds > 0) {
            inflictHeroWounds(h, wounds, logs);
        }
    }

    private boolean hasAllyWithSkillInZone(Hero target, String skillName) {
        for (Hero ally : state.heroes) {
            if (ally.alive && ally.id != target.id && ally.zone == target.zone && skillName.equals(ally.skill)) {
                return true;
            }
        }
        return false;
    }

    private void inflictHeroWounds(Hero h, int wounds, List<String> logs) {
        int remaining = h.reserve + h.fatigue;
        int actual = Math.min(remaining, wounds);
        int fromReserve = Math.min(h.reserve, actual);
        h.reserve -= fromReserve;
        int fromFatigue = actual - fromReserve;
        h.fatigue -= fromFatigue;
        h.wounds += actual;
        state.totalHeroWoundsInflicted += actual;

        if (h.reserve + h.fatigue == 0) {
            h.alive = false;
            h.freeMoveLeft = 0;
            if (h.carryingCaptive) {
                h.carryingCaptive = false;
                state.captiveState = GameConstants.CAPTIVE_DROPPED;
                state.captiveZone = h.zone;
            }
            logs.add(h.archetype + " took " + actual + " wounds and fell!");
        } else {
            logs.add(h.archetype + " suffered " + actual + " wounds.");
        }
    }

    private void handleOverlordReinforce(int tileId, int spawnZoneId, List<String> logs) {
        RiverTile tile = getTileById(tileId);
        if (tile == null || tile.dredged || "BOSS".equals(tile.unitType) || "EVENT".equals(tile.unitType)) {
            logs.add("Ignored REINFORCE for tile " + tileId);
            return;
        }
        if (state.reinforcePointsLeft < tile.reinforceCost) {
            logs.add("Ignored REINFORCE: insufficient reinforce points");
            return;
        }
        if (spawnZoneId < 0 || spawnZoneId >= state.zones.size()
            || state.zones.get(spawnZoneId).type != GameConstants.ZONE_REINFORCEMENT) {
            logs.add("Ignored REINFORCE: zone " + spawnZoneId + " is not a reinforcement zone");
            return;
        }
        Enemy deadModel = null;
        for (Enemy e : state.enemies) {
            if (!e.alive && e.tileId == tileId) {
                deadModel = e;
                break;
            }
        }
        if (deadModel == null) {
            logs.add("Ignored REINFORCE: tile " + tileId + " already at max models");
            return;
        }

        state.reinforcePointsLeft -= tile.reinforceCost;
        deadModel.alive = true;
        deadModel.slumbering = false;
        deadModel.hp = deadModel.maxHp;
        deadModel.zone = spawnZoneId;
        deadModel.movesLeft = 0;
        deadModel.attackedThisTurn = true;
        tile.state = GameConstants.TILE_READY;
        state.fxQueue.add(new EventFx("HEAL", spawnZoneId, spawnZoneId));
        logs.add("Reinforced " + tile.unitType + " (#" + deadModel.id + ") at zone " + spawnZoneId + ".");
    }

    /**
     * Heroes Optimization score formula used both on victory and viewer live preview.
     */
    public int computeHeroesScore() {
        int remainingGems = 0;
        for (Hero h : state.heroes) {
            if (h.alive) remainingGems += h.remainingGems();
        }
        if (areHeroesVictorious()) {
            return 1000
                + (state.maxRounds - state.round) * 120
                + remainingGems * 20
                + state.enemiesKilled * 20
                + state.treasuresCollected * 50;
        }
        int progress = state.enemiesKilled * 15 + state.treasuresCollected * 30;
        if (!isBossAlive()) progress += 150;
        if (state.captiveState == GameConstants.CAPTIVE_CARRIED) progress += 120;
        return progress;
    }

    /**
     * Overlord Optimization score formula used on Overlord victory.
     */
    public int computeOverlordScore() {
        int deadHeroes = state.heroes.size() - countAliveHeroes();
        int captiveBonus = (state.captiveState == GameConstants.CAPTIVE_HIDDEN)
            ? 200
            : (state.captiveState == GameConstants.CAPTIVE_DROPPED ? 100 : 0);
        int base = state.totalHeroWoundsInflicted * 35
            + deadHeroes * 350
            + (isBossAlive() ? 250 : 0)
            + captiveBonus
            + state.overlordReserve * 15;
        return areHeroesVictorious() ? Math.max(0, state.totalHeroWoundsInflicted * 15) : 1000 + base;
    }

    /**
     * Formats standard Initialization Input lines shared across Heroes, Overlord, and Multi roles.
     */
    public List<String> formatInitInputs(int role) {
        List<String> lines = new ArrayList<>();
        lines.add(String.format("%d %d %d %d %d %d %d %d %d",
            role, state.maxRounds, state.cols, state.rows,
            state.zones.size(), state.heroes.size(), state.allTiles.size(),
            state.recoverRate, state.reinforcePower));
        for (Zone z : state.zones) {
            lines.add(String.format("%d %d %d %d %d %d %d %d",
                z.id, z.gx, z.gy, z.type, z.nNorth, z.nEast, z.nSouth, z.nWest));
        }
        for (Hero h : state.heroes) {
            lines.add(String.format("%d %s %d %d %d %d %d %d %d",
                h.id, h.archetype, h.totalGems, h.freeMove,
                h.meleeTier, h.rangedTier, h.armor, h.exertMelee, h.exertRanged));
        }
        for (RiverTile t : state.allTiles) {
            lines.add(String.format("%d %s %d %d %d %d %d %d %d",
                t.id, t.unitType, t.maxModels, t.hp, t.armor,
                t.attackPower, t.attackRange, t.movePoints, t.reinforceCost));
        }
        return lines;
    }

    /**
     * Formats Turn Input lines with role-appropriate visibility for the hidden Captive hut.
     */
    public List<String> formatTurnInputs(int role) {
        List<String> lines = new ArrayList<>();
        int visibleCaptiveZone = (state.captiveState == GameConstants.CAPTIVE_HIDDEN && role == GameConstants.ROLE_HEROES)
            ? -1
            : (state.captiveState == GameConstants.CAPTIVE_HIDDEN ? state.secretCaptiveHutZone : state.captiveZone);
        lines.add(String.format("%d %d %d %d %d",
            state.round, state.captiveState, visibleCaptiveZone,
            state.overlordReserve, state.overlordFatigue));

        for (Hero h : state.heroes) {
            lines.add(String.format("%d %d %d %d %d %d %d %d",
                h.id, h.alive ? 1 : 0, h.zone, h.reserve, h.fatigue, h.wounds,
                h.stance, h.carryingCaptive ? 1 : 0));
        }

        // Send all River tiles in current slot order first, then any dredged tiles (slotCost = 0)
        int slot = 1;
        for (int tileId : state.riverOrder) {
            RiverTile t = getTileById(tileId);
            lines.add(String.format("%d %d %d %d %d",
                t.id, slot++, t.state, 0, countAliveModelsOnTile(t.id)));
        }
        for (RiverTile t : state.allTiles) {
            if (t.dredged) {
                lines.add(String.format("%d %d %d %d %d",
                    t.id, 0, t.state, 1, 0));
            }
        }

        List<Enemy> aliveEnemies = new ArrayList<>();
        for (Enemy e : state.enemies) {
            if (e.alive) aliveEnemies.add(e);
        }
        lines.add(String.valueOf(aliveEnemies.size()));
        for (Enemy e : aliveEnemies) {
            lines.add(String.format("%d %d %d %d %d",
                e.id, e.tileId, e.zone, e.hp, e.slumbering ? 1 : 0));
        }

        List<int[]> pois = new ArrayList<>();
        for (Zone z : state.zones) {
            if (z.type == GameConstants.ZONE_HUT) {
                pois.add(new int[]{z.id, GameConstants.POI_HUT, z.hutSearched ? 1 : 0});
            }
            if (z.hasChest) {
                pois.add(new int[]{z.id, GameConstants.POI_CHEST, 1});
            }
        }
        lines.add(String.valueOf(pois.size()));
        for (int[] p : pois) {
            lines.add(String.format("%d %d %d", p[0], p[1], p[2]));
        }
        return lines;
    }

    private static List<String> splitCommands(String commandLine) {
        List<String> out = new ArrayList<>();
        if (commandLine == null) {
            return out;
        }
        for (String raw : commandLine.split(";")) {
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }
}
