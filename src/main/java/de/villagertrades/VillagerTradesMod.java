package de.villagertrades;

import de.villagertrades.network.CataloguePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillagerTradesMod implements ModInitializer {
	public static final String MOD_ID = "villagertrades";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final ResourceKey<Item> MERCHANT_BOOK_KEY = ResourceKey.create(Registries.ITEM, id("merchant_book"));

	public static final Item MERCHANT_BOOK = Registry.register(BuiltInRegistries.ITEM, MERCHANT_BOOK_KEY,
			new MerchantBookItem(new Item.Properties().setId(MERCHANT_BOOK_KEY).stacksTo(1).rarity(Rarity.UNCOMMON)));

	@Override
	public void onInitialize() {
		ResourceKey<CreativeModeTab> toolsTab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("tools_and_utilities"));
		CreativeModeTabEvents.modifyOutputEvent(toolsTab).register(output -> output.insertAfter(Items.WRITABLE_BOOK, MERCHANT_BOOK));

		// The full catalogue (all professions, all levels) is bigger than the default payload limit.
		PayloadTypeRegistry.clientboundPlay().registerLarge(CataloguePayload.TYPE, CataloguePayload.CODEC, 8 * 1024 * 1024);

		LOGGER.info("Merchant book loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
