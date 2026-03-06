package net.uhhemi.maceilelocked;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.uhhemi.maceilelocked.config.ModConfig;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MaceileLockedClient implements ClientModInitializer {

    // Note block pitches: close=highest (2.0), mid=medium (1.0), far=low (0.25)
    private static final net.minecraft.registry.entry.RegistryEntry<SoundEvent> SOUND_NOTE = SoundEvents.BLOCK_NOTE_BLOCK_PLING;

    // Bell sound for FOV circle pings
    private static final net.minecraft.registry.entry.RegistryEntry<SoundEvent> SOUND_FOV_PING = SoundEvents.BLOCK_NOTE_BLOCK_BELL;

    // ...existing code...

    private static final List<ScreenBox> boxesToRender = new ArrayList<>();
    private static final Map<UUID, Integer> playerCooldowns = new HashMap<>();
    private static final Map<UUID, Integer> fovPingCooldowns = new HashMap<>(); // Separate tracking for FOV pings
    private static final Map<UUID, Float> playerShieldDurability = new HashMap<>();
    private static int globalPingCooldown = 0; // Global cooldown for all pings
    private static final Matrix4f projView = new Matrix4f();
    private static final Vector4f clipPos = new Vector4f();
    private static final Vector3f forward = new Vector3f(0, 0, -1);

    /** Builds combined projection * view so it matches the game's camera and aspect ratio. */
    private static void buildProjViewMatrix(WorldRenderContext context, Matrix4f out) {
        MinecraftClient client = MinecraftClient.getInstance();
        int fbWidth = client.getWindow().getFramebufferWidth();
        int fbHeight = client.getWindow().getFramebufferHeight();
        if (fbWidth <= 0 || fbHeight <= 0) return;

        float fovDegrees = (float) client.options.getFov().getValue().doubleValue();
        float fovRad = (float) Math.toRadians(fovDegrees);
        float aspect = (float) fbWidth / fbHeight;
        Matrix4f projection = new Matrix4f().perspective(fovRad, aspect, 0.05f, 1000f);

        Camera camera = client.gameRenderer.getCamera();
        Vec3d eye = camera.getCameraPos();
        forward.set(0, 0, -1);
        camera.getRotation().transform(forward);
        float cx = (float) (eye.x + forward.x);
        float cy = (float) (eye.y + forward.y);
        float cz = (float) (eye.z + forward.z);
        Matrix4f view = new Matrix4f().lookAt(
                (float) eye.x, (float) eye.y, (float) eye.z,
                cx, cy, cz,
                0, 1, 0
        );
        out.set(projection).mul(view);
    }

    @Override
    public void onInitializeClient() {
        System.out.println("MaceileLocked Client Started");
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;
            // Show boxes during elytra flight or if alwaysShowBoxes is enabled
            boolean elytraActive = client.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)
                    && !client.player.isOnGround();
            if (!elytraActive && !ModConfig.alwaysShowBoxes) {
                boxesToRender.clear();
                return;
            }
            boxesToRender.clear();

            buildProjViewMatrix(context, projView);
            int scaledWidth = client.getWindow().getScaledWidth();
            int scaledHeight = client.getWindow().getScaledHeight();

            for (var player : client.world.getPlayers()) {
                if (player == client.player) continue;
                projectEntityToScreen(player, projView, scaledWidth, scaledHeight);
            }
        });
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            boolean elytraActive = client.player != null
                    && client.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)
                    && !client.player.isOnGround();
            if (!elytraActive && !ModConfig.alwaysShowBoxes) {
                boxesToRender.clear();
                return;
            }

            // Only show FOV circle and process FOV pings in first-person perspective
            boolean isFirstPerson = client.options.getPerspective().isFirstPerson();

            // Get screen center (camera center)
            int screenCenterX = drawContext.getScaledWindowWidth() / 2;
            int screenCenterY = drawContext.getScaledWindowHeight() / 2;

            // Calculate FOV circle radius in pixels
            float fovDegrees = (float) client.options.getFov().getValue().doubleValue();
            int screenHeight = drawContext.getScaledWindowHeight();
            float fovRadiusPixels = (ModConfig.fovCircleDegrees / fovDegrees) * (screenHeight / 2f);

            // Decrement global ping cooldown
            if (globalPingCooldown > 0) {
                globalPingCooldown--;
            }

            // Decrement all FOV ping cooldowns
            for (UUID uuid : new ArrayList<>(fovPingCooldowns.keySet())) {
                int cooldown = fovPingCooldowns.get(uuid);
                if (cooldown > 0) {
                    fovPingCooldowns.put(uuid, cooldown - 1);
                } else {
                    fovPingCooldowns.remove(uuid);
                }
            }

            // Update smoothing and render all boxes
            for (ScreenBox box : boxesToRender) {
                box.updateSmoothing(tickCounter);

                // Check if box is within FOV circle (only in first-person)
                int boxX = box.getX();
                int boxY = box.getY();
                boolean inFovCircle = false;
                if (isFirstPerson) {
                    float distToCenter = (float) Math.sqrt(
                            Math.pow(boxX + box.w / 2 - screenCenterX, 2) +
                                    Math.pow(boxY + box.h / 2 - screenCenterY, 2)
                    );
                    inFovCircle = distToCenter <= fovRadiusPixels;
                    box.setInFovCircle(inFovCircle);

                    // Play ping sound when box enters FOV circle and cooldown is ready (first-person only)
                    if (inFovCircle && !box.wasInFovCircle && box.playerUUID != null) {
                        int cooldown = fovPingCooldowns.getOrDefault(box.playerUUID, 0);
                        if (cooldown <= 0) {
                            if (client.player != null) {
                                client.player.playSound(SOUND_FOV_PING.value(), 1f, ModConfig.pitchClose);
                            }
                            fovPingCooldowns.put(box.playerUUID, ModConfig.fovCirclePingCooldown);
                        }
                    }
                    box.wasInFovCircle = inFovCircle;
                } else {
                    // Not in first-person, reset FOV circle state
                    box.setInFovCircle(false);
                    box.wasInFovCircle = false;
                }

                // Render with appropriate color based on FOV circle
                if (inFovCircle) {
                    renderBoxWithColor(drawContext, boxX, boxY, box.w, box.h, ModConfig.boxColorFillGreen, ModConfig.boxColorBorderGreen); // Green when in FOV circle
                } else {
                    renderBoxWithColor(drawContext, boxX, boxY, box.w, box.h, ModConfig.boxColorFillRed, ModConfig.boxColorBorderRed);
                }

                // Render side bar (health or shield) on the left side of the box
                if (ModConfig.sideBarMode == ModConfig.SideBarMode.HEALTH) {
                    // Render health bar
                    int barX = boxX - ModConfig.sideBarWidth - 2; // 2 pixels to the left of the box
                    int barY = boxY;
                    int barHeight = box.h;

                    // Draw background (empty health bar)
                    drawContext.fill(barX, barY, barX + ModConfig.sideBarWidth, barY + barHeight, ModConfig.healthBarEmptyColor);

                    // Draw filled portion based on health (0-maxHealth)
                    if (box.maxHealth > 0) {
                        int filledHeight = (int) (barHeight * (box.health / box.maxHealth));
                        if (filledHeight > 0) {
                            drawContext.fill(barX, barY + barHeight - filledHeight, barX + ModConfig.sideBarWidth, barY + barHeight, ModConfig.healthBarColor);
                        }
                    }
                } else if (ModConfig.sideBarMode == ModConfig.SideBarMode.SHIELD) {
                    // Render shield bar
                    int barX = boxX - ModConfig.sideBarWidth - 2; // 2 pixels to the left of the box
                    int barY = boxY;
                    int barHeight = box.h;

                    // Draw background (empty shield bar)
                    drawContext.fill(barX, barY, barX + ModConfig.sideBarWidth, barY + barHeight, ModConfig.shieldBarEmptyColor);

                    // Draw filled portion based on stored shield durability (0-20 absorption)
                    if (box.storedShieldDurability > 0) {
                        int filledHeight = (int) (barHeight * (box.storedShieldDurability / 20f));
                        if (filledHeight > 0) {
                            drawContext.fill(barX, barY + barHeight - filledHeight, barX + ModConfig.sideBarWidth, barY + barHeight, ModConfig.shieldBarColor);
                        }
                    }
                }

                // Render player name above the box
                if (ModConfig.showPlayerNames && client != null && client.textRenderer != null) {
                    int nameX = boxX + box.w / 2;
                    int nameY = boxY + ModConfig.playerNameYOffset;
                    int textWidth = client.textRenderer.getWidth(box.playerName);
                    drawContext.drawText(client.textRenderer, box.playerName, nameX - textWidth / 2, nameY, ModConfig.playerNameColor, true);
                }
            }

            // Draw FOV circle only in first-person if enabled in config
            if (isFirstPerson && ModConfig.drawFovCircle) {
                boolean hasTargetsInFov = boxesToRender.stream().anyMatch(box -> box.inFovCircle);
                drawFovCircle(drawContext, screenCenterX, screenCenterY, (int) fovRadiusPixels, hasTargetsInFov);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> tickLockedMessages(client));
    }

    /** If another player is in elytra and looking roughly at the local player, play close/mid/far note by distance. */
    private static void tickLockedMessages(MinecraftClient client) {
        if (client.world == null || client.player == null) return;

        Vec3d localPos = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
        double cosThreshold = Math.cos(Math.toRadians(ModConfig.lockedLookAngleDeg));
        for (PlayerEntity other : client.world.getPlayers()) {
            if (other == client.player) continue;
            if (!isElytraActive(other)) continue;
            Vec3d look = other.getRotationVec(1f);
            Vec3d otherPos = new Vec3d(other.getX(), other.getY(), other.getZ());
            Vec3d toLocal = localPos.subtract(otherPos).normalize();
            if (look.dotProduct(toLocal) <= cosThreshold) continue;

            double dist = localPos.distanceTo(otherPos);

            // Get or create a cooldown tracker for this player entity
            if (!playerCooldowns.containsKey(other.getUuid())) {
                playerCooldowns.put(other.getUuid(), 0);
            }

            int currentCooldown = playerCooldowns.get(other.getUuid());
            if (currentCooldown <= 0) {
                float pitch = dist <= ModConfig.closeDist ? ModConfig.pitchClose
                        : dist <= ModConfig.midDist ? ModConfig.pitchMid
                        : dist <= ModConfig.farDist ? ModConfig.pitchFar
                        : 0f;
                if (pitch > 0f) {
                    client.player.playSound(SOUND_NOTE.value(), 1f, pitch);
                }

                // Set appropriate cooldown based on distance
                int cooldown = dist <= ModConfig.closeDist ? ModConfig.lockedCooldownClose
                        : dist <= ModConfig.midDist ? ModConfig.lockedCooldownMid
                        : ModConfig.lockedCooldownFar;
                playerCooldowns.put(other.getUuid(), cooldown);
            } else {
                playerCooldowns.put(other.getUuid(), currentCooldown - 1);
            }
        }

        // Clean up cooldowns for players that are no longer in the world
        playerCooldowns.keySet().removeIf(uuid ->
                client.world.getPlayers().stream().noneMatch(p -> p.getUuid().equals(uuid))
        );
    }

    private static boolean isElytraActive(PlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA) && !player.isOnGround();
    }

    /** Projects entity center to screen and adds a fixed-size marker (only when in front of camera). */
    private static void projectEntityToScreen(Entity entity, Matrix4f projView, int scaledWidth, int scaledHeight) {
        double cx = entity.getX();
        double cy = entity.getY() + entity.getHeight() * 0.5;
        double cz = entity.getZ();
        if (!worldToScreen(projView, cx, cy, cz, scaledWidth, scaledHeight, clipPos)) return;

        int screenX = (int) (clipPos.x - ModConfig.boxWidth * 0.5f);
        int screenY = (int) (clipPos.y - ModConfig.boxHeight * 0.5f);
        String playerName = entity.getName().getString();

        // Get player health
        float health = 0f;
        float maxHealth = 20f;
        float shieldDurability = 0f;
        if (entity instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity) entity;
            health = player.getHealth();
            maxHealth = player.getMaxHealth();

            // Get shield durability from either hand
            net.minecraft.item.ItemStack shieldStack = null;

            // Check off-hand first
            net.minecraft.item.ItemStack offHandStack = player.getOffHandStack();
            if (offHandStack.isOf(net.minecraft.item.Items.SHIELD)) {
                shieldStack = offHandStack;
            }

            // If not in off-hand, check main hand
            if (shieldStack == null) {
                net.minecraft.item.ItemStack mainHandStack = player.getMainHandStack();
                if (mainHandStack.isOf(net.minecraft.item.Items.SHIELD)) {
                    shieldStack = mainHandStack;
                }
            }

            // Calculate shield durability if a shield was found
            if (shieldStack != null) {
                // Shield durability: max is 336, so normalize to 0-20 range
                int maxDurability = shieldStack.getMaxDamage();
                int currentDamage = shieldStack.getDamage();
                int currentDurability = maxDurability - currentDamage;
                // Normalize to 0-20 scale
                shieldDurability = (float) (currentDurability * 20.0 / maxDurability);
                shieldDurability = Math.max(0, Math.min(20, shieldDurability)); // Clamp to 0-20
            }
        }

        // Find existing box for this entity or create new one
        ScreenBox box = boxesToRender.stream()
                .filter(b -> Math.abs(b.targetX - screenX) < 50 && Math.abs(b.targetY - screenY) < 50)
                .findFirst()
                .orElseGet(() -> {
                    ScreenBox newBox = new ScreenBox(screenX, screenY, ModConfig.boxWidth, ModConfig.boxHeight, playerName, entity.getUuid());
                    boxesToRender.add(newBox);
                    return newBox;
                });
        box.update(screenX, screenY, playerName);
        box.setHealth(health, maxHealth);
        // Fix: Pass both UUID and shield durability to the method
        box.setShieldDurability(entity.getUuid(), shieldDurability);
    }

    /** Returns true if point is in front of camera; writes screen coords into out (x, y). */
    private static boolean worldToScreen(Matrix4f projView, double wx, double wy, double wz,
                                         int scaledWidth, int scaledHeight, Vector4f out) {
        out.set((float) wx, (float) wy, (float) wz, 1f);
        projView.transform(out);
        if (out.w <= 0) return false;
        float ndcX = out.x / out.w;
        float ndcY = out.y / out.w;
        out.x = (ndcX + 1f) * scaledWidth * 0.5f;
        out.y = (1f - ndcY) * scaledHeight * 0.5f;
        return true;
    }

    private static void renderBox(net.minecraft.client.gui.DrawContext context, int screenX, int screenY, int w, int h) {
        renderBoxWithColor(context, screenX, screenY, w, h, ModConfig.boxColorFillRed, ModConfig.boxColorBorderRed);
    }

    private static void renderBoxWithColor(net.minecraft.client.gui.DrawContext context, int screenX, int screenY, int w, int h, int fillColor, int borderColor) {
        context.fill(screenX, screenY, screenX + w, screenY + h, fillColor);
        int border = 1;
        context.fill(screenX, screenY, screenX + w, screenY + border, borderColor);           // top
        context.fill(screenX, screenY + h - border, screenX + w, screenY + h, borderColor); // bottom
        context.fill(screenX, screenY, screenX + border, screenY + h, borderColor);         // left
        context.fill(screenX + w - border, screenY, screenX + w, screenY + h, borderColor);  // right
    }

    private static void drawFovCircle(net.minecraft.client.gui.DrawContext context, int centerX, int centerY, int radius, boolean hasTargets) {
        int color = hasTargets ? ModConfig.fovCircleColorGreen : ModConfig.fovCircleColorRed;
        int width = ModConfig.fovCircleWidth;
        // Draw circle with configurable thickness by drawing pixels
        for (int i = 0; i < 360; i += 2) {
            double angle = Math.toRadians(i);
            int x = centerX + (int) (radius * Math.cos(angle));
            int y = centerY + (int) (radius * Math.sin(angle));
            // Draw pixel(s) with thickness
            context.fill(x, y, x + width, y + width, color);
        }
    }

    private static class ScreenBox {
        int targetX, targetY;
        float smoothX, smoothY;
        int w, h;
        String playerName = "";
        UUID playerUUID = null;
        boolean wasInFovCircle = false;
        boolean inFovCircle = false;
        float health = 0f;
        float maxHealth = 20f;
        float shieldDurability = 0f; // 0-20 (absorption is 0-20 health points)
        float storedShieldDurability = 0f; // Persisted shield durability (doesn't reset on player switch)
        private static final float SMOOTHING_RATE = 0.15f; // Interpolation speed per tick

        ScreenBox(int x, int y, int w, int h, String playerName, UUID playerUUID) {
            this.targetX = x;
            this.targetY = y;
            this.smoothX = x;
            this.smoothY = y;
            this.w = w;
            this.h = h;
            this.playerName = playerName;
            this.playerUUID = playerUUID;
        }

        void update(int newX, int newY, String newName) {
            this.targetX = newX;
            this.targetY = newY;
            this.playerName = newName;
        }

        void setHealth(float health, float maxHealth) {
            this.health = Math.max(0, health);
            this.maxHealth = Math.max(1, maxHealth); // Ensure maxHealth is at least 1
        }

        void setShieldDurability(UUID playerUUID, float durability) {
            float clamped = Math.max(0, Math.min(durability, 20));
            this.storedShieldDurability = clamped; // Store in the box instance
            if (clamped > 0) {
                playerShieldDurability.put(playerUUID, clamped);
            } else {
                playerShieldDurability.remove(playerUUID);
            }
        }

        public static float getStoredShieldDurability(UUID playerUUID) {
            return playerShieldDurability.getOrDefault(playerUUID, 0f);
        }
        void updateSmoothing(net.minecraft.client.render.RenderTickCounter tickCounter) {
            // Apply smoothing - interpolate toward target position
            float interpolation = SMOOTHING_RATE;
            this.smoothX += (targetX - smoothX) * interpolation;
            this.smoothY += (targetY - smoothY) * interpolation;
        }

        void setInFovCircle(boolean inCircle) {
            this.inFovCircle = inCircle;
        }


        int getX() {
            return Math.round(smoothX);
        }

        int getY() {
            return Math.round(smoothY);
        }
    }
}