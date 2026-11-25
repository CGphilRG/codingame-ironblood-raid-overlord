package com.codingame.game.core;

/**
 * Shared immutable rule constants, archetypes, troop profiles, and deterministic combat math
 * for Ironblood Chronicles: The Captive's Raid.
 */
public final class GameConstants {
    private GameConstants() {
    }

    public static final int ROLE_HEROES = 0;
    public static final int ROLE_OVERLORD = 1;

    // Zone types
    public static final int ZONE_CLEARING = 0;
    public static final int ZONE_HUT = 1;
    public static final int ZONE_SANCTUM = 2;
    public static final int ZONE_EXIT = 3;
    public static final int ZONE_REINFORCEMENT = 4;

    // Captive states
    public static final int CAPTIVE_HIDDEN = 0;
    public static final int CAPTIVE_DROPPED = 1;
    public static final int CAPTIVE_CARRIED = 2;
    public static final int CAPTIVE_ESCAPED = 3;

    // Hero stances
    public static final int STANCE_ACTIVE = 0;
    public static final int STANCE_REST = 1;

    // River tile states
    public static final int TILE_READY = 0;
    public static final int TILE_SLUMBERING = 1;
    public static final int TILE_DEAD = 2;

    // Point-of-interest types sent in turn inputs
    public static final int POI_HUT = 0;
    public static final int POI_CHEST = 1;

    // Deterministic combat die tiers
    public static final int TIER_NONE = 0;
    public static final int TIER_YELLOW = 1;
    public static final int TIER_ORANGE = 2;
    public static final int TIER_RED = 3;

    // Shared economy and exertion limits
    public static final int EXERT_MOVE = 2;
    public static final int EXERT_MANIP = 2;
    public static final int MAX_ACTIVATIONS_PER_TURN = 2;
    public static final int DREDGE_COST = 2;
    public static final int HERO_ACTIVE_RECOVER = 2;
    public static final int HERO_REST_RECOVER = 5;

    /**
     * Computes deterministic combat hits for the given weapon tier and committed gems.
     */
    public static int calcHits(int tier, int gems) {
        if (gems <= 0) {
            return 0;
        }
        return switch (tier) {
            case TIER_YELLOW -> gems + (gems >= 3 ? 1 : 0);
            case TIER_ORANGE -> gems * 2;
            case TIER_RED -> gems * 3;
            default -> 0;
        };
    }

    /**
     * Static template defining one playable Hero archetype.
     */
    public static final class HeroTemplate {
        public final String archetype;
        public final int totalGems;
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

        private HeroTemplate(String archetype, int totalGems, int freeMove, int meleeTier,
                             int rangedTier, int armor, int exertMove, int exertMelee,
                             int exertRanged, int exertManip, String skill, String sprite) {
            this.archetype = archetype;
            this.totalGems = totalGems;
            this.freeMove = freeMove;
            this.meleeTier = meleeTier;
            this.rangedTier = rangedTier;
            this.armor = armor;
            this.exertMove = exertMove;
            this.exertMelee = exertMelee;
            this.exertRanged = exertRanged;
            this.exertManip = exertManip;
            this.skill = skill;
            this.sprite = sprite;
        }
    }

    public static final HeroTemplate HERO_BARBARIAN = new HeroTemplate(
        "BARBARIAN", 11, 2, TIER_RED, TIER_NONE, 1, EXERT_MOVE, 4, 0, EXERT_MANIP, "CLEAVE", "hero_fighter.png");
    public static final HeroTemplate HERO_SCOUT = new HeroTemplate(
        "SCOUT", 10, 3, TIER_ORANGE, TIER_YELLOW, 0, EXERT_MOVE, 3, 2, EXERT_MANIP, "EVASIVE", "hero_cleric.png");
    public static final HeroTemplate HERO_MYSTIC = new HeroTemplate(
        "MYSTIC", 10, 2, TIER_YELLOW, TIER_RED, 0, EXERT_MOVE, 2, 4, EXERT_MANIP, "WARD", "hero_mage.png");
    public static final HeroTemplate HERO_HUNTRESS = new HeroTemplate(
        "HUNTRESS", 10, 2, TIER_ORANGE, TIER_ORANGE, 1, EXERT_MOVE, 3, 3, EXERT_MANIP, "GUARD", "hero_cleric.png");

    /**
     * Static template defining one Overlord unit tile or event tile in the Runic River.
     */
    public static final class UnitTemplate {
        public final String unitType;
        public final int maxModels;
        public final int hp;
        public final int armor;
        public final int attackPower;
        public final int attackRange;
        public final int movePoints;
        public final int reinforceCost;
        public final String sprite;

        private UnitTemplate(String unitType, int maxModels, int hp, int armor,
                             int attackPower, int attackRange, int movePoints,
                             int reinforceCost, String sprite) {
            this.unitType = unitType;
            this.maxModels = maxModels;
            this.hp = hp;
            this.armor = armor;
            this.attackPower = attackPower;
            this.attackRange = attackRange;
            this.movePoints = movePoints;
            this.reinforceCost = reinforceCost;
            this.sprite = sprite;
        }
    }

    public static final UnitTemplate UNIT_GUARD = new UnitTemplate(
        "GUARD", 2, 2, 1, 3, 1, 2, 2, "monster_skeleton.png");
    public static final UnitTemplate UNIT_ARCHER = new UnitTemplate(
        "ARCHER", 2, 1, 0, 3, 2, 2, 2, "minion_wraith.png");
    public static final UnitTemplate UNIT_BEAST = new UnitTemplate(
        "BEAST", 2, 2, 0, 2, 1, 3, 2, "minion_hound.png");
    public static final UnitTemplate UNIT_BRUTE = new UnitTemplate(
        "BRUTE", 1, 5, 2, 4, 1, 2, 3, "monster_skeleton.png");
    public static final UnitTemplate UNIT_BOSS = new UnitTemplate(
        "BOSS", 1, 10, 2, 4, 2, 2, 99, "minion_wraith.png");
    public static final UnitTemplate UNIT_EVENT = new UnitTemplate(
        "EVENT", 0, 0, 0, 0, 0, 0, 99, "torch.png");
}
