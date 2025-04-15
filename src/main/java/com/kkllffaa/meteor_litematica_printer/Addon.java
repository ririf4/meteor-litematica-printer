package com.kkllffaa.meteor_litematica_printer;

import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Addon extends MeteorAddon {
	public static final Logger LOG = LogManager.getLogger();
	public static final Category CATEGORY = new Category("Printer", new ItemStack(Items.PINK_CARPET));

    @Override
    public void onInitialize() {
        LOG.info("Initializing litematica printer");

        boolean litematicaLoaded = isModLoaded("litematica");
        boolean malilibLoaded = isModLoaded("malilib");

        if (litematicaLoaded && malilibLoaded) {
            Modules.get().add(new Printer());
            LOG.info("Litematica and MaLiLib detected. Printer module loaded.");
        } else {
            LOG.warn("Litematica or MaLiLib not found! Printer module isn't loaded.");
        }
    }

    private boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public String getPackage() {
        return "com.kkllffaa.meteor_litematica_printer";
    }

	@Override
	public void onRegisterCategories() {
		Modules.registerCategory(CATEGORY);
	}
}
