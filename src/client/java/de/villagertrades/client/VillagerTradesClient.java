package de.villagertrades.client;

import de.villagertrades.network.CataloguePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class VillagerTradesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(CataloguePayload.TYPE,
				(payload, context) -> context.client().setScreenAndShow(new MerchantBookScreen(payload)));
	}
}
