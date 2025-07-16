package com.articreep.fillinthewall.game;

import com.articreep.fillinthewall.Database;
import com.articreep.fillinthewall.FillInTheWall;
import com.articreep.fillinthewall.infodisplay.ScoreboardEntry;
import com.articreep.fillinthewall.infodisplay.ScoreboardEntryType;
import com.articreep.fillinthewall.gamemode.Gamemode;
import com.articreep.fillinthewall.gamemode.GamemodeAttribute;
import com.articreep.fillinthewall.gamemode.GamemodeSettings;
import com.articreep.fillinthewall.playerinfo.PlayerLevels;
import com.articreep.fillinthewall.menu.EndScreen;
import com.articreep.fillinthewall.modifiers.*;
import com.articreep.fillinthewall.multiplayer.MultiplayerGame;
import com.articreep.fillinthewall.multiplayer.ScoreAttackGame;
import com.articreep.fillinthewall.utils.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;
import org.javatuples.Pair;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class PlayingFieldScorer {
    PlayingField field;
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();
    private int score = 0;
    private int perfectWallsCleared = 0;
    private int perfectWallChain = 0;
    private boolean hasUsedCharge = false;
    private double blocksPlaced = 0;
    // time in ticks (this is displayed on the text display)
    private int time = 0;
    /** for calculating blocks per second */
    private int absoluteTimeElapsed = 0;
    private Gamemode gamemode = Gamemode.ENDLESS;
    private GamemodeSettings settings = Gamemode.ENDLESS.getDefaultSettings();
    private int eventCount = 0;
    private int playersOnGameStart = 0;
    // todo maybe make this an actual setting
    public boolean penalizeEmptyField = true;

    // Levels (if enabled)
    // this is only for score attack/marathon
    boolean doLevels = false;
    private int level = 1;
    private int levelProgressMax = 10;
    private double levelProgress = 0;
    private int wallTimeDecreaseAmount = 20;

    private int chargesAvailable = 0;

    // todo garbage clearing power "storage", subject to change
    private int garbagePoints = 0;

    // Multiplayer variables
    private MultiplayerGame multiplayerGame = null;
    private Scoreboard scoreboard = null;
    private Objective objective = null;
    private final List<ScoreboardEntry> scoreboardEntries = new ArrayList<>();
    
    // todo add the ability to neutralize garbage
    private final Deque<Wall> garbageQueue = new ArrayDeque<>();
    private PlayingField opponent;
    /**
     * True = attacking mode, walls cleared will send garbage walls to opponent
     * False = defensive mode, walls cleared will unharden garbage walls on our board
     * This only applies when the gamemode attribute DO_CLEARING_MODES is active
     */
    private boolean clearingMode = true;

    private boolean incompleteGame = false;

    private EndlessRun endlessRun = null;

    // Stats
    private final Map<UUID, Long> playerStartTimes = new HashMap<>();
    /** Stores the number of perfect walls cleared when each player first joined.
     * This will be 0 for people who were here since the beginning, and could be higher for others.
     */
    private final Map<UUID, Integer> perfectWallsOnJoin = new HashMap<>();

    public PlayingFieldScorer(PlayingField field) {
        this.field = field;
    }

    public enum BonusType {
        PERFECT, FIRE, STRIPE, PLAYER, CHAIN
    }

    public Judgement scoreWall(Wall wall, PlayingField field) {
        ModifierEvent event = null;
        if (field.eventActive()) event = field.getEvent();

        int score;
        double percent;
        HashMap<BonusType, Integer> bonusMap;

        if (event != null && event.overrideScoreCalculation) {
            score = field.getEvent().calculateScore(wall);
        } else score = calculateScore(wall, field);

        if (event != null && event.overridePercentCalculation) {
            percent = field.getEvent().calculatePercent(wall);
        } else percent = calculatePercent(wall, score);

        if (event != null && event.overrideBonusCalculation) {
            bonusMap = field.getEvent().evaluateBonus(percent, wall);
        } else bonusMap = evaluateBonus(percent);

        int totalBonus = sumBonus(bonusMap);
        this.score += score + totalBonus;

        Judgement judgement = Judgement.MISS;

        // Determine judgement
        for (Judgement j : Judgement.values()) {
            if (percent >= j.getPercent()) {
                judgement = j;
                break;
            }
        }

        if (event != null && event.overrideScoreTitle) event.displayScoreTitle(judgement, score, bonusMap);
        else displayScoreTitle(judgement, score, bonusMap);
        playJudgementSound(judgement);

        // Add/subtract to bonus
        if ((!field.eventActive() || field.getEvent().allowMeterAccumulation) && clearingMode) {
            awardLevelPoints(percent);
        }

        // Start Rush on Rush score attack if perfect
        if (gamemode == Gamemode.RUSH_SCORE_ATTACK && (!field.eventActive()) && judgement == Judgement.PERFECT) {
            activateEvent(ModifierEvent.Type.RUSH);
        }

        // Garbage wall rules
        if (settings.getBooleanAttribute(GamemodeAttribute.DO_GARBAGE_WALLS)) {
            if (percent >= Judgement.COOL.getPercent()) {
                // Clearing modes
                if (opponent != null && settings.getBooleanAttribute(GamemodeAttribute.DO_GARBAGE_ATTACK)) {
                    attackOrDefend(wall, judgement);
                } else {
                    // If clearing modes aren't enabled, just award garbage points regardless
                    awardGarbagePoints(judgement);
                }
            } else {
                // miss
                Wall garbageWall = createMissGarbageWall(wall);
                field.getQueue().hardenWall(garbageWall,
                        (int) settings.getAttribute(GamemodeAttribute.GARBAGE_WALL_HARDNESS));
            }
        } else if (field.getQueue().countHardenedWalls() > 0) {
            awardGarbagePoints(judgement);
        }

        // Custom walls tip display
//        if (gamemode == Gamemode.CUSTOM && !hasImportedCustomWalls) {
//            field.setTipDisplay(miniMessage.deserialize("<yellow>You can import custom walls with /fitw custom <name>"));
//        }

        // Check if we are at endless score threshold
        if (endlessRun != null && this.score >= endlessRun.scoreToNextLevel) {
            field.playSoundToPlayers(Sound.ITEM_TRIDENT_THROW, 1, 1);
            Bukkit.getScheduler().runTaskLater(FillInTheWall.getInstance(),
                    () -> endlessRun.nextPhase(), 10);
        }

        return judgement;
    }

    public HashMap<BonusType, Integer> evaluateBonus(double percent) {
        HashMap<BonusType, Integer> bonusMap = new HashMap<>();
        if (percent >= 1) {
            perfectWallsCleared++;
            perfectWallChain++;
            bonusMap.put(BonusType.PERFECT, 1);
        } else {
            perfectWallChain = 0;
            bonusMap.put(BonusType.PERFECT, 0);
        }
        return bonusMap;
    }

    private static int sumBonus(HashMap<BonusType, Integer> map) {
        int bonus = 0;
        for (Integer i : map.values()) {
            bonus += i;
        }
        return bonus;
    }

    /**
     * Takes a % accuracy on the scored wall and awards level points based on that.
     * @param percent Percent score of the last wall
     */
    private void awardLevelPoints(double percent) {
        if (percent >= Judgement.COOL.getPercent()) {
            levelProgress += percent;
            if (levelProgress > levelProgressMax) {
                levelProgress = levelProgressMax;
            }
        }

        // Activate meter/level up
        if (doLevels && levelProgress >= levelProgressMax) {
            setLevel(level + 1);
            field.flashLevel(80);
        }
    }

    private void levelUpSound() {
        new BukkitRunnable() {
            final float CSHARP = (float) Math.pow(2, -5f/12);
            final float D = (float) Math.pow(2, -4f/12);
            final float DSHARP = (float) Math.pow(2, -3f/12);
            int i = 0;
            @Override
            public void run() {
                float pitch;
                if (i == 0) pitch = CSHARP;
                else if (i == 1) pitch = D;
                else {
                    pitch = DSHARP;
                    cancel();
                }
                for (Player player : field.getPlayers()) {
                    player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, pitch);
                }
                i++;
            }
        }.runTaskTimer(FillInTheWall.getInstance(), 0, 2);
    }

    private final static float E1 = (float) Math.pow(2, (float) -2/12);
    private final static float C1 = (float) Math.pow(2, (float) -6/12);

    private final static float B1 = (float) Math.pow(2, (float) -7/12);
    private final static float D1 = (float) Math.pow(2, (float) -4/12);
    private final static float F1 = (float) Math.pow(2, (float) -1/12);
    private final static float G2 = (float) Math.pow(2, (float) 1/12);
    private final static float A2 = (float) Math.pow(2, (float) 3/12);

    // Taken from advancement bingo on FACT MC which I contributed to
    public void playGameEnd() {
        new BukkitRunnable() {
            int i = 0;
            @Override
            public void run() {
                if (i >= 8) {
                    cancel();
                } else if (i == 0 || i == 1 || i == 3 || i == 7) {
                    field.playSoundToPlayers(Sound.BLOCK_NOTE_BLOCK_PLING, 1, C1);
                    field.playSoundToPlayers(Sound.BLOCK_NOTE_BLOCK_PLING, 1, E1);
                    field.playSoundToPlayers(Sound.BLOCK_NOTE_BLOCK_PLING, 1, G2);
                } else if (i == 2) {
                    field.playSoundToPlayers(Sound.BLOCK_NOTE_BLOCK_PLING, 1, D1);
                    field.playSoundToPlayers(Sound.BLOCK_NOTE_BLOCK_PLING, 1, F1);
                    field.playSoundToPlayers(Sound.BLOCK_NOTE_BLOCK_PLING, 1, A2);
                } else if (i == 5) {
                    field.playSoundToPlayers(Sound.BLOCK_NOTE_BLOCK_PLING, 1, B1);
                    field.playSoundToPlayers(Sound.BLOCK_NOTE_BLOCK_PLING, 1, D1);
                    field.playSoundToPlayers(Sound.BLOCK_NOTE_BLOCK_PLING, 1, F1);
                }
                i++;
            }
        }.runTaskTimer(FillInTheWall.getInstance(), 0, 3);
    }

    // Unused
    private void attackOrDefend(Wall wall, Judgement judgement) {
        if (clearingMode) {
            // attack
            opponent.getScorer().addGarbageToQueue(createAttackGarbageWall(wall));
        } else {
            // defend
            // todo should change back to attack mode if there are no longer any garbage walls
            awardGarbagePoints(judgement);
            // if wall was a garbage wall, attack
            if (wall.wasHardened()) opponent.getScorer().addGarbageToQueue(createAttackGarbageWall(wall));
            // decrement meter
            levelProgress -= 1;
            if (levelProgress <= 0) {
                clearingMode = true;
                field.sendMessageToPlayers("Meter empty! Switched to attack mode!");
                levelProgress = 0;
            }
        }
    }

    private void awardGarbagePoints(Judgement judgement) {
        if (field.getQueue().countHardenedWalls() > 0) {
            if (judgement == Judgement.COOL) {
                garbagePoints += 1;
            } else if (judgement == Judgement.PERFECT) {
                garbagePoints += 2;
            }

            // If we have enough garbage points, crack a hardened wall
            if (garbagePoints >= (int) settings.getAttribute(GamemodeAttribute.GARBAGE_WALL_HARDNESS)) {
                field.getQueue().crackHardenedWall(garbagePoints);
                garbagePoints = 0;
            }
        }
    }

    private Wall createMissGarbageWall(Wall wall) {
        Wall copy = wall.copy();
        Set<Pair<Integer, Integer>> correctBlocks = wall.getCorrectBlocks(field).keySet();

        for (Pair<Integer, Integer> hole : correctBlocks) {
            copy.removeHole(hole);
        }

        // If all holes are filled in and it's still a miss, randomly insert holes from the original wall
        // todo subject to change
        if (copy.getHoles().isEmpty()) {
            Iterator<Pair<Integer, Integer>> iterator = correctBlocks.iterator();
            for (int i = 0; i < wall.getExtraBlocks(field).size(); i++) {
                if (iterator.hasNext()) {
                    Pair<Integer, Integer> correctHole = iterator.next();
                    copy.insertHole(correctHole);
                }
            }
        }
        return copy;
    }

    private Wall createAttackGarbageWall(Wall wall) {
        if (wall.getExtraBlocks(field).size() + wall.getMissingBlocks(field).size() == 0) {
            return wall.copy();
        } else {
            return createMissGarbageWall(wall);
        }
    }

    public void onClearingModeChange(Player player) {
        if (!settings.getBooleanAttribute(GamemodeAttribute.DO_CLEARING_MODES)) return;

        // Meter has to be at least 25% full to switch to defense
        double percent = levelProgress / levelProgressMax;

        if (!clearingMode) {
            clearingMode = true;
            player.sendMessage(miniMessage.deserialize("<green>Switched to attack mode!"));
        } else {
            if (percent < 0.25) {
                player.sendMessage(miniMessage.deserialize("<red>Your meter isn't full enough!"));
            } else {
                clearingMode = false;
                player.sendMessage(miniMessage.deserialize("<green>Switched to defense mode!"));

            }
        }
    }

    public void onChargeActivate(Player player) {
        if (field.getEvent() instanceof Tutorial tutorial) {
            tutorial.onChargeActivate(player);
            return;
        }

        if (settings.getModifierEventTypeAttribute(GamemodeAttribute.CHARGE_EVENT).createEvent() == null) {
            player.sendMessage(miniMessage.deserialize("<red>No event to activate!"));
            return;
        }

        ModifierEvent event = field.getEvent();
        if (event != null && event.isChargeEvent) return;

        if (chargesAvailable > 0) {
            ModifierEvent newEvent = activateEvent(settings.getModifierEventTypeAttribute(GamemodeAttribute.CHARGE_EVENT));
            newEvent.allowMeterAccumulation = false;
            newEvent.isChargeEvent = true;

            // todo lol hardcoded
            if (newEvent instanceof Freeze) newEvent.setTicksRemaining(20 * 10);

            hasUsedCharge = true;
            chargesAvailable--;
        } else {
            player.sendMessage(miniMessage.deserialize("<red>You're out of charges!"));
        }
    }

    /**
     * Attempts to activate the event associated with the current gamemode.
     */
    public ModifierEvent activateEvent(ModifierEvent.Type type) {
        if (type == null || type == ModifierEvent.Type.NONE) {
            return null;
        }
        ModifierEvent event = type.createEvent();
        return activateEvent(event);
    }

    public ModifierEvent activateEvent(ModifierEvent event) {
        Bukkit.getScheduler().runTask(FillInTheWall.getInstance(), () -> {
            event.setPlayingField(field);
            event.activate();
            eventCount++;
        });
        return event;
    }

    public void displayScoreTitle(Judgement judgement, int score, Map<BonusType, Integer> bonusMap) {
        Title title = Title.title(judgement.getFormattedText(),
                Component.text(score + bonusMap.get(BonusType.PERFECT) + " points", judgement.getColor()),
                getScoreTitleTimes());
        field.sendTitleToPlayers(title);
    }

    public static Title.Times getScoreTitleTimes() {
        return Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ofMillis(250));
    }

    public void playJudgementSound(Judgement judgement) {
        for (Player player : field.getPlayers()) {
            player.playSound(player.getLocation(), judgement.getSound(), 0.7f, 1);
        }
    }

    public int calculateScore(Wall wall, PlayingField field) {
        Map<Pair<Integer, Integer>, Block> extraBlocks = wall.getExtraBlocks(field);
        Map<Pair<Integer, Integer>, Block> correctBlocks = wall.getCorrectBlocks(field);
        Map<Pair<Integer, Integer>, Block> missingBlocks = wall.getMissingBlocks(field);

        // Check score
        int points = correctBlocks.size() - extraBlocks.size();
        if (points < 0) points = 0;
        if (penalizeEmptyField && !wall.isEmpty() && extraBlocks.isEmpty() && correctBlocks.isEmpty()) points = -1;
        return points;
    }

    public void scoreEvent(ModifierEvent event) {
        if (event instanceof Rush rush) {
            // (x/2)^2
            int rushResults = (int) Math.pow(((double) rush.getBoardsCleared() / 2), 2);
            field.overrideDisplay(DisplayType.SCORE, 80, miniMessage.deserialize("<red>+<bold>" + rushResults + " points from Rush!!!"));
            score += rushResults;
        }
    }

    public double calculatePercent(Wall wall, int score) {
        if (wall.getHoles().isEmpty() && score == 0) return 1;
        if (score < 0) return 0;
        return (double) score / wall.getHoles().size();
    }

    public double calculatePercent(Wall wall, PlayingField field) {
        int score = calculateScore(wall, field);
        if (wall.getHoles().isEmpty() && score == 0) return 1;
        if (score < 0) return 0;
        return (double) score / wall.getHoles().size();
    }

    public int getPerfectWallsCleared() {
        return perfectWallsCleared;
    }

    public int getScore() {
        return score;
    }

//    public void reset() {
//        score = 0;
//        blocksPlaced = 0;
//        meter = 0;
//        perfectWallsCleared = 0;
//        time = 0;
//        gamemode = null;
//        settings = null;
//        level = 1;
//        doLevels = false;
//    }

    public Component getFormattedTime() {
        return Component.text(Utils.getFormattedTime(time));
    }

    public int getAbsoluteTimeElapsed() {
        return absoluteTimeElapsed;
    }

    public void tick() {
        absoluteTimeElapsed++;

        // if a timefreeze modifier event is active and we're in a singleplayer game, pause the timer
        if (field.eventActive() && field.getEvent().timeFreeze
                && settings.getBooleanAttribute(GamemodeAttribute.SINGLEPLAYER)) return;
        // if we're in a score attack game, decrement the time
        if (settings.getIntAttribute(GamemodeAttribute.TIME_LIMIT) > 0) time--;
        else time++;

        if (settings.getIntAttribute(GamemodeAttribute.TIME_LIMIT) > 0) {
            Title.Times warningTimes = Title.Times.times(Duration.ZERO, Duration.ofMillis(2000), Duration.ofMillis(250));
            Title.Times finalCountdownTimes = Title.Times.times(Duration.ZERO, Duration.ofMillis(1000), Duration.ofMillis(250));
            if ((int) settings.getAttribute(GamemodeAttribute.TIME_LIMIT) >= 120 * 20) {
                if (time <= 0) {
                    field.sendMessageToPlayers(miniMessage.deserialize("<red>Time's up!"));
                    field.stop();
                } else if (time == 20 * 60) {
                    field.sendTitleToPlayers(Title.title(
                            Component.empty(),
                            miniMessage.deserialize("<yellow>1 minute remaining!"),
                            warningTimes));
                } else if (time == 20 * 30) {
                    field.sendTitleToPlayers(Title.title(
                            Component.empty(),
                            miniMessage.deserialize("<yellow>30 seconds remaining!"),
                            warningTimes));
                } else if (time <= 20 * 10 && time % 20 == 0) {
                    field.sendTitleToPlayers(Title.title(
                            Component.empty(),
                            miniMessage.deserialize("<red>" + time / 20),
                            finalCountdownTimes));
                }
            } else {
                if (time <= 0) {
                    field.sendMessageToPlayers(miniMessage.deserialize("<red>Time's up!"));
                    field.stop();
                }
                if (time == 20 * 20) {
                    field.sendTitleToPlayers(Title.title(
                            Component.empty(),
                            miniMessage.deserialize("<yellow>20 seconds remaining!"),
                            warningTimes));
                } else if (time <= 20 * 10 && time % 20 == 0) {
                    field.sendTitleToPlayers(Title.title(
                            Component.empty(),
                            miniMessage.deserialize("<red>" + time / 20),
                            finalCountdownTimes));
                }
            }
        }

        // Scoreboard updating
        if (absoluteTimeElapsed % 10 == 0) {
            updateScoreboard();
        }
    }

    // todo probably make this scoreboard system its own class
    public void updateScoreboard() {
        if (scoreboard == null) return;
        for (ScoreboardEntry entry : scoreboardEntries) {
            switch (entry.getType()) {
                case SCORE -> entry.update(scoreboard, objective, miniMessage.deserialize("" + score));
                case STAGE -> {
                    if (multiplayerGame != null && multiplayerGame instanceof ScoreAttackGame game) {
                        entry.update(scoreboard, objective, game.getStage().getComponent());
                    }
                }
                case TIME -> entry.update(scoreboard, objective, getFormattedTime());
                case POSITION -> {
                    if (multiplayerGame == null) {
                        entry.update(scoreboard, objective, miniMessage.deserialize("<gold>Singleplayer game!"));
                        break;
                    }
                    int position = multiplayerGame.getRank(field);
                    if (position == 1) {
                        entry.update(scoreboard, objective, miniMessage.deserialize("<gold>1"));
                    } else {
                        entry.update(scoreboard, objective, Component.text(position));
                    }
                }
                case EMPTY -> entry.update(scoreboard, objective);
                case POINTS_BEHIND -> {
                    int position = multiplayerGame.getRank(field);
                    int pointsBehind = multiplayerGame.getPointsBehindNextRank(field);
                    if (position == 1) {
                        entry.forceUpdate(scoreboard, objective, miniMessage.deserialize("<gold>You're in the lead!"));
                    } else {
                        entry.update(scoreboard, objective, Component.text(pointsBehind),
                                Component.text(position-1));
                    }
                }
                case PLAYERS -> entry.update(scoreboard, objective,
                        Component.text(multiplayerGame.getPlayerCount()));
            }
        }

    }

    private void addScoreboardEntry(ScoreboardEntry entry) {
        scoreboardEntries.add(entry);
        entry.addToObjective(objective);
    }

    public void createScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        scoreboard = manager.getNewScoreboard();
        Team team = scoreboard.registerNewTeam(FillInTheWall.NO_COLLISION_TEAM_NAME);
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        objective = scoreboard.registerNewObjective("fillinthewall", Criteria.DUMMY,
                miniMessage.deserialize("<yellow><bold>Fill in the Wall"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        addScoreboardEntry(new ScoreboardEntry(ScoreboardEntryType.EMPTY, 1));
        addScoreboardEntry(new ScoreboardEntry(ScoreboardEntryType.STAGE, 2));
        addScoreboardEntry(new ScoreboardEntry(ScoreboardEntryType.TIME, 3));
        addScoreboardEntry(new ScoreboardEntry(ScoreboardEntryType.EMPTY, 4));
        addScoreboardEntry(new ScoreboardEntry(ScoreboardEntryType.SCORE, 5));
        addScoreboardEntry(new ScoreboardEntry(ScoreboardEntryType.POSITION, 6));
        addScoreboardEntry(new ScoreboardEntry(ScoreboardEntryType.POINTS_BEHIND, 7));
        addScoreboardEntry(new ScoreboardEntry(ScoreboardEntryType.EMPTY, 8));
        addScoreboardEntry(new ScoreboardEntry(ScoreboardEntryType.EMPTY, 9));
        addScoreboardEntry(new ScoreboardEntry(ScoreboardEntryType.PLAYERS, 10));

        for (Player player : field.getPlayers()) {
            player.setScoreboard(scoreboard);
            team.addEntity(player);
        }
    }

    public void removeScoreboard() {
        for (Player player : field.getPlayers()) {
            Utils.resetScoreboard(player);
        }
        for (ScoreboardEntry entry : scoreboardEntries) {
            entry.destroy();
        }
        scoreboard = null;
        objective = null;
        scoreboardEntries.clear();
    }

    public Scoreboard getScoreboard() {
        return scoreboard;
    }

    public void awardXP(Player player) {
        if (player == null) return;
        if (field.isLatePlayer(player)) return;
        if (Database.isOfflineMode()) return;
        
        // If the game is still running or was marked as incomplete, do not award a bonus
        boolean participationBonus = !(incompleteGame ||
                (multiplayerGame != null && multiplayerGame.isIncompleteGame()) ||
                field.hasStarted());
        int xp = getXp(participationBonus);

        if (xp > 0) {
            UUID uuid = player.getUniqueId();
            try {
                int level = PlayerLevels.getLevel(uuid).getValue0();
                player.sendActionBar(Component.text("+" + xp + " XP", NamedTextColor.AQUA));
                PlayerLevels.addXP(player.getUniqueId(), xp);
                if (level != PlayerLevels.getLevel(player.getUniqueId()).getValue0()) {
                    player.sendMessage(Component.text("You've leveled up to ", NamedTextColor.YELLOW)
                            .append(PlayerLevels.getPrefix(uuid)));
                    player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
                }
            } catch (SQLException e) {
                e.printStackTrace();
                player.sendActionBar(Component.text("Error while awarding XP", NamedTextColor.RED));
            }
        }
    }

    private int getXp(boolean participationBonus) {
        int xp = 0;
        switch (gamemode) {
            case ENDLESS -> {
                xp += perfectWallsCleared;
                xp += (endlessRun.currentPhase - 1) * 10;
            }
            case SCORE_ATTACK -> {
                if (participationBonus) xp += 20;
                xp += score / 5;
                xp = Math.min(xp, 50);
            }
            case SPRINT -> {
                if (participationBonus) xp += 20;
            }
            case RUSH_SCORE_ATTACK -> {
                if (participationBonus) xp += 20;
                xp += score / 20;
                xp = Math.min(xp, 50);
            }
            case MARATHON -> {
                xp += score / 10;
                xp = Math.min(xp, 150);
            }
            case MEGA -> {
                if (participationBonus) xp += 100;
            }
            case MULTIPLAYER_SCORE_ATTACK -> {
                if (participationBonus) xp += 50;
                xp += score / 10;
                xp = Math.min(xp, 150);
            }
        }
        return xp;
    }

    public void announceFinalScore() {
        boolean scoreByTime = gamemode.getDefaultSettings().getBooleanAttribute(GamemodeAttribute.SCORE_BY_TIME);
        boolean teamEffort = gamemode.getDefaultSettings().getBooleanAttribute(GamemodeAttribute.TEAM_EFFORT);
        boolean solo = field.getPlayers().size() == 1 && playersOnGameStart == 1;
        if (scoreByTime) {
            field.sendMessageToPlayers(miniMessage.deserialize("<aqua>Your final time is <bold>" +
                    Utils.getPreciseFormattedTime(time)));
        } else {
            field.sendMessageToPlayers(miniMessage.deserialize("<green>Your final score is <bold>" + score));
        }
        if (!Database.isOfflineMode()) {
            Set<Player> playersCopy = new HashSet<>(field.getPlayers());
            Bukkit.getScheduler().runTaskAsynchronously(FillInTheWall.getInstance(), () -> {
                if (Database.isSupported(gamemode) && (teamEffort || solo)) submitScores(scoreByTime, playersCopy);
                for (Player player : field.getPlayers()) {;
                    updateStats(player);
                }
            });
        }
    }

    private void submitScores(boolean scoreByTime, Set<Player> players) {
        ArrayList<Player> eligiblePlayers = new ArrayList<>();
        if (gamemode.getDefaultSettings().getBooleanAttribute(GamemodeAttribute.TEAM_EFFORT)) {
            eligiblePlayers.addAll(players);
        } else if (players.size() == 1) {
            eligiblePlayers.add(players.iterator().next());
        }
        if (scoreByTime) {
            // Check that clear conditions have been met
            if (perfectWallsCleared < gamemode.getDefaultSettings().getIntAttribute(GamemodeAttribute.PERFECT_WALL_CAP)) {
                return;
            }
            if (eventCount < gamemode.getDefaultSettings().getIntAttribute(GamemodeAttribute.MODIFIER_EVENT_CAP)) {
                return;
            }
        }

        for (Player player : eligiblePlayers) {
            try {
                int record = Database.getRecord(player.getUniqueId(), gamemode);
                if ((scoreByTime && time < record) || (!scoreByTime && score > record)) {
                    if (scoreByTime) {
                        Database.updateRecord(player.getUniqueId(), gamemode, time);
                    } else {
                        Database.updateRecord(player.getUniqueId(), gamemode, score);
                    }

                    Bukkit.getScheduler().runTask(FillInTheWall.getInstance(), () -> {
                        player.sendMessage(miniMessage.deserialize("<gold>New personal best!"));
                        player.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, player.getLocation(), 200, 0, 0, 0, 0.2);
                        player.playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1, 1);
                        Title.Times times = Title.Times.times(Duration.ZERO, Duration.ofMillis(3000), Duration.ofMillis(1000));
                        player.showTitle(Title.title(
                                miniMessage.deserialize("<aqua>PERSONAL BEST"), Component.empty(),
                                times));
                    });
                } else {
                    Bukkit.getScheduler().runTask(FillInTheWall.getInstance(), () -> {
                        if (scoreByTime) {
                            player.sendMessage(miniMessage.deserialize(
                                    "<aqua>Personal best: <bold>" + Utils.getPreciseFormattedTime(record)));
                        } else {
                            player.sendMessage(miniMessage.deserialize("<aqua>Personal best: <bold>" + record));
                        }
                        player.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, player.getLocation(), 200, 0, 0, 0, 0.2);
                        player.playSound(player, Sound.BLOCK_VAULT_OPEN_SHUTTER, 1, 1);
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                Bukkit.getScheduler().runTask(FillInTheWall.getInstance(), () ->
                        player.sendMessage(miniMessage.deserialize("<red>Error while updating time!")));
            }
        }
    }

    public void startTrackingStats(Player player) {
        if (player == null || !field.getPlayers().contains(player)) return;
        Instant now = Instant.now();
        playerStartTimes.put(player.getUniqueId(), now.getEpochSecond());
        perfectWallsOnJoin.put(player.getUniqueId(), perfectWallsCleared);
    }

    protected void updateStats(Player player) {
        if (Database.isOfflineMode()) return;
        UUID uuid = player.getUniqueId();
        try {
            if (playerStartTimes.containsKey(uuid)) {
                long timeElapsed = Instant.now().getEpochSecond() - playerStartTimes.remove(uuid);
                long currentPlaytime = Database.getPlaytime(uuid);
                Database.setPlaytime(uuid, currentPlaytime + timeElapsed);
            }
            if (perfectWallsOnJoin.containsKey(uuid) && gamemode != Gamemode.SANDBOX) {
                int perfects = perfectWallsCleared - perfectWallsOnJoin.remove(uuid);
                int currentPerfects = Database.getPerfectWalls(uuid);
                Database.setPerfectWalls(uuid, currentPerfects + perfects);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            Bukkit.getScheduler().runTask(FillInTheWall.getInstance(), () ->
                    player.sendMessage(miniMessage.deserialize("<red>Error while updating stats!")));
        }
    }

    public EndScreen createEndScreen() {
        EndScreen endScreen = new EndScreen(field.getCenter(true, false).add(0, 1, 0));
        endScreen.addLine(Component.text(Utils.playersToString(field.getPlayers())));
        endScreen.addLine(gamemode.getTitle());
        endScreen.addLine(Component.empty());
        // todo bold is still bleeding through, fix later
        endScreen.addLine(miniMessage.deserialize("<green>Final score: <bold>" + score + "</bold>"));
        if (settings.getBooleanAttribute(GamemodeAttribute.MULTIPLAYER) && multiplayerGame != null) {
            if (gamemode == Gamemode.MULTIPLAYER_SCORE_ATTACK) {
                endScreen.addLine(miniMessage.deserialize("<white>Position: No. " + multiplayerGame.getRank(field)));
            }
        }
        if (settings.getIntAttribute(GamemodeAttribute.TIME_LIMIT) <= 0) {
            endScreen.addLine(miniMessage.deserialize("<aqua>Time: <bold>" + Utils.getPreciseFormattedTime(time) + "</bold>"));
        }
        endScreen.addLine(miniMessage.deserialize("<gold>Perfect Walls cleared: <bold>" + perfectWallsCleared + "</bold>"));
        endScreen.addLine(miniMessage.deserialize("<red>" + getFormattedBlocksPerSecond() + " blocks per second"));
        return endScreen;
    }

    // Called by the PlayingField when the game starts
    public void setGamemode(Gamemode gamemode, GamemodeSettings settings) {
        this.gamemode = gamemode;
        this.settings = settings;
        for (GamemodeAttribute attribute : GamemodeAttribute.values()) {
            Object value = settings.getAttribute(attribute);
            
            // todo decide whether to cast here or use the methods that cast beforehand

            switch (attribute) {
                case TIME_LIMIT -> setTime((int) value);
                case DO_LEVELS -> {
                    doLevels = (boolean) value;
                    if (doLevels) setLevel(1);
                }
                case CONSISTENT_HOLE_COUNT -> {
                    if (!doLevels) field.getQueue().setRandomizeFurther(!(boolean) value);
                }
                case RANDOM_HOLE_COUNT -> {
                    if (!doLevels) field.getQueue().setRandomHoleCount((int) value);
                }
                case CONNECTED_HOLE_COUNT -> {
                    if (!doLevels) field.getQueue().setConnectedHoleCount((int) value);
                }
                case STARTING_WALL_ACTIVE_TIME -> {
                    if (!doLevels) field.getQueue().setWallActiveTime((int) value);
                }
                case WALL_TIME_DECREASE_AMOUNT -> {
                    if (doLevels) wallTimeDecreaseAmount = (int) value;
                }
                case CHARGES -> {
                    chargesAvailable = (int) value;
                }
            }
        }
        if (!settings.getBooleanAttribute(GamemodeAttribute.MULTIPLAYER) &&
                settings.getModifierEventTypeAttribute(GamemodeAttribute.SINGULAR_EVENT) != null
                && settings.getModifierEventTypeAttribute(GamemodeAttribute.SINGULAR_EVENT) != ModifierEvent.Type.NONE) {

            activateEvent(settings.getModifierEventTypeAttribute(GamemodeAttribute.SINGULAR_EVENT)).setInfinite(true);

        } else if (gamemode == Gamemode.SANDBOX) {
            WallBundle bundle = WallBundle.getWallBundle("amogus");
            // todo hardcoded dimension check
            if (bundle.size() == 0 || field.getLength() != 7 || field.getHeight() != 4) {
                field.sendMessageToPlayers(miniMessage.deserialize("<red>Loading custom walls failed"));
            } else {
                List<Wall> walls = bundle.getWalls();
                field.getQueue().clearAllWalls();
                walls.forEach(field.getQueue()::addWall);
            }
        } else if (gamemode == Gamemode.ENDLESS) {
            endlessRun = new EndlessRun(this);
        }
        if (settings.getAttribute(GamemodeAttribute.MULTIPLAYER) == Boolean.TRUE) {
            createScoreboard();
        }
    }
    
    // Default gamemode settings
    public void setGamemode(Gamemode gamemode) {
        setGamemode(gamemode, gamemode.getDefaultSettings());
    }

    // levels
    public void setLevelProgressMax(int meterMax) {
        this.levelProgressMax = meterMax;
    }

    public enum ActionBarType {
        LEVEL_PROGRESS, PERFECT_WALLS, ENDLESS_LEVEL_PROGRESS, CHARGES, NONE
    }

    public Component getLevelProgressActionbar() {
        double percentFilled = levelProgress / levelProgressMax;

        TextColor color;
        String modifier = "";
        if (percentFilled <= 0.3) {
            color = NamedTextColor.GRAY;
        } else if (percentFilled <= 0.7) {
            color = NamedTextColor.YELLOW;
        } else {
            color = NamedTextColor.GREEN;
        }
        return Component.text(modifier + " Next level: " + String.format("%.2f", levelProgress) + "/" + levelProgressMax, color);
    }

    public Component getPerfectWallsActionbar() {
        int perfectWallsRequired = settings.getIntAttribute(GamemodeAttribute.PERFECT_WALL_CAP);
        return miniMessage.deserialize("<aqua>Perfect Walls: " + perfectWallsCleared + "/" + perfectWallsRequired);
    }

    public Component getEndlessLevelProgressActionbar() {
        if (endlessRun == null) return Component.empty();
        int pointsRemaining = Math.max(endlessRun.scoreToNextLevel - score, 0);
        String color = "<gray>";
        if (pointsRemaining < 10) color = "<green>";
        return miniMessage.deserialize(color + pointsRemaining + " points to next level");
    }

    public Component getChargesActionbar() {
        String event = "";
        ModifierEvent.Type type = settings.getModifierEventTypeAttribute(GamemodeAttribute.CHARGE_EVENT);
        if (type != ModifierEvent.Type.NONE && type != null) event = type.getClazz().getSimpleName();
        if (chargesAvailable <= 0) {
            return miniMessage.deserialize("<red>Out of charges!");
        }
        return miniMessage.deserialize("<aqua>" + event + " Charges: " + "✦".repeat(chargesAvailable)  + " <blue><bold>Press <key:key.drop>");
    }



    public void setLevel(int level) {
        levelProgress = 0;
        int maxLevel = settings.getIntAttribute(GamemodeAttribute.LEVEL_CAP);
        field.getQueue().setRandomizeFurther(false);
        this.level = level;

        if (maxLevel > 0 && level > maxLevel) {
            field.sendMessageToPlayers(miniMessage.deserialize("<gold>Congratulations!"));
            playGameEnd();
            field.stop(false, true);
            return;
        }
        setDifficulty(level);
        setLevelProgressMax(level);
        if (level != 1) levelUpSound();
        // when we level up, delete all pending walls in the queue which forces a new wall to be made.
        field.getQueue().clearHiddenWalls();
    }

    public void setMeterItemGlint(boolean glint) {
        for (Player player : field.getPlayers()) {
            // Scan inventory for an item that has the persistent data key "METER_ITEM"
            player.getInventory().forEach(item -> {
                if (item != null && item.getItemMeta() != null && item.getItemMeta().getPersistentDataContainer().has(PlayingField.meterKey)) {
                    ItemMeta meta = item.getItemMeta();
                    meta.setEnchantmentGlintOverride(glint);
                    item.setItemMeta(meta);
                }
            });
        }
    }

    private void setDifficulty(int level) {
        WallQueue queue = field.getQueue();
        queue.setWallActiveTime(Math.max(200 - level * wallTimeDecreaseAmount, 40));

        if (level == 1) {
            queue.setRandomHoleCount(1);
            queue.setConnectedHoleCount(0);
        } else if (level == 2) {
            queue.setRandomHoleCount(1);
            queue.setConnectedHoleCount(1);
        } else {
            int remainingHoles = (level/2) + 1;
            if (level < 5) {
                queue.setRandomHoleCount(1);
                remainingHoles -= 1;

            } else if (level < 9) {
                queue.setRandomHoleCount(2);
                remainingHoles -= 2;
            } else {
                queue.setRandomHoleCount(3);
                remainingHoles -= 3;
            }
            queue.setConnectedHoleCount(remainingHoles);
        }
    }

    public int getLevel() {
        return level;
    }

    public Gamemode getGamemode() {
        return gamemode;
    }

    public void setTime(int time) {
        this.time = time;
    }

    public void increaseBlocksPlaced() {
        blocksPlaced++;
    }

    public double getBlocksPerSecond() {
        if (absoluteTimeElapsed == 0) return 0;
        return blocksPlaced / ((double) absoluteTimeElapsed / 20);
    }

    public String getFormattedBlocksPerSecond() {
        return String.format("%.2f", getBlocksPerSecond());
    }
    
    public void addGarbageToQueue(Wall wall) {
        garbageQueue.push(wall);
    }
    
    public void setOpponent(PlayingField field) {
        opponent = field;
    }

    public Deque<Wall> getGarbageQueue() {
        return garbageQueue;
    }

    public int getEventCount() {
        return eventCount;
    }

    public void setMultiplayerGame(MultiplayerGame multiplayerGame) {
        this.multiplayerGame = multiplayerGame;
    }

    public MultiplayerGame getMultiplayerGame() {
        return multiplayerGame;
    }

    public int getPointsBehind() {
        if (multiplayerGame == null) return -1;
        return multiplayerGame.getPointsBehindNextRank(field);
    }

    public int getPosition() {
        if (multiplayerGame == null) return -1;
        return multiplayerGame.getRank(field);
    }

    public GamemodeSettings getSettings() {
        return settings;
    }

    public int getPerfectWallChain() {
        return perfectWallChain;
    }

    public void breakPerfectWallChain() {
        perfectWallChain = 0;
    }

    public double getMeterPercentFilled() {
        return levelProgress / levelProgressMax;
    }

    public void setPlayersOnGameStart(int playersOnGameStart) {
        this.playersOnGameStart = playersOnGameStart;
    }

    public boolean isIncompleteGame() {
        return incompleteGame;
    }

    // Marks the game as incomplete and not eligible for participation XP
    public void setIncompleteGame(boolean incompleteGame) {
        this.incompleteGame = incompleteGame;
    }

    public int getScoreToNextLevel() {
        if (endlessRun == null) return -1;
        else return endlessRun.scoreToNextLevel;
    }
}
