package betterpit;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemSoup;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class AutoSoupMod {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int SOUP_HEALTH_THRESHOLD = 10; // Порог здоровья для использования супа
    private static final int HOTBAR_SIZE = 9;
    private static final int INVENTORY_SIZE = 36;
    private boolean refilling = false;
    private boolean serverOpenInventory = false;

    // Флаги для управления выбросом миски
    private boolean shouldDropBowl = false;
    private int bowlSlot = -1;

    // Таймер задержки для нажатия клавиши Q (в тиках)
    private int dropBowlDelay = 0;
    private static final int DROP_BOWL_DELAY_TICKS = 0; // Задержка 0 тиков

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            autoSoup();
        }

        if (event.phase == TickEvent.Phase.END) {
            // Обработка выброса миски с учётом задержки
            if (shouldDropBowl) {
                if (dropBowlDelay > 0) {
                    dropBowlDelay--;
                    System.out.println("AutoSoupMod: Задержка перед выбросом миски. Осталось тиков: " + dropBowlDelay);
                } else {
                    dropBowl();
                    shouldDropBowl = false;
                }
            }
        }
    }

    /**
     * Основной метод для автоматического использования супа.
     */
    private void autoSoup() {
        if (!inGame() || mc.thePlayer.isDead || refilling) {
            return;
        }

        // Проверяем текущее здоровье
        if (mc.thePlayer.getHealth() < SOUP_HEALTH_THRESHOLD) {
            if (hasSoupInHotbar()) {
                useSoup();
            } else if (isHotbarNotFull()) {
                moveSoupToHotbar();
            }
        }
    }

    /**
     * Проверяет наличие супа в горячей панели.
     */
    private boolean hasSoupInHotbar() {
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            ItemStack itemStack = mc.thePlayer.inventory.getStackInSlot(i);
            if (itemStack != null && itemStack.getItem() instanceof ItemSoup) {
                System.out.println("AutoSoupMod: Суп найден в горячей панели слота " + i + ".");
                return true;
            }
        }
        System.out.println("AutoSoupMod: Суп не найден в горячей панели.");
        return false;
    }

    /**
     * Проверяет, не заполнена ли горячая панель полностью супами.
     */
    private boolean isHotbarNotFull() {
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            ItemStack itemStack = mc.thePlayer.inventory.getStackInSlot(i);
            if (itemStack == null || !(itemStack.getItem() instanceof ItemSoup)) {
                System.out.println("AutoSoupMod: Найден свободный слот в горячей панели слота " + i + ".");
                return true;
            }
        }
        System.out.println("AutoSoupMod: Горячая панель полностью заполнена супами.");
        return false;
    }

    /**
     * Использует суп из горячей панели и устанавливает флаг для выброса миски.
     */
    private void useSoup() {
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            ItemStack itemStack = mc.thePlayer.inventory.getStackInSlot(i);
            if (itemStack != null && itemStack.getItem() instanceof ItemSoup) {
                // Устанавливаем текущий слот
                mc.thePlayer.inventory.currentItem = i;
                System.out.println("AutoSoupMod: Установлен текущий слот на " + i + " для использования супа.");

                // Используем суп
                mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, itemStack);
                System.out.println("AutoSoupMod: Использован суп из слота " + i + ".");

                // Устанавливаем флаг для выброса миски и запоминаем слот
                shouldDropBowl = true;
                bowlSlot = i;
                System.out.println("AutoSoupMod: Установлен флаг для выброса миски.");

                // Устанавливаем задержку перед выбросом миски
                dropBowlDelay = DROP_BOWL_DELAY_TICKS;
                System.out.println("AutoSoupMod: Установлена задержка перед выбросом миски на " + DROP_BOWL_DELAY_TICKS + " тиков.");

                // Сбрасываем флаг рефиллинга
                refilling = false;

                // Если задержка равна нулю, сразу выбрасываем миску
                if (dropBowlDelay <= 0) {
                    dropBowl();
                    shouldDropBowl = false;
                }

                break;
            }
        }
    }

    /**
     * Симулирует выброс миски из текущего слота.
     */
    private void dropBowl() {
        System.out.println("AutoSoupMod: Попытка выбросить миску из слота " + bowlSlot + ".");
        if (bowlSlot != -1) {
            // Устанавливаем текущий слот на слот с миской
            mc.thePlayer.inventory.currentItem = bowlSlot % HOTBAR_SIZE;
            System.out.println("AutoSoupMod: Установлен текущий слот на " + (bowlSlot % HOTBAR_SIZE) + " для выброса миски.");

            // Используем метод dropOneItem для выброса миски
            mc.thePlayer.dropOneItem(true); // true для выброса всего стека

            System.out.println("AutoSoupMod: Вызван метод dropOneItem(true) для выброса миски.");
        } else {
            System.out.println("AutoSoupMod: Нет слота для выброса миски.");
        }
    }

    /**
     * Находит слот с пустой миской в инвентаре.
     *
     * @return номер слота с миской или -1, если миска не найдена.
     */
    private int findEmptyBowlSlot() {
        System.out.println("AutoSoupMod: Поиск слота с миской.");
        // Ищем миску в горячей панели сначала
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            ItemStack itemStack = mc.thePlayer.inventory.getStackInSlot(i);
            if (itemStack != null && itemStack.getItem() == Items.bowl) {
                System.out.println("AutoSoupMod: Миска найдена в горячей панели слота " + i + ".");
                return i;
            }
        }

        // Если не нашли в горячей панели, ищем в остальной части инвентаря
        for (int i = HOTBAR_SIZE; i < INVENTORY_SIZE; i++) {
            ItemStack itemStack = mc.thePlayer.inventory.getStackInSlot(i);
            if (itemStack != null && itemStack.getItem() == Items.bowl) {
                System.out.println("AutoSoupMod: Миска найдена в инвентаре слота " + i + ".");
                return i;
            }
        }

        System.out.println("AutoSoupMod: Миска не найдена в инвентаре.");
        return -1; // Миска не найдена
    }

    /**
     * Перемещает суп из инвентаря в горячую панель.
     */
    private void moveSoupToHotbar() {
        refilling = true;
        System.out.println("AutoSoupMod: Перемещение супов в горячую панель.");

        mc.addScheduledTask(() -> {
            setServerOpenInventory(true);
            int soupsMoved = 0; // Счётчик перемещённых супов

            for (int i = HOTBAR_SIZE; i < INVENTORY_SIZE; i++) {
                ItemStack itemStack = mc.thePlayer.inventory.getStackInSlot(i);

                if (itemStack != null && itemStack.getItem() instanceof ItemSoup) {
                    for (int j = 0; j < HOTBAR_SIZE; j++) {
                        if (mc.thePlayer.inventory.getStackInSlot(j) == null) {
                            // Перемещаем суп в горячую панель
                            mc.playerController.windowClick(0, i, 0, 1, mc.thePlayer);
                            soupsMoved++;
                            System.out.println("AutoSoupMod: Перемещен суп из слота " + i + " в горячую панель слота " + j + ".");

                            // Прерываем внутренний цикл, чтобы переместить следующий суп
                            break;
                        }
                    }
                }

                // Останавливаем цикл, если переместили 7 супов
                if (soupsMoved >= 7) {
                    System.out.println("AutoSoupMod: Перемещено 7 супов. Операция завершена.");
                    break;
                }
            }

            if (soupsMoved == 0) {
                System.out.println("AutoSoupMod: Супы в инвентаре закончились!");
            }

            setServerOpenInventory(false);
            refilling = false;
        });
    }


    /**
     * Управляет открытием и закрытием инвентаря на сервере.
     *
     * @param value true для открытия, false для закрытия.
     */
    private void setServerOpenInventory(boolean value) {
        if (value != serverOpenInventory) {
            if (value) {
                mc.thePlayer.sendQueue.addToSendQueue(new C16PacketClientStatus(C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT));
                System.out.println("AutoSoupMod: Инвентарь открыт.");
            } else {
                mc.thePlayer.sendQueue.addToSendQueue(new C0DPacketCloseWindow(mc.thePlayer.openContainer.windowId));
                System.out.println("AutoSoupMod: Инвентарь закрыт.");
            }
            serverOpenInventory = value;
        }
    }

    /**
     * Проверяет, находится ли игрок в игре.
     *
     * @return true, если игрок в игре, иначе false.
     */
    private boolean inGame() {
        return mc.theWorld != null && mc.thePlayer != null;
    }

    /**
     * Возвращает состояние рефиллинга.
     *
     * @return true, если мод находится в процессе рефиллинга, иначе false.
     */
    public boolean isRefilling() {
        return refilling;
    }
}
