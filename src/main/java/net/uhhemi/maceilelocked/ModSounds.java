package net.uhhemi.maceilelocked;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class ModSounds {

    public static final SoundEvent CLOSE = register("close");
    public static final SoundEvent MID = register("mid");
    public static final SoundEvent FAR = register("far");

    private ModSounds() {}

    private static SoundEvent register(String path) {
        Identifier id = Identifier.of(MaceileLocked.MOD_ID, path);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void registerAll() {
        // Trigger class init so the static fields above run register()
        MaceileLocked.LOGGER.info("Registered maceilelocked sounds");
    }
}
