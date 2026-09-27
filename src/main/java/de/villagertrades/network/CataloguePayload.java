package de.villagertrades.network;

import de.villagertrades.VillagerTradesMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamEncoder;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record CataloguePayload(List<Profession> professions) implements CustomPacketPayload {
	public static final Type<CataloguePayload> TYPE = new Type<>(VillagerTradesMod.id("catalogue"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CataloguePayload> CODEC = StreamCodec.ofMember(CataloguePayload::write, CataloguePayload::read);

	/** Nothing legitimate comes close to this, it only stops a bogus packet from allocating wildly. */
	private static final int MAX_ENTRIES = 8192;

	public record Profession(Component name, ItemStack icon, List<Level> levels) {
	}

	// picks = how many of these trades a villager gets
	public record Level(Component title, String picks, List<Trade> trades) {
	}

	public record Trade(ItemStack wants, String wantsCount, ItemStack extra, String extraCount, ItemStack gives,
						Optional<Component> label, Optional<Component> restriction, List<Component> details) {
	}

	private static <T> void writeList(RegistryFriendlyByteBuf buf, List<T> list,
									  StreamEncoder<RegistryFriendlyByteBuf, T> encoder) {
		buf.writeVarInt(list.size());
		for (T entry : list) {
			encoder.encode(buf, entry);
		}
	}

	private static <T> List<T> readList(RegistryFriendlyByteBuf buf, StreamDecoder<RegistryFriendlyByteBuf, T> decoder) {
		int size = buf.readVarInt();
		if (size < 0 || size > MAX_ENTRIES) {
			throw new IllegalStateException("Merchant book: bad list size " + size);
		}
		List<T> list = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			list.add(decoder.decode(buf));
		}
		return list;
	}

	private void write(RegistryFriendlyByteBuf buf) {
		writeList(buf, professions, (out, profession) -> {
			ComponentSerialization.STREAM_CODEC.encode(out, profession.name());
			ItemStack.OPTIONAL_STREAM_CODEC.encode(out, profession.icon());
			writeList(out, profession.levels(), CataloguePayload::writeLevel);
		});
	}

	private static void writeLevel(RegistryFriendlyByteBuf buf, Level level) {
		ComponentSerialization.STREAM_CODEC.encode(buf, level.title());
		buf.writeUtf(level.picks());
		writeList(buf, level.trades(), (out, trade) -> {
			ItemStack.OPTIONAL_STREAM_CODEC.encode(out, trade.wants());
			out.writeUtf(trade.wantsCount());
			ItemStack.OPTIONAL_STREAM_CODEC.encode(out, trade.extra());
			out.writeUtf(trade.extraCount());
			ItemStack.OPTIONAL_STREAM_CODEC.encode(out, trade.gives());
			ComponentSerialization.OPTIONAL_STREAM_CODEC.encode(out, trade.label());
			ComponentSerialization.OPTIONAL_STREAM_CODEC.encode(out, trade.restriction());
			writeList(out, trade.details(), ComponentSerialization.STREAM_CODEC::encode);
		});
	}

	private static CataloguePayload read(RegistryFriendlyByteBuf buf) {
		return new CataloguePayload(readList(buf, in -> new Profession(
				ComponentSerialization.STREAM_CODEC.decode(in),
				ItemStack.OPTIONAL_STREAM_CODEC.decode(in),
				readList(in, CataloguePayload::readLevel))));
	}

	private static Level readLevel(RegistryFriendlyByteBuf buf) {
		return new Level(ComponentSerialization.STREAM_CODEC.decode(buf), buf.readUtf(), readList(buf, in -> new Trade(
				ItemStack.OPTIONAL_STREAM_CODEC.decode(in),
				in.readUtf(),
				ItemStack.OPTIONAL_STREAM_CODEC.decode(in),
				in.readUtf(),
				ItemStack.OPTIONAL_STREAM_CODEC.decode(in),
				ComponentSerialization.OPTIONAL_STREAM_CODEC.decode(in),
				ComponentSerialization.OPTIONAL_STREAM_CODEC.decode(in),
				readList(in, ComponentSerialization.STREAM_CODEC::decode))));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
