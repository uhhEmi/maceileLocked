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

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MaceileLockedClient implements ClientModInitializer {

    private static final int BOX_WIDTH = 20;
    private static final int BOX_HEIGHT = 20;
    private static final int BOX_COLOR_FILL_RED = 0x40FF0000;   // semi-transparent red
    private static final int BOX_COLOR_BORDER_RED = 0xFFFF0000; // red border
    private static final int BOX_COLOR_FILL_GREEN = 0x4000FF00;   // semi-transparent green
    private static final int BOX_COLOR_BORDER_GREEN = 0xFF00FF00; // green border

    /** Max angle (deg) from other player's look to you to count as "looking at you". */
    private static final double LOCKED_LOOK_ANGLE_DEG = 20;
    /** Cooldown between lock sounds (ticks) - unique per distance. */
    private static final int LOCKED_COOLDOWN_CLOSE = 3;  // Close: fast pings (every 2 ticks)
    private static final int LOCKED_COOLDOWN_MID = 10;    // Mid: medium pings (every 5 ticks)
    private static final int LOCKED_COOLDOWN_FAR = 20;   // Far: slow pings (every 10 ticks)
    private static final int PING_COOLDOWN = 40; // Global cooldown between any pings (in ticks)
    /** Distance thresholds (blocks): close <= CLOSE_DIST, mid <= MID_DIST, far <= FAR_DIST. */
    private static final double CLOSE_DIST = 50;
    private static final double MID_DIST = 100;
    private static final double FAR_DIST = 200;

    // Note block pitches: close=highest (2.0), mid=medium (1.0), far=low (0.25)
    private static final net.minecraft.registry.entry.RegistryEntry<SoundEvent> SOUND_NOTE = SoundEvents.BLOCK_NOTE_BLOCK_PLING;
    private static final float PITCH_CLOSE = 2.0f;  // High frequency (fast beeps)
    private static final float PITCH_MID = 1.0f;    // Medium frequency (normal beeps)
    private static final float PITCH_FAR = 0.25f;   // Low frequency (slow beeps)
    private static final float PITCH_CAMERA_CENTER = 2.4f; // Very high pitch for camera center detection

    private static final List<ScreenBox> boxesToRender = new ArrayList<>();
    private static final Map<UUID, Integer> playerCooldowns = new HashMap<>();
    private static int globalPingCooldown = 0; // Global cooldown for all pings
    private static final float FOV_CIRCLE_DEGREES = 15f; // FOV circle radius in degrees for ping detection
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
            // Only show teammate-style markers during elytra flight (elytra equipped + in air)
            boolean elytraActive = client.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)
                    && !client.player.isOnGround();
            if (!elytraActive) {
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
            if (!elytraActive) {
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
            float fovRadiusPixels = (FOV_CIRCLE_DEGREES / fovDegrees) * (screenHeight / 2f);

            // Decrement global ping cooldown
            if (globalPingCooldown > 0) {
                globalPingCooldown--;
            }

            // Update smoothing and render all boxes
            for (ScreenBox box : boxesToRender) {
                box.updateSmoothing(tickCounter);

                // Decrement per-box ping cooldown
                if (box.pingCooldown > 0) {
                    box.pingCooldown--;
                }

                // Check if camera center is over this box
                int boxX = box.getX();
                int boxY = box.getY();
                boolean cameraOver = screenCenterX >= boxX && screenCenterX <= boxX + box.w &&
                                     screenCenterY >= boxY && screenCenterY <= boxY + box.h;
                box.setCameraCenter(cameraOver);
                box.checkCameraCenterTransition(client);

                // Check if box is within FOV circle (only in first-person)
                boolean inFovCircle = false;
                if (isFirstPerson) {
                    float distToCenter = (float) Math.sqrt(
                        Math.pow(boxX + box.w / 2 - screenCenterX, 2) +
                        Math.pow(boxY + box.h / 2 - screenCenterY, 2)
                    );
                    inFovCircle = distToCenter <= fovRadiusPixels;
                    box.setInFovCircle(inFovCircle);

                    // Play ping sound when box enters FOV circle and cooldown is ready (first-person only)
                    if (inFovCircle && !box.wasInFovCircle && globalPingCooldown <= 0 && box.pingCooldown <= 0) {
                        if (client.player != null) {
                            client.player.playSound(SOUND_NOTE.value(), 1f, PITCH_CLOSE);
                        }
                        globalPingCooldown = PING_COOLDOWN;
                        box.pingCooldown = 20; // Per-box cooldown to prevent excessive pings
                    }
                    box.wasInFovCircle = inFovCircle;
                } else {
                    // Not in first-person, reset FOV circle state
                    box.setInFovCircle(false);
                    box.wasInFovCircle = false;
                }

                // Render with appropriate color based on camera center or FOV circle
                if (cameraOver) {
                    renderBoxWithColor(drawContext, boxX, boxY, box.w, box.h, BOX_COLOR_FILL_GREEN, BOX_COLOR_BORDER_GREEN);
                } else if (inFovCircle) {
                    renderBoxWithColor(drawContext, boxX, boxY, box.w, box.h, 0x4400FF00, 0xFF00FF00); // Green when in FOV circle
                } else {
                    renderBoxWithColor(drawContext, boxX, boxY, box.w, box.h, BOX_COLOR_FILL_RED, BOX_COLOR_BORDER_RED);
                }

                // Render player name above the box
                if (client != null && client.textRenderer != null) {
                    int nameX = boxX + box.w / 2;
                    int nameY = boxY - 10;
                    int textWidth = client.textRenderer.getWidth(box.playerName);
                    drawContext.drawText(client.textRenderer, box.playerName, nameX - textWidth / 2, nameY, 0xFFFFFFFF, true);
                }
            }

            // Draw FOV circle only in first-person - pass true if any boxes are detected in FOV circle
            if (isFirstPerson) {
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
        double cosThreshold = Math.cos(Math.toRadians(LOCKED_LOOK_ANGLE_DEG));
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
                float pitch = dist <= CLOSE_DIST ? PITCH_CLOSE
                        : dist <= MID_DIST ? PITCH_MID
                        : dist <= FAR_DIST ? PITCH_FAR
                        : 0f;
                if (pitch > 0f) {
                    client.player.playSound(SOUND_NOTE.value(), 1f, pitch);
                }

                // Set appropriate cooldown based on distance
                int cooldown = dist <= CLOSE_DIST ? LOCKED_COOLDOWN_CLOSE
                        : dist <= MID_DIST ? LOCKED_COOLDOWN_MID
                        : LOCKED_COOLDOWN_FAR;
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

        int screenX = (int) (clipPos.x - BOX_WIDTH * 0.5f);
        int screenY = (int) (clipPos.y - BOX_HEIGHT * 0.5f);
        String playerName = entity.getName().getString();

        // Find existing box for this entity or create new one
        ScreenBox box = boxesToRender.stream()
                .filter(b -> Math.abs(b.targetX - screenX) < 50 && Math.abs(b.targetY - screenY) < 50)
                .findFirst()
                .orElseGet(() -> {
                    ScreenBox newBox = new ScreenBox(screenX, screenY, BOX_WIDTH, BOX_HEIGHT, playerName);
                    boxesToRender.add(newBox);
                    return newBox;
                });
        box.update(screenX, screenY, playerName);
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
        renderBoxWithColor(context, screenX, screenY, w, h, BOX_COLOR_FILL_RED, BOX_COLOR_BORDER_RED);
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
        int color = hasTargets ? 0xAA00FF00 : 0xFFFF0000; // Green when targets detected, red otherwise
        // Draw circle with 1 pixel thickness by drawing single pixels
        for (int i = 0; i < 360; i += 2) {
            double angle = Math.toRadians(i);
            int x = centerX + (int) (radius * Math.cos(angle));
            int y = centerY + (int) (radius * Math.sin(angle));
            // Draw single pixel point
            context.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static class ScreenBox {
        int targetX, targetY;
        float smoothX, smoothY;
        int w, h;
        String playerName = "";
        boolean isCameraCenter = false;
        boolean cameraHasBeenCentered = false;
        boolean wasInFovCircle = false;
        boolean inFovCircle = false;
        int pingCooldown = 0; // per-box cooldown to prevent repeated pings
        private static final float SMOOTHING_RATE = 0.15f; // Interpolation speed per tick

        ScreenBox(int x, int y, int w, int h, String playerName) {
            this.targetX = x;
            this.targetY = y;
            this.smoothX = x;
            this.smoothY = y;
            this.w = w;
            this.h = h;
            this.playerName = playerName;
        }

        void update(int newX, int newY, String newName) {
            this.targetX = newX;
            this.targetY = newY;
            this.playerName = newName;
        }

        void updateSmoothing(net.minecraft.client.render.RenderTickCounter tickCounter) {
            // Apply smoothing - interpolate toward target position
            float interpolation = SMOOTHING_RATE;
            this.smoothX += (targetX - smoothX) * interpolation;
            this.smoothY += (targetY - smoothY) * interpolation;
        }

        void setCameraCenter(boolean center) {
            this.isCameraCenter = center;
        }

        void setInFovCircle(boolean inCircle) {
            this.inFovCircle = inCircle;
        }

        boolean checkCameraCenterTransition(MinecraftClient client) {
            // Play camera-center ping only once per entry and only if global cooldown allows
            if (isCameraCenter && !cameraHasBeenCentered && globalPingCooldown <= 0) {
                if (client.player != null) {
                    client.player.playSound(SOUND_NOTE.value(), 1f, PITCH_CAMERA_CENTER);
                }
                cameraHasBeenCentered = true;
                // set global cooldown so we don't spam pings
                globalPingCooldown = PING_COOLDOWN;
                return true;
            } else if (!isCameraCenter && cameraHasBeenCentered) {
                // Camera center left - reset flag for next time
                cameraHasBeenCentered = false;
            }
            return false;
        }

        int getX() {
            return Math.round(smoothX);
        }

        int getY() {
            return Math.round(smoothY);
        }
    }
}

