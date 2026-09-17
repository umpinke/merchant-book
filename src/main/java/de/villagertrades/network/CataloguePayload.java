package de.villagertrades.network;

import de.villagertrades.VillagerTradesMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/** Every profession with all trades it can roll per level, sent from the server when the merchant book is used. */
public record CataloguePayload(List<Profession> professions) implements CustomPacketPayload {
	public static final Type<CataloguePayload> TYPE = new Type<>(VillagerTradesMod.id("catalogue"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CataloguePayload> CODEC = StreamCodec.ofMember(CataloguePayload::write, CataloguePayload::read);

	public record Profession(Component name, ItemStack icon, List<Level> levels) {
	}

	/** {@code picks} is how many of the listed trades a villager actually gets on this level. */
	public record Level(Component title, String picks, List<Trade> trades) {
	}

	public record Trade(ItemStack wants, String wantsCount, ItemStack extra, String extraCount, ItemStack gives,
						Optional<Component> label, Optional<Component> restriction, List<Component> details) {
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeCollection(professions, (b, profession) -> {
			RegistryFriendlyByteBuf out = (RegistryFriendlyByteBuf) b;
			ComponentSerialization.STREAM_CODEC.encode(out, profession.name());
			ItemStack.OPTIONAL_STREAM_CODEC.encode(out, profession.icon());
			out.writeCollection(profession.levels(), (b2, level) -> writeLevel((RegistryFriendlyByteBuf) b2, level));
		});
	}

	private static void writeLevel(RegistryFriendlyByteBuf buf, Level level) {
		ComponentSerialization.STREAM_CODEC.encode(buf, level.title());
		buf.writeUtf(level.picks());
		buf.writeCollection(level.trades(), (b, trade) -> {
			RegistryFriendlyByteBuf out = (RegistryFriendlyByteBuf) b;
			ItemStack.OPTIONAL_STREAM_CODEC.encode(out, trade.wants());
			out.writeUtf(trade.wantsCount());
			ItemStack.OPTIONAL_STREAM_CODEC.encode(out, trade.extra());
			out.writeUtf(trade.extraCount());
			ItemStack.OPTIONAL_STREAM_CODEC.encode(out, trade.gives());
			ComponentSerialization.OPTIONAL_STREAM_CODEC.encode(out, trade.label());
			ComponentSerialization.OPTIONAL_STREAM_CODEC.encode(out, trade.restriction());
			out.writeCollection(trade.details(), (b2, line) -> ComponentSerialization.STREAM_CODEC.encode((RegistryFriendlyByteBuf) b2, line));
		});
	}

	private static CataloguePayload read(RegistryFriendlyByteBuf buf) {
		return new CataloguePayload(buf.readList(b -> {
			RegistryFriendlyByteBuf in = (RegistryFriendlyByteBuf) b;
			return new Profession(ComponentSerialization.STREAM_CODEC.decode(in), ItemStack.OPTIONAL_STREAM_CODEC.decode(in),
					in.readList(b2 -> readLevel((RegistryFriendlyByteBuf) b2)));
		}));
	}

	private static Level readLevel(RegistryFriendlyByteBuf buf) {
		return new Level(ComponentSerialization.STREAM_CODEC.decode(buf), buf.readUtf(), buf.readList(b -> {
			RegistryFriendlyByteBuf in = (RegistryFriendlyByteBuf) b;
			return new Trade(ItemStack.OPTIONAL_STREAM_CODEC.decode(in), in.readUtf(), ItemStack.OPTIONAL_STREAM_CODEC.decode(in),
					in.readUtf(), ItemStack.OPTIONAL_STREAM_CODEC.decode(in), ComponentSerialization.OPTIONAL_STREAM_CODEC.decode(in),
					ComponentSerialization.OPTIONAL_STREAM_CODEC.decode(in),
					in.readList(b2 -> ComponentSerialization.STREAM_CODEC.decode((RegistryFriendlyByteBuf) b2)));
		}));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
