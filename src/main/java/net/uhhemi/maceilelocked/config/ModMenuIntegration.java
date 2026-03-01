package net.uhhemi.maceilelocked.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return screen -> createConfigScreen(screen);
    }

    private static Screen createConfigScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.literal("Maceile Locked Config"))
                .setSavingRunnable(ModMenuIntegration::saveConfig);

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // Box Rendering Category
        ConfigCategory boxCategory = builder.getOrCreateCategory(Text.literal("Box Rendering"));
        boxCategory.addEntry(entryBuilder.startIntField(Text.literal("Box Width"), ModConfig.boxWidth)
                .setDefaultValue(20)
                .setMin(5)
                .setMax(100)
                .setSaveConsumer(v -> ModConfig.boxWidth = v)
                .build());
        boxCategory.addEntry(entryBuilder.startIntField(Text.literal("Box Height"), ModConfig.boxHeight)
                .setDefaultValue(20)
                .setMin(5)
                .setMax(100)
                .setSaveConsumer(v -> ModConfig.boxHeight = v)
                .build());

        // Detection Settings Category
        ConfigCategory detectionCategory = builder.getOrCreateCategory(Text.literal("Detection Settings"));
        detectionCategory.addEntry(entryBuilder.startDoubleField(Text.literal("Look Angle Threshold (degrees)"), ModConfig.lockedLookAngleDeg)
                .setDefaultValue(20.0)
                .setMin(1.0)
                .setMax(180.0)
                .setSaveConsumer(v -> ModConfig.lockedLookAngleDeg = v)
                .build());
        detectionCategory.addEntry(entryBuilder.startDoubleField(Text.literal("Close Distance (blocks)"), ModConfig.closeDist)
                .setDefaultValue(50.0)
                .setMin(1.0)
                .setMax(500.0)
                .setSaveConsumer(v -> ModConfig.closeDist = v)
                .build());
        detectionCategory.addEntry(entryBuilder.startDoubleField(Text.literal("Mid Distance (blocks)"), ModConfig.midDist)
                .setDefaultValue(100.0)
                .setMin(1.0)
                .setMax(500.0)
                .setSaveConsumer(v -> ModConfig.midDist = v)
                .build());
        detectionCategory.addEntry(entryBuilder.startDoubleField(Text.literal("Far Distance (blocks)"), ModConfig.farDist)
                .setDefaultValue(200.0)
                .setMin(1.0)
                .setMax(500.0)
                .setSaveConsumer(v -> ModConfig.farDist = v)
                .build());

        // Cooldown Settings Category
        ConfigCategory cooldownCategory = builder.getOrCreateCategory(Text.literal("Cooldown Settings"));
        cooldownCategory.addEntry(entryBuilder.startIntField(Text.literal("Close Cooldown (ticks)"), ModConfig.lockedCooldownClose)
                .setDefaultValue(3)
                .setMin(1)
                .setMax(100)
                .setSaveConsumer(v -> ModConfig.lockedCooldownClose = v)
                .build());
        cooldownCategory.addEntry(entryBuilder.startIntField(Text.literal("Mid Cooldown (ticks)"), ModConfig.lockedCooldownMid)
                .setDefaultValue(10)
                .setMin(1)
                .setMax(100)
                .setSaveConsumer(v -> ModConfig.lockedCooldownMid = v)
                .build());
        cooldownCategory.addEntry(entryBuilder.startIntField(Text.literal("Far Cooldown (ticks)"), ModConfig.lockedCooldownFar)
                .setDefaultValue(20)
                .setMin(1)
                .setMax(100)
                .setSaveConsumer(v -> ModConfig.lockedCooldownFar = v)
                .build());
        cooldownCategory.addEntry(entryBuilder.startIntField(Text.literal("Global Ping Cooldown (ticks)"), ModConfig.pingCooldown)
                .setDefaultValue(40)
                .setMin(1)
                .setMax(100)
                .setSaveConsumer(v -> ModConfig.pingCooldown = v)
                .build());
        cooldownCategory.addEntry(entryBuilder.startIntField(Text.literal("FOV Ping Cooldown (ticks)"), ModConfig.fovCirclePingCooldown)
                .setDefaultValue(20)
                .setMin(1)
                .setMax(100)
                .setSaveConsumer(v -> ModConfig.fovCirclePingCooldown = v)
                .build());


        // Sound Settings Category
        ConfigCategory soundCategory = builder.getOrCreateCategory(Text.literal("Sound Settings"));
        soundCategory.addEntry(entryBuilder.startFloatField(Text.literal("Close Pitch"), ModConfig.pitchClose)
                .setDefaultValue(2.0f)
                .setMin(0.1f)
                .setMax(4.0f)
                .setSaveConsumer(v -> ModConfig.pitchClose = v)
                .build());
        soundCategory.addEntry(entryBuilder.startFloatField(Text.literal("Mid Pitch"), ModConfig.pitchMid)
                .setDefaultValue(1.0f)
                .setMin(0.1f)
                .setMax(4.0f)
                .setSaveConsumer(v -> ModConfig.pitchMid = v)
                .build());
        soundCategory.addEntry(entryBuilder.startFloatField(Text.literal("Far Pitch"), ModConfig.pitchFar)
                .setDefaultValue(0.25f)
                .setMin(0.1f)
                .setMax(4.0f)
                .setSaveConsumer(v -> ModConfig.pitchFar = v)
                .build());

        // FOV Circle Settings Category
        ConfigCategory fovCategory = builder.getOrCreateCategory(Text.literal("FOV Circle Settings"));
        fovCategory.addEntry(entryBuilder.startFloatField(Text.literal("FOV Circle Degrees"), ModConfig.fovCircleDegrees)
                .setDefaultValue(15f)
                .setMin(1f)
                .setMax(90f)
                .setSaveConsumer(v -> ModConfig.fovCircleDegrees = v)
                .build());

        // Text Rendering Category
        ConfigCategory textCategory = builder.getOrCreateCategory(Text.literal("Text Settings"));
        textCategory.addEntry(entryBuilder.startBooleanToggle(Text.literal("Show Player Names"), ModConfig.showPlayerNames)
                .setDefaultValue(true)
                .setSaveConsumer(v -> ModConfig.showPlayerNames = v)
                .build());
        textCategory.addEntry(entryBuilder.startIntField(Text.literal("Player Name Y Offset"), ModConfig.playerNameYOffset)
                .setDefaultValue(-10)
                .setMin(-50)
                .setMax(50)
                .setSaveConsumer(v -> ModConfig.playerNameYOffset = v)
                .build());

        return builder.build();
    }

    private static void saveConfig() {
        // Config values are saved immediately through setSaveConsumer
    }
}


