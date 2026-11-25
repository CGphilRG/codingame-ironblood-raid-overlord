package com.codingame.game.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable domain state structures for zones, Heroes, the Overlord's Runic River,
 * enemy models, visual FX cues, and overall RaidState.
 */
public final class RaidModels {
    private RaidModels() {
    }

    /**
     * One tactical zone on the planar village grid.
     */
    public static final class Zone {
        public final int id;
        public final int gx;
        public final int gy;
        public int type;
        public int nNorth = -1;
        public int nEast = -1;
        public int nSouth = -1;
        public int nWest = -1;
        public final List<Integer> neighbors = new ArrayList<>();
        public boolean hutSearched = false;
        public boolean hasChest = false;

        public Zone(int id, int gx, int gy, int type) {
            this.id = id;
            this.gx = gx;
            this.gy = gy;
            this.type = type;
        }
    }

    /**
     * Playable Hero entity driven by a single shared stamina/wound gem pool.
     */
    public static final class Hero {
        public final int id;
        public final String archetype;
        public int zone;
        public final int totalGems;
        public int reserve;
        public int fatigue;
        public int wounds;
        public final int freeMove;
        public final int meleeTier;
        public final int rangedTier;
        public final int armor;
        public final int exertMove;
        public final int exertMelee;
        public final int exertRanged;
        public final int exertManip;
        public final String skill;
        public final String sprite;

        public boolean alive = true;
        public int stance = GameConstants.STANCE_ACTIVE;
        public boolean carryingCaptive = false;

        // Per-turn expenditure trackers
        public int freeMoveLeft = 0;
        public int moveGemsSpent = 0;
        public int meleeGemsSpent = 0;
        public int rangedGemsSpent = 0;
        public int manipGemsSpent = 0;

        public Hero(int id, GameConstants.HeroTemplate tpl, int startZone, int initialFatigue) {
            this.id = id;
            this.archetype = tpl.archetype;
            this.zone = startZone;
            this.totalGems = tpl.totalGems;
            int clampedFatigue = Math.max(0, Math.min(initialFatigue, tpl.totalGems - 2));
            this.fatigue = clampedFatigue;
            this.reserve = tpl.totalGems - clampedFatigue;
            this.wounds = 0;
            this.freeMove = tpl.freeMove;
            this.meleeTier = tpl.meleeTier;
            this.rangedTier = tpl.rangedTier;
            this.armor = tpl.armor;
            this.exertMove = tpl.exertMove;
            this.exertMelee = tpl.exertMelee;
            this.exertRanged = tpl.exertRanged;
            this.exertManip = tpl.exertManip;
            this.skill = tpl.skill;
            this.sprite = tpl.sprite;
        }

        public void resetTurnTrackers() {
            this.freeMoveLeft = 0;
            this.moveGemsSpent = 0;
            this.meleeGemsSpent = 0;
            this.rangedGemsSpent = 0;
            this.manipGemsSpent = 0;
        }

        public int remainingGems() {
            return reserve + fatigue;
        }
    }

    /**
     * One tile placed in the Overlord's Runic River conveyor.
     */
    public static final class RiverTile {
        public final int id;
        public final String unitType;
        public final int maxModels;
        public final int hp;
        public final int armor;
        public final int attackPower;
        public final int attackRange;
        public final int movePoints;
        public final int reinforceCost;
        public final String sprite;

        public int state;
        public boolean dredged = false;
        public int spawnZoneHint = 0;

        public RiverTile(int id, GameConstants.UnitTemplate tpl, int initialState) {
            this.id = id;
            this.unitType = tpl.unitType;
            this.maxModels = tpl.maxModels;
            this.hp = tpl.hp;
            this.armor = tpl.armor;
            this.attackPower = tpl.attackPower;
            this.attackRange = tpl.attackRange;
            this.movePoints = tpl.movePoints;
            this.reinforceCost = tpl.reinforceCost;
            this.sprite = tpl.sprite;
            this.state = initialState;
        }
    }

    /**
     * An individual Overlord miniature bound to a RiverTile.
     */
    public static final class Enemy {
        public final int id;
        public final int tileId;
        public final String unitType;
        public int zone;
        public int hp;
        public final int maxHp;
        public final int armor;
        public final int attackPower;
        public final int attackRange;
        public final int movePoints;
        public final String sprite;

        public boolean alive = true;
        public boolean slumbering;
        public int movesLeft = 0;
        public boolean attackedThisTurn = false;

        public Enemy(int id, RiverTile tile, int zone, boolean slumbering) {
            this.id = id;
            this.tileId = tile.id;
            this.unitType = tile.unitType;
            this.zone = zone;
            this.hp = tile.hp;
            this.maxHp = tile.hp;
            this.armor = tile.armor;
            this.attackPower = tile.attackPower;
            this.attackRange = tile.attackRange;
            this.movePoints = tile.movePoints;
            this.sprite = tile.sprite;
            this.slumbering = slumbering;
        }
    }

    /**
     * Visual effect descriptor consumed by RaidViewer during mid-turn/end-turn animations.
     */
    public static final class EventFx {
        public final String kind; // SLASH, FIREBALL, HEAL
        public final int fromZone;
        public final int toZone;

        public EventFx(String kind, int fromZone, int toZone) {
            this.kind = kind;
            this.fromZone = fromZone;
            this.toZone = toZone;
        }
    }

    /**
     * Complete mutable state of one scenario run.
     */
    public static final class RaidState {
        public long seed;
        public int tier;
        public int maxRounds;
        public int round = 1;

        public int cols;
        public int rows;
        public final List<Zone> zones = new ArrayList<>();
        public final List<Hero> heroes = new ArrayList<>();
        public final List<RiverTile> allTiles = new ArrayList<>();
        public final List<Integer> riverOrder = new ArrayList<>();
        public final List<Enemy> enemies = new ArrayList<>();

        public int exitZone = 0;
        public int sanctumZone = 0;
        public int bossTileId = -1;
        public int bossEnemyId = -1;
        public int secretCaptiveHutZone = -1;
        public int captiveState = GameConstants.CAPTIVE_HIDDEN;
        public int captiveZone = -1; // zone when DROPPED, heroId when CARRIED

        public int overlordTotalGems;
        public int overlordReserve;
        public int overlordFatigue;
        public int recoverRate;
        public int reinforcePower;
        public int activationsUsed = 0;
        public int reinforcePointsLeft = 0;

        public int totalHeroWoundsInflicted = 0;
        public int enemiesKilled = 0;
        public int treasuresCollected = 0;

        public String lastHeroMsg = "";
        public String lastOverlordMsg = "";
        public final List<EventFx> fxQueue = new ArrayList<>();
    }
}
