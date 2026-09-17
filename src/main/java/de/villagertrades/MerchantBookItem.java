package de.villagertrades;

import de.villagertrades.network.CataloguePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public class MerchantBookItem extends Item {
	public MerchantBookItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
			if (ServerPlayNetworking.canSend(serverPlayer, CataloguePayload.TYPE)) {
				ServerPlayNetworking.send(serverPlayer, TradeCatalogue.build(serverPlayer.level().getServer()));
			} else {
				serverPlayer.sendSystemMessage(Component.translatableWithFallback("villagertrades.missing_client_mod",
						"The merchant book needs the mod on your client, too."));
			}
		}
		return InteractionResult.SUCCESS;
	}
}
