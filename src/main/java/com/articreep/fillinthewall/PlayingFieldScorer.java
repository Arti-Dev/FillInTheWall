package com.articreep.fillinthewall;

import com.articreep.fillinthewall.display.ScoreboardEntry;
import com.articreep.fillinthewall.display.ScoreboardEntryType;
import com.articreep.fillinthewall.gamemode.Gamemode;
import com.articreep.fillinthewall.gamemode.GamemodeAttribute;
import com.articreep.fillinthewall.gamemode.GamemodeSettings;
import com.articreep.fillinthewall.menu.EndScreen;
import com.articreep.fillinthewall.modifiers.*;
import com.articreep.fillinthewall.multiplayer.MultiplayerGame;
import com.articreep.fillinthewall.multiplayer.ScoreAttackGame;
import com.articreep.fillinthewall.utils.Utils;
import net.md_5.bungee.api.chat.*;
import org.bukkit.Bukkit;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.javatuples.Pair;

import java.util.*;

public class PlayingFieldScorer {
    PlayingField field;
    private int score = 0;
    private double meter = 0;
    private int perfectWallsCleared = 0;
    private int perfectWallChain = 0;
    private int wallsClearedWithMeterFull = 0;
    private boolean hasUsedMeter = false;
    private boolean hasImportedCustomWalls = false;
    private double blocksPlaced = 0;
    // time in ticks (this is displayed on the text display)
    private int time = 0;
    /** for calculating blocks per second */
    private int absoluteTimeElapsed = 0;
    private Gamemode gamemode = Gamemode.INFINITE;
    private GamemodeSettings settings = Gamemode.INFINITE.getDefaultSettings();
    private int eventCount = 0;

    // Levels (if enabled)
    boolean doLevels = false;
    private int level = 1;
    private int meterMax = 10;
    private int wallTimeDecreaseAmount = 20;

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

    public PlayingFieldScorer(PlayingField field) {
        this.field = field;
    }

    public enum BonusType {
        PERFECT, FIRE, STRIPE, PLAYER
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
            awardMeterPoints(percent);
        }

        // Activate meter
        if (meter >= meterMax && doLevels) {
            setLevel(level + 1);
            field.sendTitleToPlayers("", ChatColor.GREEN + "Level up!", 0, 10, 5);
        } else if (meter >= meterMax && ((boolean) settings.getAttribute(GamemodeAttribute.AUTOMATIC_METER))) {
            ModifierEvent newEvent = activateEvent(settings.getModifierEventTypeAttribute(GamemodeAttribute.ABILITY_EVENT), true);
            newEvent.allowMeterAccumulation = false;
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

        // Update meter item
        setMeterItemGlint(isMeterFilledEnough(meter / meterMax));

        // Custom walls tip display
        if (gamemode == Gamemode.CUSTOM && !hasImportedCustomWalls) {
            field.setTipDisplay(ChatColor.YELLOW + "You can import custom walls with /fitw custom <name>");
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

    private void awardMeterPoints(double percent) {
        if (percent >= Judgement.COOL.getPercent()) {
            meter += percent;
            if (meter > meterMax) {
                meter = meterMax;

                wallsClearedWithMeterFull++;
                ModifierEvent.Type abilityEvent = settings.getModifierEventTypeAttribute(GamemodeAttribute.ABILITY_EVENT);
                if (abilityEvent != ModifierEvent.Type.NONE && !hasUsedMeter && wallsClearedWithMeterFull >= 4) {
                    if (abilityEvent == ModifierEvent.Type.FREEZE) {
                        field.setTipDisplay(ChatColor.GRAY + "Tip: " + ChatColor.YELLOW + "Press your drop key to" +
                                ChatColor.AQUA + " freeze " + ChatColor.YELLOW + "all active walls!");
                    } else {
                        field.setTipDisplay(ChatColor.GRAY + "Tip: " + ChatColor.YELLOW + "Press your drop key to activate a special ability!");
                    }
                }
            }
        } else if (!field.eventActive() && clearingMode) {
            // You cannot lose progress if levels are enabled
            if (!doLevels) {
                meter -= 1;
            }
            if (meter < 0) meter = 0;
        }
    }

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
            meter -= 1;
            if (meter <= 0) {
                clearingMode = true;
                field.sendMessageToPlayers("Meter empty! Switched to attack mode!");
                meter = 0;
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
        double percent = meter / meterMax;

        if (!clearingMode) {
            clearingMode = true;
            player.sendMessage(ChatColor.GREEN + "Switched to attack mode!");
        } else {
            if (percent < 0.25) {
                player.sendMessage(ChatColor.RED + "Your meter isn't full enough!");
            } else {
                clearingMode = false;
                player.sendMessage(ChatColor.GREEN + "Switched to defense mode!");

            }
        }
    }

    public void onMeterActivate(Player player) {
        if (field.getEvent() instanceof Tutorial tutorial) {
            tutorial.onMeterActivate(player);
            return;
        }

        if (settings.getModifierEventTypeAttribute(GamemodeAttribute.ABILITY_EVENT).createEvent(field) == null) {
            player.sendMessage(ChatColor.RED + "No event to activate!");
            return;
        }
        if (isMeterFilledEnough(meter / meterMax)) {
            ModifierEvent newEvent = activateEvent(settings.getModifierEventTypeAttribute(GamemodeAttribute.ABILITY_EVENT), true);
            newEvent.allowMeterAccumulation = false;
            hasUsedMeter = true;
        } else {
            player.sendMessage(ChatColor.RED + "Your meter isn't full enough!");
        }
    }

    /**
     * Attempts to activate the event associated with the current gamemode.
     */
    public ModifierEvent activateEvent(ModifierEvent.Type type, boolean resetMeter) {
        if (type == null || type == ModifierEvent.Type.NONE) {
            return null;
        }
        ModifierEvent event = type.createEvent(field);
        return activateEvent(event, resetMeter);
    }

    public ModifierEvent activateEvent(ModifierEvent.Type type) {
        return activateEvent(type, false);
    }

    public ModifierEvent activateEvent(ModifierEvent event, boolean resetMeter) {
        Bukkit.getScheduler().runTask(FillInTheWall.getInstance(), () -> {
            event.activate();
            eventCount++;

            // Update meter item
            setMeterItemGlint(isMeterFilledEnough(meter / meterMax));
            if (resetMeter) meter = 0;
        });
        return event;
    }

    public ModifierEvent activateEvent(ModifierEvent event) {
        return activateEvent(event, false);
    }

    public void displayScoreTitle(Judgement judgement, int score, Map<BonusType, Integer> bonusMap) {
        field.sendTitleToPlayers(
                judgement.getColor() + judgement.getText(),
                judgement.getColor() + "" + (score + bonusMap.get(BonusType.PERFECT)) + " points",
                0, 10, 5);
    }

    // todo these two methods could be replaced with some reflection
    public boolean isMeterFilledEnough(double percent) {
        ModifierEvent.Type type = settings.getModifierEventTypeAttribute(GamemodeAttribute.ABILITY_EVENT);
        if (type != ModifierEvent.Type.FREEZE && type != ModifierEvent.Type.RUSH && type != ModifierEvent.Type.TUTORIAL) {
            return percent >= 1;
        } else {
            if (type == ModifierEvent.Type.RUSH && percent >= ModifierEvent.createEvent(Rush.class, null).getMeterPercentRequired()) {
                return true;
            } else if (type == ModifierEvent.Type.FREEZE && percent >= ModifierEvent.createEvent(Freeze.class, null).getMeterPercentRequired()) {
                return true;
            } else return type == ModifierEvent.Type.TUTORIAL;
        }
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
        return points;
    }

    public void scoreEvent(ModifierEvent event) {
        if (event instanceof Rush rush) {
            // (x/2)^2
            int rushResults = (int) Math.pow(((double) rush.getBoardsCleared() / 2), 2);
            field.overrideScoreDisplay(80, ChatColor.RED + "+" + ChatColor.BOLD + rushResults + " points from Rush!!!");
            score += rushResults;
        }
    }

    public double calculatePercent(Wall wall, int score) {
        if (wall.getHoles().isEmpty() && score == 0) return 1;
        return (double) score / wall.getHoles().size();
    }

    public double calculatePercent(Wall wall, PlayingField field) {
        int score = calculateScore(wall, field);
        if (wall.getHoles().isEmpty() && score == 0) return 1;
        return (double) score / wall.getHoles().size();
    }

    public int getPerfectWallsCleared() {
        return perfectWallsCleared;
    }

    public int getScore() {
        return score;
    }

    public void reset() {
        score = 0;
        blocksPlaced = 0;
        meter = 0;
        perfectWallsCleared = 0;
        time = 0;
        gamemode = null;
        settings = null;
        level = 1;
        doLevels = false;
    }

    public String getFormattedTime() {
        return Utils.getFormattedTime(time);
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
            if ((int) settings.getAttribute(GamemodeAttribute.TIME_LIMIT) >= 120 * 20) {
                if (time <= 0) {
                    field.sendMessageToPlayers(ChatColor.RED + "Time's up!");
                    field.stop();
                } else if (time == 20 * 60) {
                    field.sendTitleToPlayers("", ChatColor.YELLOW + "1 minute remaining!", 0, 40, 5);
                } else if (time == 20 * 30) {
                    field.sendTitleToPlayers("", ChatColor.YELLOW + "30 seconds remaining!", 0, 40, 5);
                } else if (time <= 20 * 10 && time % 20 == 0) {
                    field.sendTitleToPlayers("", ChatColor.RED + String.valueOf(time / 20), 0, 20, 5);
                }
            } else {
                if (time <= 0) {
                    field.sendMessageToPlayers(ChatColor.RED + "Time's up!");
                    field.stop();
                }
                if (time == 20 * 20) {
                    field.sendTitleToPlayers("", ChatColor.YELLOW + "20 seconds remaining!", 0, 40, 5);
                } else if (time <= 20 * 10 && time % 20 == 0) {
                    field.sendTitleToPlayers("", ChatColor.RED + String.valueOf(time / 20), 0, 20, 5);
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
                case SCORE -> entry.update(scoreboard, objective, score);
                case STAGE -> {
                    if (multiplayerGame != null && multiplayerGame instanceof ScoreAttackGame game) {
                        entry.update(scoreboard, objective, game.getStage().getString());
                    }
                }
                case TIME -> entry.update(scoreboard, objective, getFormattedTime());
                case POSITION -> {
                    if (multiplayerGame == null) {
                        entry.update(scoreboard, objective, ChatColor.GOLD + "Singleplayer game!");
                        break;
                    }
                    int position = multiplayerGame.getRank(field);
                    if (position == 1) {
                        entry.update(scoreboard, objective, ChatColor.GOLD + "1");
                    } else {
                        entry.update(scoreboard, objective, position);
                    }
                }
                case EMPTY -> entry.update(scoreboard, objective);
                case POINTS_BEHIND -> {
                    int position = multiplayerGame.getRank(field);
                    int pointsBehind = multiplayerGame.getPointsBehindNextRank(field);
                    if (position == 1) {
                        entry.forceUpdate(scoreboard, objective, ChatColor.GOLD + "You're in the lead!");
                    } else {
                        entry.update(scoreboard, objective, pointsBehind, position-1);
                    }
                }
                case PLAYERS -> entry.update(scoreboard, objective, multiplayerGame.getPlayerCount());
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
        objective = scoreboard.registerNewObjective("fillinthewall", "dummy",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Fill in the Wall");
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

    public void announceFinalScore() {
        boolean scoreByTime = gamemode.getDefaultSettings().getBooleanAttribute(GamemodeAttribute.SCORE_BY_TIME);
        if (scoreByTime) {
            field.sendMessageToPlayers(ChatColor.AQUA + "Your final time is " + ChatColor.BOLD + Utils.getFormattedTime(time));
        } else {
            field.sendMessageToPlayers(ChatColor.GREEN + "Your final score is " + ChatColor.BOLD + score);
        }
        // todo atm other players can simply walk onto the playing field and step off before the game ends
        if (ScoreDatabase.isSupported(gamemode)) {
            ArrayList<Player> players = new ArrayList<>();
            if (gamemode.getDefaultSettings().getBooleanAttribute(GamemodeAttribute.TEAM_EFFORT)) {
                players.addAll(field.getPlayers());
            } else if (field.getPlayers().size() == 1) {
                players.add(field.getPlayers().iterator().next());
            }
            if (scoreByTime) {
                // Check that clear conditions have been met
                if (perfectWallsCleared < gamemode.getDefaultSettings().getIntAttribute(GamemodeAttribute.PERFECT_WALL_CAP))
                    return;
                if (eventCount < gamemode.getDefaultSettings().getIntAttribute(GamemodeAttribute.MODIFIER_EVENT_CAP))
                    return;
            }
            for (Player player : players) {
                try {
                    int record = ScoreDatabase.getRecord(player.getUniqueId(), gamemode);
                    if ((scoreByTime && time < record) || (!scoreByTime && score > record)) {
                        if (scoreByTime) {
                            ScoreDatabase.updateRecord(player.getUniqueId(), gamemode, time);
                        } else {
                            ScoreDatabase.updateRecord(player.getUniqueId(), gamemode, score);
                        }
                        player.sendMessage(ChatColor.GOLD + "New personal best!");
                        player.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, player.getLocation(), 200, 0, 0, 0, 0.2);
                        player.playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1, 1);
                        player.sendTitle(ChatColor.AQUA + "PERSONAL BEST", "", 0, 60, 20);
                    } else {
                        if (scoreByTime) {
                            player.sendMessage(ChatColor.AQUA + "Personal best: " + ChatColor.BOLD + Utils.getFormattedTime(record));
                        } else {
                            player.sendMessage(ChatColor.AQUA + "Personal best: " + ChatColor.BOLD + record);
                        }
                        player.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, player.getLocation(), 200, 0, 0, 0, 0.2);
                        player.playSound(player, Sound.BLOCK_VAULT_OPEN_SHUTTER, 1, 1);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    player.sendMessage(ChatColor.RED + "Error while updating time!");
                }
            }
        }
    }

    public EndScreen createEndScreen() {
        EndScreen endScreen = new EndScreen(field.getCenter(true, false).add(0, 1, 0));
        endScreen.addLine(Utils.playersToString(field.getPlayers()));
        endScreen.addLine(gamemode.getTitle());
        endScreen.addLine("");
        endScreen.addLine(ChatColor.GREEN + "Final score: " + ChatColor.BOLD + score);
        if (settings.getBooleanAttribute(GamemodeAttribute.MULTIPLAYER) && multiplayerGame != null) {
            if (gamemode == Gamemode.MULTIPLAYER_SCORE_ATTACK) {
                endScreen.addLine(ChatColor.WHITE + "Position: No. " + multiplayerGame.getRank(field));
            }
        }
        if (settings.getIntAttribute(GamemodeAttribute.TIME_LIMIT) <= 0) {
            endScreen.addLine(ChatColor.AQUA + "Time: " + ChatColor.BOLD + getFormattedTime());
        }
        endScreen.addLine(ChatColor.GOLD + "Perfect Walls cleared: " + ChatColor.BOLD + perfectWallsCleared);
        endScreen.addLine(ChatColor.RED + getFormattedBlocksPerSecond() + " blocks per second");
        return endScreen;
    }

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
                case METER_MAX -> {
                    if (!doLevels) setMeterMax((int) value);
                }
                case WALL_TIME_DECREASE_AMOUNT -> {
                    if (doLevels) wallTimeDecreaseAmount = (int) value;
                }
            }
        }
        if (!settings.getBooleanAttribute(GamemodeAttribute.MULTIPLAYER) &&
                settings.getModifierEventTypeAttribute(GamemodeAttribute.SINGULAR_EVENT) != null
                && settings.getModifierEventTypeAttribute(GamemodeAttribute.SINGULAR_EVENT) != ModifierEvent.Type.NONE) {
            activateEvent(settings.getModifierEventTypeAttribute(GamemodeAttribute.SINGULAR_EVENT)).setInfinite(true);
        } else if (gamemode == Gamemode.CUSTOM) {
            WallBundle bundle = WallBundle.getWallBundle("amogus");
            // todo hardcoded dimension check
            if (bundle.size() == 0 || field.getLength() != 7 || field.getHeight() != 4) {
                field.sendMessageToPlayers(ChatColor.RED + "Loading custom walls failed");
            } else {
                List<Wall> walls = bundle.getWalls();
                field.getQueue().clearAllWalls();
                walls.forEach(field.getQueue()::addWall);
            }
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
    public void setMeterMax(int meterMax) {
        this.meterMax = meterMax;
    }

    public BaseComponent getFormattedMeter() {
        double percentFilled = meter / meterMax;

        ChatColor color;
        String modifier = "";
        ModifierEvent.Type type = settings.getModifierEventTypeAttribute(GamemodeAttribute.ABILITY_EVENT);
        if (type != ModifierEvent.Type.NONE && type != null) modifier = type.getClazz().getSimpleName() + " ";
        if (percentFilled <= 0.3) {
            color = ChatColor.GRAY;
        } else if (percentFilled <= 0.7) {
            color = ChatColor.YELLOW;
        } else {
            color = ChatColor.GREEN;
        }
        ComponentBuilder builder = new ComponentBuilder(color + modifier + "Meter: " + String.format("%.2f", meter) + "/" + meterMax);
        if (isMeterFilledEnough(percentFilled)) {
            builder.append(" " + ChatColor.BLUE + ChatColor.BOLD + "Ready! Press ")
                    .append(new KeybindComponent(Keybinds.DROP)).color(ChatColor.BLUE).bold(true);
        }
        return builder.build();
    }

    public void setLevel(int level) {
        meter = 0;
        field.getQueue().setRandomizeFurther(false);
        this.level = level;
        setDifficulty(level);
        setMeterMax(level);
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

    public double getMeterPercentFilled() {
        return meter / meterMax;
    }

    public void setHasImportedCustomWalls(boolean bool) {
        hasImportedCustomWalls = bool;
    }
}
