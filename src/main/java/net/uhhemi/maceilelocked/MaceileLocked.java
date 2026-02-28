package net.uhhemi.maceilelocked;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MaceileLocked implements ModInitializer {
    public static final String MOD_ID = "maceilelocked";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("MaceileLocked initialized!");
        ModSounds.registerAll();
    }
}