package net.uhhemi.maceilelocked.config;

/**
 * Centralized configuration for Maceile Locked mod.
 * All values are stored here and can be modified by the config menu.
 */
public class ModConfig {

    // Box rendering
    public static int boxWidth = 20;
    public static int boxHeight = 20;

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
    public static int fovCircleColorRed = 0xFFFF0000; // Red when no targets
    public static int fovCircleColorGreen = 0xAA00FF00; // Green when targets detected

    // FOV Circle ping settings
    public static int fovCirclePingCooldown = 100; // Per-box cooldown for FOV circle pings (in ticks)

    // Text rendering
    public static boolean showPlayerNames = true; // Show player names above boxes
    public static int playerNameColor = 0xFFFFFFFF; // White text color
    public static int playerNameYOffset = -10; // Y offset above the box
}

