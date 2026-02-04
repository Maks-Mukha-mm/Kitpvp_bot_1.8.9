package betterpit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.Display;

import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static betterpit.PitBotMod.mc;

public class Utils {
    public static boolean inGame() {
        return mc.thePlayer != null && mc.theWorld != null;
    }

    public static void sendMessage(String message) {
        if (inGame()) {
            mc.thePlayer.addChatMessage(new ChatComponentText("§c[PB] - §8" + message));
        }
    }

    public static float[] getAimRotations(BlockPos blockPos) {
        if (blockPos != null && mc.theWorld != null && mc.thePlayer != null) {
            double xDiff = blockPos.getX() + 0.5 - mc.thePlayer.posX;
            double yDiff = blockPos.getY() + 0.5 - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();
            double zDiff = blockPos.getZ() + 0.5 - mc.thePlayer.posZ;
            float yaw = (float) Math.toDegrees(Math.atan2(zDiff, xDiff)) - 90;
            float pitch = (float) -Math.toDegrees(Math.atan2(yDiff, Math.sqrt(xDiff * xDiff + zDiff * zDiff)));

            return new float[]{yaw, pitch};
        }
        return null;
    }

    public static EntityPlayer GetTarget(List<String> targetList, double reach, double attackReach) {
        return mc.theWorld.playerEntities.stream()
                .filter(entity ->
                        entity != mc.thePlayer
                                && targetList.contains(entity.getName())
                                && !entity.isDead
                                && !entity.isInvisible()
                                && mc.thePlayer.getDistanceToEntity(entity) <= reach
                )
                .min(Comparator.comparingDouble(entity -> mc.thePlayer.getDistanceToEntity(entity)))
                .orElse(null);
    }

    public static boolean hasDiamondChestplate(EntityPlayer player) {
        for (ItemStack itemStack : player.inventory.armorInventory) {
            if (itemStack != null && itemStack.getItem() instanceof ItemArmor) {
                ItemArmor armor = (ItemArmor) itemStack.getItem();
                if (armor.getArmorMaterial() == ItemArmor.ArmorMaterial.DIAMOND && armor.armorType == 1) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void aim(EntityPlayer target, double[] smoothness) {
        if (target == null) {
            return;
        }

        float[] targetRotations = getTargetRotations(target);
        float[] smoothedRotations = smoothRotations(targetRotations, smoothness[0] / 10, smoothness[1] / 10);

        float[] fixedRotations = getFixedRotation(smoothedRotations, new float[]{mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch});

        mc.thePlayer.rotationYaw = fixedRotations[0];
        mc.thePlayer.rotationPitch = fixedRotations[1];
    }

    public static float[] getTargetRotations(Entity entity) {
        double diffX = entity.posX - mc.thePlayer.posX;
        double diffY;
        if (entity instanceof EntityLivingBase) {
            EntityLivingBase en = (EntityLivingBase) entity;
            diffY = en.posY + (double) en.getEyeHeight() * 0.71D - (mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight());
        } else {
            diffY = (entity.getEntityBoundingBox().minY + entity.getEntityBoundingBox().maxY) / 2.0D - (mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight());
        }

        double diffZ = entity.posZ - mc.thePlayer.posZ;
        double dist = MathHelper.sqrt_double(diffX * diffX + diffZ * diffZ);
        float yaw = (float) (Math.atan2(diffZ, diffX) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(diffY, dist) * 180.0D / Math.PI));
        float adjustedPitch = pitch + 11;

        return new float[]{yaw, adjustedPitch};
    }

    public static float[] smoothRotations(float[] rotations, double smoothYaw, double smoothPitch) {
        float angleDifference = angledifference(rotations[0], mc.thePlayer.rotationYaw);
        float angleDifference2 = angledifference(rotations[1], mc.thePlayer.rotationPitch);

        double yawCalculation = (1 - smoothYaw) * (1.0 + 0.1 * Math.abs(angleDifference) / 180.0);
        double pitchCalculation = (1 - smoothPitch) * (1.0 + 0.1 * Math.abs(angleDifference2) / 180.0);

        return new float[]{
                (float) (mc.thePlayer.rotationYaw + MathHelper.wrapAngleTo180_float(rotations[0] - mc.thePlayer.rotationYaw) * yawCalculation),
                (float) (mc.thePlayer.rotationPitch + MathHelper.wrapAngleTo180_float(rotations[1] - mc.thePlayer.rotationPitch) * pitchCalculation)
        };
    }

    public static float angledifference(float rotation1, float rotation2) {
        return MathHelper.wrapAngleTo180_float(rotation1 - rotation2);
    }

    public static float[] getFixedRotation(final float[] rotations, final float[] lastRotations) {
        final float yaw = rotations[0];
        final float pitch = rotations[1];

        final float lastYaw = lastRotations[0];
        final float lastPitch = lastRotations[1];

        final float f = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        final float gcd = f * f * f * 1.2F;

        final float deltaYaw = yaw - lastYaw;
        final float deltaPitch = pitch - lastPitch;

        final float fixedDeltaYaw = deltaYaw - (deltaYaw % gcd);
        final float fixedDeltaPitch = deltaPitch - (deltaPitch % gcd);

        final float fixedYaw = lastYaw + fixedDeltaYaw;
        final float fixedPitch = lastPitch + fixedDeltaPitch;

        return new float[]{fixedYaw, fixedPitch};
    }

    public static boolean isGoldenApple(ItemStack is) {
        return is.getItem().equals(Item.getByNameOrId("golden_apple"));
    }

    public static boolean isGoldenHead(ItemStack is) {
        return is.getItem().equals(Item.getByNameOrId("skull"));
    }

    public static boolean isBakedPotato(ItemStack is) {
        return is.getItem().equals(Item.getByNameOrId("baked_potato"));
    }

    static double getRandom(double minValue, double maxValue) {
        Random random = new Random();
        return minValue + (maxValue - minValue) * random.nextDouble();
    }

    public static String formatTime(int seconds) {
        int minutes = minutes(seconds);
        int remainingSeconds = seconds % 60;
        return minutes + (remainingSeconds < 10 ? ":0" + remainingSeconds : ":" + remainingSeconds);
    }

    public static int minutes(int seconds) {
        return seconds / 60;
    }

    public static void takeMinecraftScreenshot() {
        try {
            int screenWidth = Display.getWidth();
            int screenHeight = Display.getHeight();

            ScreenShotHelper.saveScreenshot(null, null, screenWidth, screenHeight, mc.getFramebuffer());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
