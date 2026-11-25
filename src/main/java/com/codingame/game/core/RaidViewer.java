package com.codingame.game.core;

import com.codingame.game.core.RaidModels.Enemy;
import com.codingame.game.core.RaidModels.EventFx;
import com.codingame.game.core.RaidModels.Hero;
import com.codingame.game.core.RaidModels.RaidState;
import com.codingame.game.core.RaidModels.RiverTile;
import com.codingame.game.core.RaidModels.Zone;
import com.codingame.gameengine.module.entities.Circle;
import com.codingame.gameengine.module.entities.Curve;
import com.codingame.gameengine.module.entities.GraphicEntityModule;
import com.codingame.gameengine.module.entities.Group;
import com.codingame.gameengine.module.entities.Polygon;
import com.codingame.gameengine.module.entities.Rectangle;
import com.codingame.gameengine.module.entities.Sprite;
import com.codingame.gameengine.module.entities.Text;
import com.codingame.gameengine.module.toggle.ToggleModule;
import com.codingame.gameengine.module.tooltip.TooltipModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared 1920x1080 PixiJS viewer driving Zone board rendering, RPG Sprite / Geometric Debug toggle,
 * Hero & Overlord gem dashboards, and the animated horizontal Runic River conveyor (< 190 entities).
 */
public final class RaidViewer {
    private static final int MAP_X = 415;
    private static final int MAP_Y = 102;
    private static final int MAP_W = 1090;
    private static final int MAP_H = 740;
    private static final int RIVER_Y = 865;

    private final GraphicEntityModule graphic;
    private final TooltipModule tooltips;
    private final ToggleModule toggle;
    private final RaidEngine engine;
    private final int viewerRole;

    private int cellW;
    private int cellH;
    private int entityCount = 0;

    private Text turnText;
    private Text objectiveText;
    private Text scoreText;

    // Left Hero Cards (up to 3 heroes)
    private final List<Rectangle> heroCards = new ArrayList<>();
    private final List<Text> heroTitles = new ArrayList<>();
    private final List<Text> heroStats = new ArrayList<>();

    // Right Overlord Panel
    private Text overlordStatsText;
    private Text overlordRiverNoteText;
    private Text battleLogText;

    // Map Zone & Prop graphics
    private final List<Group> zoneGroups = new ArrayList<>();
    private final List<Text> zoneLabels = new ArrayList<>();
    private final List<Rectangle> zoneDebugRects = new ArrayList<>();
    private final Map<Integer, Sprite> hutMarkerSprites = new HashMap<>();
    private final Map<Integer, Sprite> chestMarkerSprites = new HashMap<>();
    private Group captiveGroup;

    // Mobile unit tokens
    private final List<Group> heroGroups = new ArrayList<>();
    private final List<Text> heroBadgeTexts = new ArrayList<>();
    private final List<Group> enemyGroups = new ArrayList<>();
    private final List<Text> enemyBadgeTexts = new ArrayList<>();

    // Runic River cards indexed by tileId
    private final List<Group> riverTileGroups = new ArrayList<>();
    private final List<Rectangle> riverTileRects = new ArrayList<>();
    private final List<Text> riverTileTexts = new ArrayList<>();

    // Pooled animation FX
    private Sprite fxSlash;
    private Sprite fxBolt;
    private Sprite fxHeal;

    public RaidViewer(GraphicEntityModule graphic, TooltipModule tooltips, ToggleModule toggle,
                      RaidEngine engine, int viewerRole) {
        this.graphic = graphic;
        this.tooltips = tooltips;
        this.toggle = toggle;
        this.engine = engine;
        this.viewerRole = viewerRole;
    }

    public int getEntityCount() {
        return entityCount;
    }

    /**
     * Allocates all static and reusable dynamic PixiJS entities at init() within the <200 entity budget.
     */
    public void initView(String titleLabel) {
        RaidState state = engine.getState();
        cellW = Math.min(265, (MAP_W - 20) / state.cols);
        cellH = Math.min(240, (MAP_H - 20) / state.rows);

        createHeaderAndBackdrop(state, titleLabel);
        createHeroDashboard(state);
        createOverlordDashboard(state);
        createZoneGrid(state);
        createUnitTokens(state);
        createRiverBar(state);
        createFxSprites();

        refreshState(false, false);
    }

    private void createHeaderAndBackdrop(RaidState state, String titleLabel) {
        graphic.createSprite()
            .setImage("background.jpg")
            .setX(0).setY(0)
            .setBaseWidth(1920).setBaseHeight(1080)
            .setAlpha(0.32)
            .setZIndex(0);
        entityCount++;

        graphic.createRectangle()
            .setX(0).setY(0).setWidth(1920).setHeight(84)
            .setFillColor(0x0f172a).setFillAlpha(0.92)
            .setZIndex(2);
        entityCount++;

        graphic.createText(titleLabel)
            .setX(28).setY(42).setAnchorY(0.5)
            .setFontFamily("Lato").setFontSize(34).setFontWeight(Text.FontWeight.BOLD)
            .setFillColor(0xfbbf24)
            .setZIndex(4);
        entityCount++;

        turnText = graphic.createText("ROUND 1 / " + state.maxRounds)
            .setX(960).setY(42).setAnchor(0.5)
            .setFontFamily("Lato").setFontSize(38).setFontWeight(Text.FontWeight.BOLDER)
            .setFillColor(0xffffff).setStrokeColor(0x000000).setStrokeThickness(4)
            .setZIndex(5);
        entityCount++;

        objectiveText = graphic.createText("CAPTIVE: HIDDEN | BOSS: ALIVE")
            .setX(1235).setY(42).setAnchorY(0.5)
            .setFontFamily("Lato").setFontSize(26).setFontWeight(Text.FontWeight.BOLD)
            .setFillColor(0x93c5fd)
            .setZIndex(4);
        entityCount++;

        scoreText = graphic.createText("SCORE: 0")
            .setX(1892).setY(42).setAnchorX(1.0).setAnchorY(0.5)
            .setFontFamily("Lato").setFontSize(28).setFontWeight(Text.FontWeight.BOLD)
            .setFillColor(0x86efac)
            .setZIndex(4);
        entityCount++;
    }

    private void createHeroDashboard(RaidState state) {
        graphic.createRectangle()
            .setX(18).setY(MAP_Y).setWidth(378).setHeight(960)
            .setFillColor(0x0f172a).setFillAlpha(0.86)
            .setLineColor(0x3b82f6).setLineWidth(2)
            .setZIndex(2);
        entityCount++;

        graphic.createText("HERO SQUAD (GEMS)")
            .setX(207).setY(MAP_Y + 34).setAnchor(0.5)
            .setFontFamily("Lato").setFontSize(28).setFontWeight(Text.FontWeight.BOLD)
            .setFillColor(0x60a5fa)
            .setZIndex(4);
        entityCount++;

        int cardH = 260;
        for (int i = 0; i < state.heroes.size(); i++) {
            Hero h = state.heroes.get(i);
            int cy = MAP_Y + 72 + i * (cardH + 16);
            Rectangle card = graphic.createRectangle()
                .setX(34).setY(cy).setWidth(346).setHeight(cardH)
                .setFillColor(0x1e293b).setFillAlpha(0.92)
                .setLineColor(0x60a5fa).setLineWidth(2)
                .setZIndex(3);
            heroCards.add(card);
            entityCount++;

            Text title = graphic.createText("H" + h.id + " " + h.archetype + " [" + h.skill + "]")
                .setX(50).setY(cy + 26)
                .setFontFamily("Lato").setFontSize(25).setFontWeight(Text.FontWeight.BOLD)
                .setFillColor(0xf8fafc)
                .setZIndex(4);
            heroTitles.add(title);
            entityCount++;

            Text stats = graphic.createText("")
                .setX(50).setY(cy + 70)
                .setFontFamily("Lato").setFontSize(22)
                .setFillColor(0xcbd5e1)
                .setZIndex(4);
            heroStats.add(stats);
            entityCount++;
        }
    }

    private void createOverlordDashboard(RaidState state) {
        graphic.createRectangle()
            .setX(1524).setY(MAP_Y).setWidth(378).setHeight(960)
            .setFillColor(0x0f172a).setFillAlpha(0.86)
            .setLineColor(0xef4444).setLineWidth(2)
            .setZIndex(2);
        entityCount++;

        graphic.createText("OVERLORD BOARD")
            .setX(1713).setY(MAP_Y + 34).setAnchor(0.5)
            .setFontFamily("Lato").setFontSize(28).setFontWeight(Text.FontWeight.BOLD)
            .setFillColor(0xf87171)
            .setZIndex(4);
        entityCount++;

        overlordStatsText = graphic.createText("")
            .setX(1542).setY(MAP_Y + 74)
            .setFontFamily("Lato").setFontSize(23)
            .setFillColor(0xfecaca)
            .setZIndex(4);
        entityCount++;

        overlordRiverNoteText = graphic.createText("")
            .setX(1542).setY(MAP_Y + 335)
            .setFontFamily("Lato").setFontSize(21)
            .setFillColor(0xe2e8f0)
            .setZIndex(4);
        entityCount++;

        battleLogText = graphic.createText("Turn Log: Ready")
            .setX(1542).setY(MAP_Y + 610)
            .setFontFamily("Lato").setFontSize(20)
            .setFillColor(0xfde68a)
            .setZIndex(4);
        entityCount++;
    }

    private void createZoneGrid(RaidState state) {
        for (Zone z : state.zones) {
            int cx = zoneCenterX(state, z.gx);
            int cy = zoneCenterY(state, z.gy);

            String spriteImg = switch (z.type) {
                case GameConstants.ZONE_EXIT -> "stairs.png";
                case GameConstants.ZONE_HUT, GameConstants.ZONE_SANCTUM -> "floor2.png";
                default -> "floor1.png";
            };
            Sprite sp = graphic.createSprite()
                .setImage(spriteImg)
                .setAnchor(0.5)
                .setBaseWidth(cellW - 10).setBaseHeight(cellH - 10);
            toggle.displayOnToggleState(sp, "renderMode", true);
            entityCount++;

            int dbgColor = zoneBaseColor(z.type);
            Rectangle dbg = graphic.createRectangle()
                .setX(-(cellW - 10) / 2).setY(-(cellH - 10) / 2)
                .setWidth(cellW - 10).setHeight(cellH - 10)
                .setFillColor(dbgColor)
                .setLineColor(0x475569).setLineWidth(3);
            toggle.displayOnToggleState(dbg, "renderMode", false);
            zoneDebugRects.add(dbg);
            entityCount++;

            Text lbl = graphic.createText(buildZoneTag(z))
                .setX(-(cellW - 24) / 2).setY(-(cellH - 22) / 2)
                .setFontFamily("Lato").setFontSize(24).setFontWeight(Text.FontWeight.BOLD)
                .setFillColor(0xffffff).setStrokeColor(0x000000).setStrokeThickness(3);
            zoneLabels.add(lbl);
            entityCount++;

            Group zg = graphic.createGroup(sp, dbg, lbl)
                .setX(cx).setY(cy).setZIndex(2);
            zoneGroups.add(zg);
            entityCount++;

            if (z.type == GameConstants.ZONE_HUT) {
                Sprite torch = graphic.createSprite()
                    .setImage("torch.png")
                    .setAnchor(0.5)
                    .setBaseWidth(38).setBaseHeight(38)
                    .setX(cx + cellW / 4).setY(cy - cellH / 4)
                    .setZIndex(5);
                hutMarkerSprites.put(z.id, torch);
                entityCount++;
            }
            if (z.hasChest) {
                Sprite chest = graphic.createSprite()
                    .setImage("chest_closed.png")
                    .setAnchor(0.5)
                    .setBaseWidth(40).setBaseHeight(40)
                    .setX(cx - cellW / 4).setY(cy + cellH / 4)
                    .setZIndex(5);
                chestMarkerSprites.put(z.id, chest);
                entityCount++;
            }
        }

        // Draw thick palisade barriers between disconnected adjacent cells
        int gridLeft = MAP_X + (MAP_W - state.cols * cellW) / 2;
        int gridTop = MAP_Y + (MAP_H - state.rows * cellH) / 2;
        for (Zone z : state.zones) {
            if (z.gx + 1 < state.cols && z.nEast < 0) {
                graphic.createRectangle()
                    .setX(gridLeft + (z.gx + 1) * cellW - 5)
                    .setY(gridTop + z.gy * cellH + 4)
                    .setWidth(10).setHeight(cellH - 8)
                    .setFillColor(0x991b1b).setLineColor(0xfca5a5).setLineWidth(1)
                    .setZIndex(6);
                entityCount++;
            }
            if (z.gy + 1 < state.rows && z.nSouth < 0) {
                graphic.createRectangle()
                    .setX(gridLeft + z.gx * cellW + 4)
                    .setY(gridTop + (z.gy + 1) * cellH - 5)
                    .setWidth(cellW - 8).setHeight(10)
                    .setFillColor(0x991b1b).setLineColor(0xfca5a5).setLineWidth(1)
                    .setZIndex(6);
                entityCount++;
            }
        }

        // Captive marker
        Circle capCircle = graphic.createCircle()
            .setRadius(18).setFillColor(0xfacc15).setLineColor(0x000000).setLineWidth(3);
        Text capText = graphic.createText("C").setAnchor(0.5)
            .setFontFamily("Lato").setFontSize(22).setFontWeight(Text.FontWeight.BOLDER).setFillColor(0x000000);
        captiveGroup = graphic.createGroup(capCircle, capText).setZIndex(14).setVisible(false);
        entityCount += 3;
    }

    private void createUnitTokens(RaidState state) {
        for (Hero h : state.heroes) {
            Sprite sp = graphic.createSprite()
                .setImage(h.sprite)
                .setAnchor(0.5).setBaseWidth(58).setBaseHeight(58);
            toggle.displayOnToggleState(sp, "renderMode", true);

            Circle dbg = graphic.createCircle()
                .setRadius(25).setFillColor(0x2563eb).setLineColor(0xffffff).setLineWidth(3);
            toggle.displayOnToggleState(dbg, "renderMode", false);

            Text badge = graphic.createText("H" + h.id)
                .setX(0).setY(34).setAnchor(0.5)
                .setFontFamily("Lato").setFontSize(22).setFontWeight(Text.FontWeight.BOLDER)
                .setFillColor(0x93c5fd).setStrokeColor(0x000000).setStrokeThickness(3);
            heroBadgeTexts.add(badge);

            Group g = graphic.createGroup(sp, dbg, badge).setZIndex(12);
            heroGroups.add(g);
            entityCount += 4;
        }

        for (Enemy e : state.enemies) {
            Sprite sp = graphic.createSprite()
                .setImage(e.sprite)
                .setAnchor(0.5)
                .setBaseWidth("BOSS".equals(e.unitType) ? 68 : 50)
                .setBaseHeight("BOSS".equals(e.unitType) ? 68 : 50);
            toggle.displayOnToggleState(sp, "renderMode", true);

            int dColor = "BOSS".equals(e.unitType) ? 0x9333ea : 0xdc2626;
            Polygon dbg = graphic.createPolygon()
                .addPoint(0, -24).addPoint(24, 0).addPoint(0, 24).addPoint(-24, 0)
                .setFillColor(dColor).setLineColor(0xffffff).setLineWidth(2);
            toggle.displayOnToggleState(dbg, "renderMode", false);

            Text badge = graphic.createText("E" + e.id)
                .setX(0).setY(30).setAnchor(0.5)
                .setFontFamily("Lato").setFontSize(20).setFontWeight(Text.FontWeight.BOLDER)
                .setFillColor(0xfca5a5).setStrokeColor(0x000000).setStrokeThickness(3);
            enemyBadgeTexts.add(badge);

            Group g = graphic.createGroup(sp, dbg, badge).setZIndex(10);
            enemyGroups.add(g);
            entityCount += 4;
        }
    }

    private void createRiverBar(RaidState state) {
        graphic.createRectangle()
            .setX(MAP_X).setY(RIVER_Y).setWidth(MAP_W).setHeight(196)
            .setFillColor(0x0f172a).setFillAlpha(0.90)
            .setLineColor(0x475569).setLineWidth(2)
            .setZIndex(2);
        entityCount++;

        graphic.createText("RUNIC RIVER (ACTIVATION COST: SLOT 1 -> 8, MAX 2 TILES/TURN)")
            .setX(MAP_X + 18).setY(RIVER_Y + 22).setAnchorY(0.5)
            .setFontFamily("Lato").setFontSize(22).setFontWeight(Text.FontWeight.BOLD)
            .setFillColor(0xfbbf24)
            .setZIndex(4);
        entityCount++;

        int slotW = (MAP_W - 28) / state.allTiles.size();
        for (RiverTile tile : state.allTiles) {
            Rectangle rect = graphic.createRectangle()
                .setX(0).setY(0).setWidth(slotW - 10).setHeight(136)
                .setFillColor(0x1e293b).setLineColor(0x94a3b8).setLineWidth(2);
            riverTileRects.add(rect);

            Text label = graphic.createText(tile.unitType)
                .setX((slotW - 10) / 2).setY(68).setAnchor(0.5)
                .setFontFamily("Lato").setFontSize(20).setFontWeight(Text.FontWeight.BOLD)
                .setFillColor(0xffffff);
            riverTileTexts.add(label);

            Group tg = graphic.createGroup(rect, label)
                .setY(RIVER_Y + 46).setZIndex(6);
            riverTileGroups.add(tg);
            entityCount += 3;
        }
    }

    private void createFxSprites() {
        fxSlash = graphic.createSprite().setImage("fx_slash.png").setAnchor(0.5)
            .setBaseWidth(88).setBaseHeight(88).setVisible(false).setZIndex(18);
        fxBolt = graphic.createSprite().setImage("fx_fireball.png").setAnchor(0.5)
            .setBaseWidth(88).setBaseHeight(88).setVisible(false).setZIndex(18);
        fxHeal = graphic.createSprite().setImage("fx_heal.png").setAnchor(0.5)
            .setBaseWidth(88).setBaseHeight(88).setVisible(false).setZIndex(18);
        entityCount += 3;
    }

    /**
     * Synchronizes all entities with current RaidState and commits optional mid-turn frame.
     */
    public void refreshState(boolean midTurnCommit, boolean animateFx) {
        RaidState state = engine.getState();

        if (animateFx && !state.fxQueue.isEmpty()) {
            triggerFxStep(state);
        } else {
            fxSlash.setVisible(false);
            fxBolt.setVisible(false);
            fxHeal.setVisible(false);
        }

        turnText.setText("ROUND " + state.round + " / " + state.maxRounds);
        String capStr = switch (state.captiveState) {
            case GameConstants.CAPTIVE_HIDDEN -> "HIDDEN";
            case GameConstants.CAPTIVE_DROPPED -> "DROPPED@" + state.captiveZone;
            case GameConstants.CAPTIVE_CARRIED -> "CARRIED(H" + state.captiveZone + ")";
            default -> "ESCAPED";
        };
        objectiveText.setText("CAPTIVE: " + capStr + " | BOSS: " + (engine.isBossAlive() ? "ALIVE" : "SLAIN"));
        int liveScore = (viewerRole == GameConstants.ROLE_HEROES)
            ? engine.computeHeroesScore()
            : engine.computeOverlordScore();
        scoreText.setText("SCORE: " + liveScore);

        refreshHeroCards(state);
        refreshOverlordPanel(state);
        refreshZonesAndProps(state);
        refreshTokensOnMap(state);
        refreshRiverCards(state);

        if (midTurnCommit) {
            graphic.commitWorldState(0.5);
            fxSlash.setVisible(false);
            fxBolt.setVisible(false);
            fxHeal.setVisible(false);
        }
    }

    private void triggerFxStep(RaidState state) {
        RaidModels.EventFx slash = null;
        RaidModels.EventFx bolt = null;
        RaidModels.EventFx heal = null;
        for (EventFx fx : state.fxQueue) {
            if ("SLASH".equals(fx.kind)) slash = fx;
            if ("FIREBALL".equals(fx.kind)) bolt = fx;
            if ("HEAL".equals(fx.kind)) heal = fx;
        }
        placeFx(fxSlash, slash, state);
        placeFx(fxBolt, bolt, state);
        placeFx(fxHeal, heal, state);
    }

    private void placeFx(Sprite sp, EventFx fx, RaidState state) {
        if (fx == null) {
            sp.setVisible(false);
            return;
        }
        Zone to = state.zones.get(fx.toZone);
        sp.setX(zoneCenterX(state, to.gx)).setY(zoneCenterY(state, to.gy)).setVisible(true);
    }

    private void refreshHeroCards(RaidState state) {
        for (int i = 0; i < state.heroes.size(); i++) {
            Hero h = state.heroes.get(i);
            Rectangle card = heroCards.get(i);
            card.setFillColor(h.alive ? 0x1e293b : 0x2b1215)
                .setLineColor(h.carryingCaptive ? 0xfacc15 : (h.alive ? 0x60a5fa : 0xef4444));
            String stanceStr = h.alive ? (h.stance == GameConstants.STANCE_REST ? "REST" : "ACTIVE") : "DEAD";
            heroTitles.get(i).setText("H" + h.id + " " + h.archetype + " (" + stanceStr + ")");
            heroStats.get(i).setText(String.format(
                "Zone: %d | Skill: %s%nReserve: %d | Fatigue: %d%nWounds: %d / %d%nArmor: %d | FreeMove: %d%nExert (M/R): %d / %d",
                h.zone, h.skill, h.reserve, h.fatigue, h.wounds, h.totalGems,
                h.armor, h.freeMove, h.exertMelee, h.exertRanged));
        }
    }

    private void refreshOverlordPanel(RaidState state) {
        Enemy boss = engine.getEnemyById(state.bossEnemyId);
        int bossHp = (boss != null && boss.alive) ? boss.hp : 0;
        overlordStatsText.setText(String.format(
            "Reserve Gems: %d%nFatigue Gems: %d%nRecovery/Turn: +%d%nActivations: %d / 2%nReinforce Pts: %d%nBoss HP: %d / %d%nHero Wounds: %d",
            state.overlordReserve, state.overlordFatigue, state.recoverRate,
            state.activationsUsed, state.reinforcePointsLeft,
            bossHp, boss != null ? boss.maxHp : 0, state.totalHeroWoundsInflicted));

        StringBuilder riverSummary = new StringBuilder("River Order:\n");
        for (int idx = 0; idx < state.riverOrder.size(); idx++) {
            RiverTile t = engine.getTileById(state.riverOrder.get(idx));
            String mark = t.state == GameConstants.TILE_DEAD ? "DEAD"
                : (t.state == GameConstants.TILE_SLUMBERING ? "SLEEP" : "RDY");
            riverSummary.append(String.format("[%d] T%d %s (%s)\n", idx + 1, t.id, t.unitType, mark));
        }
        overlordRiverNoteText.setText(riverSummary.toString().trim());

        String note = viewerRole == GameConstants.ROLE_HEROES ? state.lastHeroMsg : state.lastOverlordMsg;
        if (note == null || note.isEmpty()) {
            note = "Role: " + (viewerRole == GameConstants.ROLE_HEROES ? "HEROES" : "OVERLORD");
        }
        battleLogText.setText(note.length() > 32 ? note.substring(0, 32) : note);
    }

    private void refreshZonesAndProps(RaidState state) {
        for (Zone z : state.zones) {
            zoneLabels.get(z.id).setText(buildZoneTag(z));
            tooltips.setTooltipText(zoneGroups.get(z.id),
                String.format("Zone %d (%d,%d)\nNeighbors: %s\nEnemies: %d | Heroes: %d",
                    z.id, z.gx, z.gy, z.neighbors,
                    engine.countActiveEnemiesInZone(z.id), engine.countAliveHeroesInZone(z.id)));
            Sprite hm = hutMarkerSprites.get(z.id);
            if (hm != null) {
                hm.setAlpha(z.hutSearched ? 0.20 : 1.0);
            }
            Sprite cm = chestMarkerSprites.get(z.id);
            if (cm != null) {
                cm.setImage(z.hasChest ? "chest_closed.png" : "chest_open.png");
            }
        }

        if (state.captiveState == GameConstants.CAPTIVE_DROPPED && state.captiveZone >= 0) {
            Zone z = state.zones.get(state.captiveZone);
            captiveGroup.setX(zoneCenterX(state, z.gx) - cellW / 4).setY(zoneCenterY(state, z.gy)).setVisible(true);
        } else if (state.captiveState == GameConstants.CAPTIVE_CARRIED && state.captiveZone >= 0) {
            Hero carrier = engine.getHeroById(state.captiveZone);
            if (carrier != null) {
                Zone z = state.zones.get(carrier.zone);
                captiveGroup.setX(zoneCenterX(state, z.gx) + 28).setY(zoneCenterY(state, z.gy) - 34).setVisible(true);
            }
        } else {
            captiveGroup.setVisible(false);
        }
    }

    private void refreshTokensOnMap(RaidState state) {
        Map<Integer, Integer> zoneSlotCounts = new HashMap<>();

        for (Hero h : state.heroes) {
            Group g = heroGroups.get(h.id);
            if (!h.alive) {
                g.setVisible(false);
                continue;
            }
            int slot = zoneSlotCounts.getOrDefault(h.zone, 0);
            zoneSlotCounts.put(h.zone, slot + 1);
            Zone z = state.zones.get(h.zone);
            int ox = -cellW / 4 + (slot % 3) * 44;
            int oy = -16;
            g.setX(zoneCenterX(state, z.gx) + ox, Curve.EASE_IN_AND_OUT)
                .setY(zoneCenterY(state, z.gy) + oy, Curve.EASE_IN_AND_OUT)
                .setVisible(true);
            heroBadgeTexts.get(h.id).setText("H" + h.id + "(" + h.reserve + "/" + h.remainingGems() + ")");
            tooltips.setTooltipText(g, String.format("Hero %d (%s) Zone %d\nReserve %d | Fatigue %d | Wounds %d",
                h.id, h.archetype, h.zone, h.reserve, h.fatigue, h.wounds));
        }

        Map<Integer, Integer> enemySlotCounts = new HashMap<>();
        for (Enemy e : state.enemies) {
            Group g = enemyGroups.get(e.id);
            if (!e.alive) {
                g.setVisible(false);
                continue;
            }
            int slot = enemySlotCounts.getOrDefault(e.zone, 0);
            enemySlotCounts.put(e.zone, slot + 1);
            Zone z = state.zones.get(e.zone);
            int ox = -cellW / 4 + (slot % 3) * 44;
            int oy = 24 + (slot / 3) * 28;
            g.setX(zoneCenterX(state, z.gx) + ox, Curve.EASE_IN_AND_OUT)
                .setY(zoneCenterY(state, z.gy) + oy, Curve.EASE_IN_AND_OUT)
                .setAlpha(e.slumbering ? 0.45 : 1.0)
                .setVisible(true);
            String shortTag = "BOSS".equals(e.unitType) ? "BOSS" : e.unitType.substring(0, 2);
            enemyBadgeTexts.get(e.id).setText(shortTag + e.id + "(" + e.hp + ")");
            tooltips.setTooltipText(g, String.format("Enemy %d (%s, Tile %d)\nZone %d | HP %d/%d | Armor %d | Atk %d (Rng %d)",
                e.id, e.unitType, e.tileId, e.zone, e.hp, e.maxHp, e.armor, e.attackPower, e.attackRange));
        }
    }

    private void refreshRiverCards(RaidState state) {
        int slotW = (MAP_W - 28) / state.allTiles.size();
        for (int idx = 0; idx < state.riverOrder.size(); idx++) {
            int tileId = state.riverOrder.get(idx);
            RiverTile tile = engine.getTileById(tileId);
            int x = MAP_X + 18 + idx * slotW;

            riverTileGroups.get(tileId)
                .setX(x, Curve.EASE_IN_AND_OUT)
                .setVisible(true);

            int fillColor = tile.state == GameConstants.TILE_DEAD ? 0x334155
                : (tile.state == GameConstants.TILE_SLUMBERING ? 0x312e81 : 0x1e293b);
            int borderColor = "BOSS".equals(tile.unitType) ? 0xc084fc
                : ("EVENT".equals(tile.unitType) ? 0xfbbf24 : 0xef4444);
            riverTileRects.get(tileId).setFillColor(fillColor).setLineColor(borderColor);

            String aliveStr = tile.maxModels > 0
                ? (engine.countAliveModelsOnTile(tileId) + "/" + tile.maxModels)
                : ("+" + state.reinforcePower + " RNF");
            String stateTag = tile.state == GameConstants.TILE_DEAD ? "DEAD (DREDGE 2)"
                : (tile.state == GameConstants.TILE_SLUMBERING ? "SLEEP" : "COST " + (idx + 1));
            riverTileTexts.get(tileId).setText(String.format("T%d %s\n%s\n%s",
                tileId, tile.unitType, stateTag, aliveStr));
            tooltips.setTooltipText(riverTileGroups.get(tileId),
                String.format("River Tile %d (%s)\nSlot Cost: %d | Reinforce Cost: %d | State: %s",
                    tileId, tile.unitType, idx + 1, tile.reinforceCost, stateTag));
        }
        for (RiverTile tile : state.allTiles) {
            if (tile.dredged) {
                riverTileGroups.get(tile.id).setVisible(false);
            }
        }
    }

    private int zoneCenterX(RaidState state, int gx) {
        int left = MAP_X + (MAP_W - state.cols * cellW) / 2;
        return left + gx * cellW + cellW / 2;
    }

    private int zoneCenterY(RaidState state, int gy) {
        int top = MAP_Y + (MAP_H - state.rows * cellH) / 2;
        return top + gy * cellH + cellH / 2;
    }

    private static int zoneBaseColor(int type) {
        return switch (type) {
            case GameConstants.ZONE_EXIT -> 0x1e3a8a;
            case GameConstants.ZONE_SANCTUM -> 0x581c87;
            case GameConstants.ZONE_HUT -> 0x78350f;
            case GameConstants.ZONE_REINFORCEMENT -> 0x7f1d1d;
            default -> 0x1f2937;
        };
    }

    private static String buildZoneTag(Zone z) {
        return switch (z.type) {
            case GameConstants.ZONE_EXIT -> "z" + z.id + " EXIT";
            case GameConstants.ZONE_SANCTUM -> "z" + z.id + " SANCTUM";
            case GameConstants.ZONE_HUT -> "z" + z.id + (z.hutSearched ? " HUT(ok)" : " HUT?");
            case GameConstants.ZONE_REINFORCEMENT -> "z" + z.id + " REINF";
            default -> "z" + z.id;
        };
    }
}
