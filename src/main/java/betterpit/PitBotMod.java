package betterpit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.util.Session;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;

@Mod(modid = "betterpit", version = "1.0")
public class PitBotMod {
    public static Bot bot;
    public static FakeGui Gui = new FakeGui();
    public static Minecraft mc = Minecraft.getMinecraft();
    public static boolean botenabled = false;

    @EventHandler
    public void init(FMLInitializationEvent event) {
        setCustomSession(); // Устанавливаем пользовательскую сессию
        MinecraftForge.EVENT_BUS.register(this);
    }


    private void setCustomSession() {
        try {
            Session customSession = new Session("zabivala2015", "fakePlayerID", "fakeAccessToken", "mojang");
            Field sessionField = Minecraft.class.getDeclaredField("session");
            sessionField.setAccessible(true); // Делаем поле доступным для изменения
            sessionField.set(mc, customSession);
            System.out.println("Custom session set successfully with nickname: ziga2281337666");
        } catch (Exception e) {
            System.err.println("Failed to set custom session: " + e.getMessage());
        }
    }

    /**
     * Обрабатывает нажатия клавиш для включения/выключения бота.
     */
    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        if (Utils.inGame()) {
            if (Keyboard.getEventKey() == Keyboard.KEY_H && Keyboard.getEventKeyState()) {
                toggleBot(!botenabled);
            }
        }
    }

    /**
     * Включает или выключает бота.
     *
     * @param enable true, если бот должен быть включён; false, если выключен.
     */
    public static void toggleBot(boolean enable) {
        if (enable) {
            bot = new Bot();
            MinecraftForge.EVENT_BUS.register(bot);
            // Открываем GUI для бота
            mc.displayGuiScreen(Gui);
            System.out.println("Bot enabled.");
        } else {
            if (bot != null) {
                bot.stopwalking();
                MinecraftForge.EVENT_BUS.unregister(bot);
                bot = null;
                System.out.println("Bot disabled.");
            }
            // Закрываем GUI
            mc.displayGuiScreen(null);
        }
        botenabled = enable;
        Utils.sendMessage("Bot toggled " + (enable ? "ON" : "OFF"));
    }
}
