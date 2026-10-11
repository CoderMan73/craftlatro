package com.craftlatro;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CraftlatroMod implements ModInitializer {
	public static final String MOD_ID = "craftlatro";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Craftlatro mod initialized");
	}
}
