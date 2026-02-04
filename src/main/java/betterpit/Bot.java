package betterpit;

import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Session;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.awt.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;

import static betterpit.Bot.Phase.*;
import static betterpit.PitBotMod.*;
import static betterpit.Utils.*;

public class Bot {
    public enum Phase {WALKING, FALLING, FIGHTING}

    private BlockPos center;
    private Phase phase;
    private EntityPlayer target;

    private double reach = 25;
    private double attackreach = 4;
    private double[] smoothness = {7.9, 9};
    private double jumpchance = 0.02;
    private double strafechance = 0.02;
    private int phasetick = 0;
    private double cps = 15;
    private int loop = 0;
    private double[] ranvals;
    private int sprintresticks = 50;
    private double waitchance = 0.3;
    private int waitticks = 0;
    private int screenshotticks = 0;
    private int gheaddelay = 50;
    private int spawny;
    private double switchtargetrange = 1;
    private int mentions = 0;
    private int sessionstop = 20;
    private double reachskip = 1;
    private boolean quitbot = false;
    private int tickscolided = 0;
    private int maxtickscolided = 160;
    private double distancesq = 10;
    private int sametargetticks = 0;
    private boolean sessionpause;
    private int randomsprintresticks;
    private int currectsession;
    private int gheadtickdelay;
    private int fallticks;
    private long session;
    private boolean hasHandledDeath = false; // Флаг для обработки смерти
    private int deathTickCounter = 0;        // Счётчик тиков для задержки между командами
    private boolean afterLobbyCommand = false; // Флаг, что команда /lobby отправлена

    // ----- Добавленные переменные для «полёта» к целевой точке -----
    private boolean isFlyingToTarget = false;
    private double targetX = 140.0;
    private double targetY = 72.0;
    private double targetZ = 47.0;
    private double tolerance = 10.0;

    // ----- Порог здоровья, при котором пьём суп -----
    private double soupHealthThreshold = 6.0;

    // ----- Авто-суп модуль -----
    private final AutoSoupMod autoSoupMod = new AutoSoupMod();

    // ----- Список НИКов-целей -----
    private List<String> targetList = new ArrayList<>();

    // ----- Переменные для тик-базированных задержек -----
    private int strafeKeyReleaseTick = -1;
    private int sprintResetTick = -1;

    // ----- Флаг режима боя -----
    private boolean isInFightingMode = false;

    // ----- Переменные для обработки команд после смерти -----
    private int spawnCommandTick = -1;
    private int commandStep = 0;
    private int flyTickCounter = 0;
    private int chestClickDelay = 0;

    public Bot() {
        this.phase = WALKING;
        this.center = new BlockPos(0, mc.thePlayer.posY - 8, 0);
        this.ranvals = new double[]{
                new Random().nextInt(15),
                new Random().nextInt(15),
                new Random().nextInt(15),
                Utils.getRandom(0.5, 0.95),
                Utils.getRandom(0.85, 0.95)
        };
        this.session = System.currentTimeMillis();
        this.fallticks = (int) Utils.getRandom(5, 30);
        this.spawny = (int) mc.thePlayer.posY;
        this.sessionpause = false;
        this.randomsprintresticks = (int) Utils.getRandom(sprintresticks - 20, sprintresticks + 40);

        loadConfig();
        this.currectsession = sessionstop;
        this.gheadtickdelay = gheaddelay;

        // Регистрируем авто-суп и основной класс бота в шине событий
        MinecraftForge.EVENT_BUS.register(autoSoupMod);
        MinecraftForge.EVENT_BUS.register(this);

        // Устанавливаем кастомный ник
        setCustomNickname("zabivala2015");
    }

    // ========== МЕТОДЫ ДВИЖЕНИЯ ==========

    public void walking() {
        // Если AutoSoup модуль активен и идет рефилл супов — стоим на месте
        if (autoSoupMod.isRefilling()) {
            stopwalking();
            return;
        }

        // AIMING (наведение взгляда) — смотрим в центр
        if (phasetick > ranvals[1] && mc.thePlayer.getDistanceSq(center) > distancesq) {
            float[] angles = getFixedRotation(
                    Utils.smoothRotations(Utils.getAimRotations(center), ranvals[3], ranvals[4]),
                    new float[]{mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch}
            );

            mc.thePlayer.rotationYaw = angles[0];
            if (mc.thePlayer.onGround) {
                mc.thePlayer.rotationPitch += (float) ((angles[1] - mc.thePlayer.rotationPitch) / 2.0);
            }
        }

        // WALKING
        if (phasetick == 15 + ranvals[0]) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
            System.out.println("Bot: Нажата клавиша Forward.");
        } else if (phasetick == 40 + ranvals[1]) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
            System.out.println("Bot: Нажата клавиша Sprint.");
        } else if (phasetick == 70 + ranvals[2]) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
            System.out.println("Bot: Нажата клавиша Jump.");
        }

        // Переход в фазу падения, если «летим» вниз
        if (mc.thePlayer.fallDistance > 10) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
            System.out.println("Bot: Отключена клавиша Jump из-за падения.");
            setPhase(FALLING);
        }
    }

    public void falling() {
        // Ждём нужное кол-во тиков, затем переходим в фазу боя
        if (phasetick >= fallticks) {
            setPhase(FIGHTING);
        }
    }

    /**
     * Основной метод боя: проверка оружия, цели, атаки и т.д.
     */
    public void fighting(TickEvent.ClientTickEvent e) {
        if (autoSoupMod.isRefilling()) {
            stopwalking();
            return;
        }

        // ========== Выбор меча / золотых яблок / и т.д. ==========
        for (int slot = 8; slot >= 0; slot--) {
            ItemStack itemInSlot = mc.thePlayer.inventory.getStackInSlot(slot);
            if (itemInSlot != null) {
                // Если это яблоко / голденхед / картошка — пробуем подхилиться
                if ((Utils.isGoldenApple(itemInSlot) || Utils.isGoldenHead(itemInSlot) || Utils.isBakedPotato(itemInSlot))
                        && mc.thePlayer.getHealth() < mc.thePlayer.getMaxHealth()
                        && gheaddelay == 0) {
                    setPhase(FIGHTING);
                    mc.thePlayer.inventory.currentItem = slot;
                    mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
                    return;
                }
                // Если это меч — переключаемся на меч
                else if (itemInSlot.getItem() instanceof ItemSword) {
                    if (!(mc.thePlayer.getHeldItem().getItem() instanceof ItemSword)) {
                        mc.thePlayer.inventory.currentItem = slot;
                        return;
                    }
                }
            }
        }

        // ========== Антизастревание ==========
        if (tickscolided >= maxtickscolided) {
            // Обновим цель, если долго упираемся в стену
            target = Bot.GetTarget(targetList, reach, attackreach);
        }
        if (mc.thePlayer.isCollidedHorizontally) {
            tickscolided++;
        } else {
            tickscolided = 0;
        }

        if (tickscolided >= maxtickscolided * 2) {
            // Если совсем застряли, пишем /spawn
            spawncommand();
            tickscolided = 0;
        }

        // ========== RANDOM STRAFES (опционально) ==========
        Random random = new Random();

        // ========== Выбор цели ==========
        EntityPlayer newtarget = Bot.GetTarget(targetList, reach, attackreach);
        if (newtarget != null) {
            if (target == null || !target.equals(newtarget)) {
                target = newtarget;
                sametargetticks = 0;
            } else {
                sametargetticks++;
            }
        } else {
            target = null;
            sametargetticks = 0;
        }

        // Если 500 тиков — одна и та же цель, пробуем сменить (вдруг NPC)
        if (sametargetticks >= 500) {
            sametargetticks = 0;
            target = Bot.GetTarget(targetList, reach, attackreach);
        }

        // AIMING
        if (target != null) {
            // Если цель далеко, плавно смотрим
            if (mc.thePlayer.getDistanceToEntity(target) >= reachskip) {
                Utils.aim(target, new double[]{smoothness[0], smoothness[1]});
            }
        }

        // ATTACKING (ЛКМ с рандомной CPS)
        if (e.phase == TickEvent.Phase.START) {
            if (target != null && mc.thePlayer.getDistanceToEntity(target) <= attackreach + 2) {
                double currentCps = 11 + random.nextDouble() * 3; // 14..17
                double attackProbability = currentCps / 20.0;

                if (random.nextDouble() < attackProbability) {
                    mc.thePlayer.swingItem();
                    if (mc.objectMouseOver != null && mc.objectMouseOver.entityHit != null) {
                        mc.playerController.attackEntity(mc.thePlayer, mc.objectMouseOver.entityHit);
                    }
                }
            }

            // RESET SPRINT
            if (sprintResetTick != -1 && phasetick >= sprintResetTick) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
                System.out.println("Bot: Спринт и движение вперед снова включены.");
                sprintResetTick = -1;
            }
        }
    }

    // ========== HUD ==========
    @SubscribeEvent
    public void renderTick(TickEvent.RenderTickEvent e) {
        if (!inGame()) {
            return;
        }

        String t = "[ Kitpvp Bot stats ]";
        String t2 = "[ Phase: " + phase.toString() + " ]";
        String t4 = "[ Session: " + Utils.formatTime((int) ((System.currentTimeMillis() - session) / 1000)) + " ]";
        String t5 = "[ Made By ziga2281337666 & whytrxng ]";

        ScaledResolution res = new ScaledResolution(mc);
        int x = res.getScaledWidth() / 2 - mc.fontRendererObj.getStringWidth(t) / 2;
        int y = res.getScaledHeight() / 2 + 15;
        mc.fontRendererObj.drawString(t, (float) x - 300, (float) y, Color.cyan.getRGB(), true);
        mc.fontRendererObj.drawString(t2, (float) x - 300, (float) y + 10, Color.cyan.getRGB(), true);
        mc.fontRendererObj.drawString(t4, (float) x - 300, (float) y + 30, Color.cyan.getRGB(), true);
        mc.fontRendererObj.drawString(t5, (float) x - 300, (float) y + 40, Color.cyan.getRGB(), true);
    }

    // ========== ОБРАБОТЧИК ЧАТА ==========
    @SubscribeEvent
    public void chat(ClientChatReceivedEvent e) {
        String message = e.message.getUnformattedText().trim();

        // Обработка упоминаний
        String mentionNickname = "Octo_";
        if (Utils.inGame()) {
            if (message.contains(mentionNickname)) {
                mentions++;
                Utils.sendMessage("You got mentioned in the chat!");
                System.out.println("Bot: Упоминание в чате обнаружено.");
            }
        }

        // Обработка команд бота
        if (message.startsWith("!pitbot")) {
            handleCommand(message);
        }
    }

    /**
     * Обработка команд, отправленных в чат.
     */
    private void handleCommand(String message) {
        String[] parts = message.split(" ");
        if (parts.length < 2) {
            Utils.sendMessage("Invalid command format. Use !pitbot <command> [arguments].");
            return;
        }

        String command = parts[1].toLowerCase();

        switch (command) {
            case "addtarget":
                if (parts.length < 3) {
                    Utils.sendMessage("Usage: !pitbot addtarget <playername>");
                } else {
                    String playerToAdd = parts[2];
                    addTarget(playerToAdd);
                }
                break;

            case "removetarget":
                if (parts.length < 3) {
                    Utils.sendMessage("Usage: !pitbot removetarget <playername>");
                } else {
                    String playerToRemove = parts[2];
                    removeTarget(playerToRemove);
                }
                break;

            case "listtargets":
                listTargets();
                break;

            default:
                Utils.sendMessage("Unknown command. Available commands: addtarget, removetarget, listtargets.");
                break;
        }
    }

    /**
     * Добавляет игрока в список целей.
     */
    private void addTarget(String playername) {
        if (targetList.contains(playername)) {
            Utils.sendMessage("Player \"" + playername + "\" is already in the target list.");
            return;
        }
        targetList.add(playername);
        saveConfig();
        Utils.sendMessage("Player \"" + playername + "\" has been added to the target list.");
        System.out.println("Bot: Добавлен игрок \"" + playername + "\" в targetList.");
    }

    /**
     * Удаляет игрока из списка целей.
     */
    private void removeTarget(String playername) {
        if (!targetList.contains(playername)) {
            Utils.sendMessage("Player \"" + playername + "\" is not in the target list.");
            return;
        }
        targetList.remove(playername);
        saveConfig();
        Utils.sendMessage("Player \"" + playername + "\" has been removed from the target list.");
        System.out.println("Bot: Удалён игрок \"" + playername + "\" из targetList.");
    }

    /**
     * Выводит текущий список целей.
     */
    private void listTargets() {
        if (targetList.isEmpty()) {
            Utils.sendMessage("Target list is currently empty.");
            return;
        }
        String targets = String.join(", ", targetList);
        Utils.sendMessage("Current targets: " + targets);
        System.out.println("Bot: Текущий список целей: " + targets);
    }

    // ========== ИЗМЕНЕНИЕ СЕССИИ (смена ника) ==========
    public void setCustomNickname(String nickname) {
        try {
            Field sessionField = mc.getClass().getDeclaredField("session");
            sessionField.setAccessible(true);
            Session customSession = new Session(nickname, "playerID", "accessToken", "mojang");
            sessionField.set(mc, customSession);
            System.out.println("Bot: Nickname successfully set to: " + nickname);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Bot: Failed to set custom nickname.");
        }
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========

    public void PauseSession() {
        mc.thePlayer.sendChatMessage("/l");
        waitticks = (int) Utils.getRandom(4500, 8000);
        System.out.println("Bot: Session paused with /l command.");
    }

    public void spawncommand() {
        waitticks = (int) Utils.getRandom(640, 800);
        System.out.println("Bot: Preparing to send /spawn command.");

        // Устанавливаем тик, после которого отправить команду /spawn
        int spawnCommandTickLocal = phasetick + (waitticks - 40) / 50;
        this.spawnCommandTick = spawnCommandTickLocal;
    }

    public void rightClick() {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
        System.out.println("Bot: Right click pressed.");

        // Таймер для отпуска кнопки через 100 мс
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                System.out.println("Bot: Right click released.");
            }
        }, 100);
    }

    public void selectSecondHotbarSlot() {
        if (mc.thePlayer != null) {
            mc.thePlayer.inventory.currentItem = 1;
            System.out.println("Bot: Selected the second hotbar slot.");
        } else {
            System.out.println("Bot: Player not found. Cannot select hotbar slot.");
        }
    }

    public void flyUpForSeconds(int seconds) {
        System.out.println("Bot: Flying up for " + seconds + " seconds.");
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
                System.out.println("Bot: Stopped flying up.");
            }
        }, seconds * 1000);
    }

    @SubscribeEvent
    public void onGuiOpen(TickEvent.ClientTickEvent event) {
        // Проверяем, открыт ли GUI сундука
        if (mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiChest) {
            System.out.println("Bot: Chest GUI is open. Preparing to interact...");

            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    clickFirstSlotInChest();
                }
            }, 1000); // Задержка 1 сек
        }
    }

    public void clickFirstSlotInChest() {
        if (mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiChest) {
            net.minecraft.client.gui.inventory.GuiChest chest = (net.minecraft.client.gui.inventory.GuiChest) mc.currentScreen;
            net.minecraft.inventory.Container chestContainer = chest.inventorySlots;

            int totalSlots = chestContainer.inventorySlots.size();
            System.out.println("Bot: Total slots in chest = " + totalSlots);

            int slotIndex = 0; // Первый слот сундука
            try {
                mc.playerController.windowClick(
                        chestContainer.windowId,
                        slotIndex,
                        0,
                        0,
                        mc.thePlayer
                );
                System.out.println("Bot: Clicked on slot " + slotIndex + " in the chest.");
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Bot: Failed to click on slot " + slotIndex);
            }
        } else {
            System.out.println("Bot: No chest GUI is currently open.");
        }
    }

    private void initializeFlightToTarget() {
        isFlyingToTarget = true;
        System.out.println("Bot: Initializing flight to target coordinates.");
    }

    private void startFlightToTarget() {
        if (!isFlyingToTarget) return;

        double playerX = mc.thePlayer.posX;
        double playerY = mc.thePlayer.posY;
        double playerZ = mc.thePlayer.posZ;

        double deltaX = targetX - playerX;
        double deltaZ = targetZ - playerZ;

        double yaw = Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90;

        mc.thePlayer.rotationYaw = (float) yaw;
        mc.thePlayer.rotationPitch = 0.0F;

        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
        System.out.println("Bot: Started flying towards target coordinates.");
    }

    /**
     * Изменённый метод с учётом tolerance —
     * проверяем, что мы в радиусе tolerance блоков от (targetX, targetY, targetZ).
     */
    private boolean isAtTarget() {
        double playerX = mc.thePlayer.posX;
        double playerY = mc.thePlayer.posY;
        double playerZ = mc.thePlayer.posZ;

        double dx = playerX - targetX;
        double dy = playerY - targetY;
        double dz = playerZ - targetZ;
        double distanceSq = dx * dx + dy * dy + dz * dz;

        return distanceSq <= tolerance * tolerance;
    }

    private void stopFlightToTarget() {
        isFlyingToTarget = false;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        System.out.println("Bot: Stopped flying to target coordinates.");
    }
    private void faceCoords(double x, double y, double z) {
        double dx = x - mc.thePlayer.posX;
        double dz = z - mc.thePlayer.posZ;
        float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
        mc.thePlayer.rotationYaw = yaw;
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent e) {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        // Логика после смерти (команды /lobby, /kp1 и т.п.)
        if (hasHandledDeath) {
            if (deathTickCounter > 0) {
                // Пока deathTickCounter > 0, просто ждем
                deathTickCounter--;
            } else {
                switch (commandStep) {
                    case 0:
                        // 1) /lobby
                        mc.thePlayer.sendChatMessage("/lobby");
                        System.out.println("Bot: Отправлена команда '/lobby'. Ожидание 2 секунд.");
                        commandStep = 1;
                        deathTickCounter = 40; // 2 сек
                        return;

                    case 1:
                        // 2) /kp1
                        mc.thePlayer.sendChatMessage("/kp1");
                        System.out.println("Bot: Отправлена команда '/kp1'. Ожидание 2 секунд.");
                        commandStep = 2;
                        deathTickCounter = 40;
                        return;

                    case 2:
                        // (пример) Правый клик по хотбар-слоту
                        rightClick();
                        System.out.println("Bot: Выполнен правый клик по второму слоту хотбара.");
                        commandStep = 3;
                        deathTickCounter = 20; // 1 сек
                        return;

                    case 3:
                        // Ждем, пока откроется сундук
                        if (mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiChest) {
                            System.out.println("Bot: GUI сундука открыт.");
                            commandStep = 4;
                            chestClickDelay = 20; // ждём 1 сек
                        } else {
                            // Если сундук не открылся, подождем еще чуть-чуть
                            deathTickCounter = 5;
                        }
                        return;

                    case 4:
                        // Клик по слоту сундука
                        if (chestClickDelay > 0) {
                            chestClickDelay--;
                        } else {
                            clickFirstSlotInChest();
                            System.out.println("Bot: Выполнен клик по первому слоту сундука.");
                            commandStep = 5;
                            deathTickCounter = 20;
                        }
                        return;

                    case 5:
                        // Выбираем второй слот хотбара + ПКМ (пример)
                        selectSecondHotbarSlot();
                        rightClick();
                        System.out.println("Bot: Подготовка к двойному прыжку (или игнорируйте).");
                        // Если прыжок вам не нужен — закомментируйте следующую часть
                        new Timer().schedule(new TimerTask() {
                            @Override
                            public void run() {
                                KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
                                System.out.println("Bot: Клавиша Jump отпущена.");
                            }
                        }, 50);

                        commandStep = 6;
                        flyTickCounter = 60;
                        return;

                    case 6:
                        // Первый прыжок (если нужно)
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
                        System.out.println("Bot: Выполнен первый прыжок.");

                        new Timer().schedule(new TimerTask() {
                            @Override
                            public void run() {
                                KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
                                System.out.println("Bot: Первый прыжок отпущен.");
                                commandStep = 7;
                            }
                        }, 100);
                        return;

                    case 7:
                        // Второй прыжок (если нужно).
                        // Если уже не нужен "полет", можете убрать.
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
                        System.out.println("Bot: Выполнен второй прыжок (если надо).");

                        new Timer().schedule(new TimerTask() {
                            @Override
                            public void run() {
                                KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
                                System.out.println("Bot: Второй прыжок отпущен. Переходим к бегу по таймеру.");
                                // Вот тут мы переходим к case 8,
                                // где начнется 30-секундный бег до (108,70,4)
                                commandStep = 8;
                            }
                        }, 500);
                        return;

                    // ========== НОВЫЕ ШАГИ ДЛЯ БЕГА ПО ВРЕМЕНИ ==========

                    case 8:
                        // 8) Бежим 30 секунд к точке (108,70,4)
                        System.out.println("Bot: Начинаем 30-секундный бег к (108,70,4).");
                        // Активируем "вперёд" и "спринт"
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);

                        // Поворачиваемся лицом к (108,70,4)
                        faceCoords(108, 70, 4);

                        // Запускаем простой таймер на 30 секунд (600 тиков)
                        deathTickCounter = 600; // 30 сек
                        commandStep = 9;
                        return;

                    case 9:
                        // Пока deathTickCounter > 0, мы в if (deathTickCounter > 0) НЕ попадем,
                        // но при следующем тике мы опять увидим deathTickCounter-- и вернемся сюда,
                        // пока он не станет 0.
                        // Когда стало 0 => идем сюда (case 9) еще раз, но deathTickCounter = 0.

                        // Если мы дошли сюда с deathTickCounter=0 => значит 30 секунд истекли
                        System.out.println("Bot: 30 секунд бега завершены. Теперь 20 секунд к (140,72,46).");
                        faceCoords(140, 72, 46);
                        // По-прежнему держим вперёд+спринт или заново включаем
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);

                        // Запускаем на 20 секунд (400 тиков)
                        deathTickCounter = 400;
                        commandStep = 10;
                        return;

                    case 10:
                        // Аналогично: пока deathTickCounter > 0, ждем.
                        // Когда дойдет до 0 => значит 20 секунд отбегали.
                        System.out.println("Bot: Добежали вторые 20 секунд, переходим в фазу FIGHTING.");

                        // Останавливаем движение
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);

                        // Переходим в бой
                        setPhase(Phase.FIGHTING);
                        hasHandledDeath = false;
                        commandStep = 0;
                        return;

                    default:
                        break;
                }

                // ВАЖНО: если вы дошли сюда, значит ни один case не сработал,
                // но hasHandledDeath всё ещё true. Можно сбросить или что-то ещё.
            }
        }

        // ----- Основная логика бота, если нет death-обработки -----
        if (!hasHandledDeath) {
            if (getPhase() == Phase.WALKING) {
                walking();
            } else if (getPhase() == Phase.FALLING) {
                falling();
            } else if (getPhase() == Phase.FIGHTING) {
                fighting(e);
            }
        }

        if (e.phase == TickEvent.Phase.END) {
            onUpdate();
        }

        phasetick++;
    }


    public Phase getPhase() {
        return this.phase;
    }

    @SubscribeEvent
    public void onChatMessage(ClientChatReceivedEvent event) {
        if (event.message == null) return;

        String chatMessage = event.message.getUnformattedText();
        String botNickname = "zabivala2015"; // Ник бота

        // Проверяем наличие «ключевых фраз» смерти
        if (chatMessage.contains(botNickname) && (
                chatMessage.matches(".*убил.*") ||
                        chatMessage.matches(".*жестоко убит.*") ||
                        chatMessage.matches(".*не мог сопротивляться убийству.*") ||
                        chatMessage.matches(".*был убит.*") ||
                        chatMessage.matches(".*был убит игроком.*") ||
                        chatMessage.matches(".*был зверски убит.*") ||
                        chatMessage.matches(".*Вы кикнуты с сервера!.*") ||
                        chatMessage.matches(".*умер от руки игроком.*") ||
                        chatMessage.matches(".*был разорван на кусочки игроком.*")
        )) {
            if (!hasHandledDeath) {
                hasHandledDeath = true;
                deathTickCounter = 40; // Ожидание 2 сек
                System.out.println("Bot: Death detected via chat message. Waiting 2 seconds before sending '/lobby'.");
            }
        }
    }

    /**
     * Изменённый метод setPhase с учётом флага isInFightingMode,
     * чтобы не сбрасывать фазу, если уже в бою.
     */
    public void setPhase(Phase p) {
        if (isInFightingMode && p != Phase.FIGHTING) {
            System.out.println("Bot: Фаза не изменена. Бот уже в режиме FIGHTING.");
            return;
        }
        this.phase = p;
        phasetick = 0;
        System.out.println("Bot: Фаза установлена на " + p.toString());
        if (p == Phase.FIGHTING) {
            isInFightingMode = true;
        }
    }

    public void stopwalking() {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
        System.out.println("Bot: Остановлены все движения.");
    }

    public void startwalking() {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
        System.out.println("Bot: Начато движение вперед и спринт.");
    }

    public boolean iswalking() {
        return mc.gameSettings.keyBindForward.isKeyDown();
    }

    public void onUpdate() {
        screenshotticks++;
        if (screenshotticks > 1200) {
            Utils.takeMinecraftScreenshot();
            Utils.sendMessage("Screenshot taken!");
            System.out.println("Bot: Скриншот сделан.");
            screenshotticks = 0;
        }
        if (Utils.minutes((int) ((System.currentTimeMillis() - session) / 1000)) >= currectsession) {
            currectsession += sessionstop;
            System.out.println("Bot: Текущая сессия обновлена.");
        }
    }

    // ========== КОНФИГ ФАЙЛ ==========
    public void saveConfig() {
        Properties props = new Properties();
        props.setProperty("Aim_reach", String.valueOf(reach));
        props.setProperty("Attack_reach", String.valueOf(attackreach));
        props.setProperty("Aim_smoothness_(yaw,pitch)", Arrays.toString(smoothness));
        props.setProperty("Jump_chance_(when_fighting)", String.valueOf(jumpchance));
        props.setProperty("Strafe_chance_(when_fighting)", String.valueOf(strafechance));
        props.setProperty("Attack_cps", String.valueOf(cps));
        props.setProperty("Reset_sprint_every_(ticks)", String.valueOf(sprintresticks));
        props.setProperty("Waiting_phase_chance_after_respawning", String.valueOf(waitchance));
        props.setProperty("Minimal_new_target_distance_advantage_compared_blah_blah", String.valueOf(switchtargetrange));
        props.setProperty("Skip_aiming_when_target_in_range", String.valueOf(reachskip));
        props.setProperty("Stop_session_every_(minutes)", String.valueOf(sessionstop));
        props.setProperty("Quit_when_encountered_issue_(or_staff_check)_instead_of_stopping_session", String.valueOf(quitbot));
        props.setProperty("Max_ticks_collided_horizontally", String.valueOf(maxtickscolided));
        props.setProperty("Soup_health_threshold", String.valueOf(soupHealthThreshold));

        // Сохраняем список целей
        props.setProperty("Target_List", String.join(",", targetList));

        try (OutputStream out = Files.newOutputStream(Paths.get("pitbotconf"))) {
            props.store(out, "Pit Bot Config");
            System.out.println("Bot: Конфигурация сохранена.");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Bot: Ошибка при сохранении конфигурации.");
        }
    }

    public void loadConfig() {
        Properties props = new Properties();
        if (!Files.exists(Paths.get("pitbotconf"))) {

            targetList.addAll(Arrays.asList("heal", "Mediogryz", "Anyone__", "Lostcelebration",
                    "renessansez", "OpiumNight_Lol" , "Moonckler", "MyNameCombo", "TTsRap",
                    "Kowko_devo4ka_09", "syleis", "Nemo_fol", "login666" , "NMxbypased","_PVPewer_","110xNoob","Eksen_969", "KSOIN123" ,"K4czk4_"  ,"TriologyUNIX"));
            saveConfig();
            System.out.println("Bot: Конфигурационный файл не найден. Создан новый по умолчанию.");
            return;
        }

        try (InputStream in = Files.newInputStream(Paths.get("pitbotconf"))) {
            props.load(in);
            System.out.println("Bot: Конфигурация загружена.");
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Bot: Ошибка при загрузке конфигурации.");
        }


        soupHealthThreshold = Double.parseDouble(props.getProperty("Soup_health_threshold", "8.0"));
        reach = Double.parseDouble(props.getProperty("Aim_reach", "25.0"));
        attackreach = Double.parseDouble(props.getProperty("Attack_reach", "4.0"));

        String smoothnessStr = props.getProperty("Aim_smoothness_(yaw,pitch)", "[7.9,9.0]");
        smoothness = Arrays.stream(
                        smoothnessStr.substring(1, smoothnessStr.length() - 1).split(","))
                .mapToDouble(Double::parseDouble)
                .toArray();

        jumpchance = Double.parseDouble(props.getProperty("Jump_chance_(when_fighting)", "0.02"));
        strafechance = Double.parseDouble(props.getProperty("Strafe_chance_(when_fighting)", "0.02"));
        cps = Double.parseDouble(props.getProperty("Attack_cps", "15.0"));
        sprintresticks = Integer.parseInt(props.getProperty("Reset_sprint_every_(ticks)", "50"));
        waitchance = Double.parseDouble(props.getProperty("Waiting_phase_chance_after_respawning", "0.3"));
        switchtargetrange = Double.parseDouble(props.getProperty("Minimal_new_target_distance_advantage_compared_blah_blah", "1.0"));
        reachskip = Double.parseDouble(props.getProperty("Skip_aiming_when_target_in_range", "1.0"));
        sessionstop = Integer.parseInt(props.getProperty("Stop_session_every_(minutes)", "20"));
        quitbot = Boolean.parseBoolean(props.getProperty("Quit_when_encountered_issue_(or_staff_check)_instead_of_stopping_session", "false"));
        maxtickscolided = Integer.parseInt(props.getProperty("Max_ticks_collided_horizontally", "160"));


        String targets = props.getProperty("Target_List", "");
        if (!targets.isEmpty()) {
            targetList = Arrays.stream(targets.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            System.out.println("Bot: Список целей загружен: " + targetList);
        } else {
            System.out.println("Bot: Список целей пуст. Используются значения по умолчанию.");
        }
    }

    // ========== ВЫБОР ЦЕЛИ ИЗ СПИСКА ==========
    public static EntityPlayer GetTarget(List<String> targetList, double reach, double attackReach) {
        double botPosY = mc.thePlayer.posY;
        return mc.theWorld.playerEntities.stream()
                .filter(entity -> {
                    if (entity == mc.thePlayer) return false;
                    if (!targetList.contains(entity.getName())) return false;
                    if (entity.isDead) return false;
                    if (entity.isInvisible()) return false;
                    if (mc.thePlayer.getDistanceToEntity(entity) > reach) return false;

                    int botBlockY = (int) Math.floor(botPosY);
                    int entityBlockY = (int) Math.floor(entity.posY);

                    if (entityBlockY < botBlockY - 2) {
                        System.out.println("Bot: Игнорируется " + entity.getName()
                                + " из-за низкой позиции. entityBlockY = " + entityBlockY
                                + ", botBlockY = " + botBlockY);
                        return false;
                    }
                    return true;
                })
                .min(Comparator.comparingDouble(entity -> mc.thePlayer.getDistanceToEntity(entity)))
                .orElse(null);
    }
}
