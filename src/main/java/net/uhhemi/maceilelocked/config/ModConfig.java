package net.uhhemi.maceilelocked.config;

/**
 * Centralized configuration for Maceile Locked mod.
 * All values are stored here and can be modified by the config menu.
 */
public class ModConfig {

    // Box rendering
    public static int boxWidth = 20;
    public static int boxHeight = 20;
    public static boolean alwaysShowBoxes = false; // Show boxes always, not just in elytra flight mode

    // Box colors - separate ARGB values for more control
    public static int boxColorFillRed = 0x40FF0000;   // semi-transparent red
    public static int boxColorBorderRed = 0xFFFF0000; // red border
    public static int boxColorFillGreen = 0x4000FF00;   // semi-transparent green
    public static int boxColorBorderGreen = 0xFF00FF00; // green border

    // Looking at detection
    public static double lockedLookAngleDeg = 20.0; // Max angle (deg) from other player's look to you to count as "looking at you"

    // Cooldowns (in ticks)
    public static int lockedCooldownClose = 3;  // Close: fast pings (every 2 ticks)
    public static int lockedCooldownMid = 10;    // Mid: medium pings (every 5 ticks)
    public static int lockedCooldownFar = 20;   // Far: slow pings (every 10 ticks)
    public static int pingCooldown = 100; // Global cooldown between any pings (in ticks)

    // Distance thresholds (blocks)
    public static double closeDist = 50.0;
    public static double midDist = 100.0;
    public static double farDist = 200.0;

    // Sound pitches
    public static float pitchClose = 2.0f;  // High frequency (fast beeps)
    public static float pitchMid = 1.0f;    // Medium frequency (normal beeps)
    public static float pitchFar = 0.25f;   // Low frequency (slow beeps)

    // FOV Circle settings
    public static float fovCircleDegrees = 20f; // FOV circle radius in degrees for ping detection
    public static boolean drawFovCircle = false; // Whether to draw the FOV circle on screen
    public static int fovCircleWidth = 1; // Width/thickness of the FOV circle line in pixels
    public static int fovCircleColorRed = 0xFFFF0000; // Red when no targets
    public static int fovCircleColorGreen = 0xAA00FF00; // Green when targets detected

    // FOV Circle ping settings
    public static int fovCirclePingCooldown = 100; // Per-box cooldown for FOV circle pings (in ticks)

    // Text rendering
    public static boolean showPlayerNames = true; // Show player names above boxes
    public static int playerNameColor = 0xFFFFFFFF; // White text color
    public static int playerNameYOffset = -10; // Y offset above the box

    // Side bar settings (left side of box) - choose between health or shield
    public enum SideBarMode {
        NONE("None"),
        HEALTH("Health"),
        SHIELD("Shield");

        public final String displayName;

        SideBarMode(String displayName) {
            this.displayName = displayName;
        }
    }

    public static SideBarMode sideBarMode = SideBarMode.HEALTH; // Default to health bar
    public static int sideBarWidth = 3; // Width of the side bar in pixels
    public static int sideBarHeight = 30; // Height of the side bar in pixels
    public static int healthBarColor = 0xFF00FF00; // Green for health (locked on color)
    public static int healthBarOutlineColor = 0xFF00FF00; // Green outline
    public static int healthBarEmptyColor = 0xFFFF0000; // Red background for depleted health (locked off color)
    public static int healthBarEmptyOutlineColor = 0xFFFF0000; // Red outline
    public static int shieldBarColor = 0xFF00FF00; // Green for shield (locked on color)
    public static int shieldBarOutlineColor = 0xFF00FF00; // Green outline
    public static int shieldBarEmptyColor = 0xFFFF0000; // Red background for empty shield (locked off color)
    public static int shieldBarEmptyOutlineColor = 0xFFFF0000; // Red outline
}

