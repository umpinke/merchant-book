package de.villagertrades;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import de.villagertrades.network.CataloguePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.TradeSets;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

// Trades are data driven since 26.1, easiest way to read the loot functions is to encode them back to json.
public final class TradeCatalogue {
	private static final int MAX_EMERALDS = 64;

	private final RegistryAccess registries;
	private final RegistryOps<JsonElement> ops;

	private TradeCatalogue(MinecraftServer server) {
		this.registries = server.registryAccess();
		this.ops = RegistryOps.create(JsonOps.INSTANCE, registries);
	}

	public static CataloguePayload build(MinecraftServer server) {
		return new TradeCatalogue(server).build();
	}

	private CataloguePayload build() {
		List<CataloguePayload.Profession> professions = new ArrayList<>();
		BuiltInRegistries.VILLAGER_PROFESSION.listElements().forEach(holder -> {
			if (holder.is(VillagerProfession.NONE) || holder.is(VillagerProfession.NITWIT)) {
				return;
			}
			VillagerProfession profession = holder.value();
			List<CataloguePayload.Level> levels = new ArrayList<>();
			for (int level = 1; level <= 5; level++) {
				ResourceKey<TradeSet> key = profession.tradeSetsByLevel().get(level);
				if (key != null) {
					level(Component.translatable("merchant.level." + level), key).ifPresent(levels::add);
				}
			}
			if (!levels.isEmpty()) {
				professions.add(new CataloguePayload.Profession(profession.name(), jobSiteIcon(profession), levels));
			}
		});

		List<CataloguePayload.Level> wandering = new ArrayList<>();
		level(Component.translatable("villagertrades.wandering.buying"), TradeSets.WANDERING_TRADER_BUYING).ifPresent(wandering::add);
		level(Component.translatable("villagertrades.wandering.common"), TradeSets.WANDERING_TRADER_COMMON).ifPresent(wandering::add);
		level(Component.translatable("villagertrades.wandering.uncommon"), TradeSets.WANDERING_TRADER_UNCOMMON).ifPresent(wandering::add);
		if (!wandering.isEmpty()) {
			professions.add(new CataloguePayload.Profession(Component.translatable("entity.minecraft.wandering_trader"),
					new ItemStack(Items.WANDERING_TRADER_SPAWN_EGG), wandering));
		}
		return new CataloguePayload(professions);
	}

	private static ItemStack jobSiteIcon(VillagerProfession profession) {
		return BuiltInRegistries.POINT_OF_INTEREST_TYPE.listElements()
				.filter(poi -> profession.heldJobSite().test(poi))
				.flatMap(poi -> poi.value().matchingStates().stream())
				.map(state -> new ItemStack(state.getBlock().asItem()))
				.filter(stack -> !stack.isEmpty())
				.findFirst()
				.orElseGet(() -> new ItemStack(Items.EMERALD));
	}

	private Optional<CataloguePayload.Level> level(Component title, ResourceKey<TradeSet> key) {
		return registries.lookupOrThrow(Registries.TRADE_SET).get(key).map(set -> {
			List<CataloguePayload.Trade> trades = new ArrayList<>();
			for (Holder<VillagerTrade> trade : set.value().getTrades()) {
				try {
					trades.add(trade(trade.value()));
				} catch (RuntimeException e) {
					VillagerTradesMod.LOGGER.warn("Could not read trade {}", trade.getRegisteredName(), e);
				}
			}
			String picks = TradeSet.CODEC.encodeStart(ops, set.value()).result()
					.map(json -> {
						// master librarian has amount 3 but only 2 trades
						double[] amount = range(json.getAsJsonObject().get("amount"));
						return format(new double[]{Math.min(amount[0], trades.size()), Math.min(amount[1], trades.size())});
					})
					.orElse("?");
			return new CataloguePayload.Level(title, picks, trades);
		});
	}

	private CataloguePayload.Trade trade(VillagerTrade trade) {
		JsonObject json = VillagerTrade.CODEC.encodeStart(ops, trade).getOrThrow().getAsJsonObject();

		JsonObject wants = json.getAsJsonObject("wants");
		double[] wantsCount = range(wants.get("count"));
		ItemStack wantsStack = new ItemStack(item(wants));

		ItemStack extraStack = ItemStack.EMPTY;
		String extraCount = "";
		if (json.has("additional_wants")) {
			JsonObject extra = json.getAsJsonObject("additional_wants");
			extraStack = new ItemStack(item(extra));
			extraCount = format(range(extra.get("count")));
		}

		ItemStack gives = ItemStackTemplate.CODEC.parse(ops, json.get("gives")).getOrThrow().create();
		List<Component> details = new ArrayList<>();
		Component label = null;
		double[] extraCost = null;

		List<Holder<Enchantment>> doublePrice = json.has("double_trade_price_enchantments")
				? holders(Registries.ENCHANTMENT, json.get("double_trade_price_enchantments")) : List.of();

		JsonArray modifiers = json.has("given_item_modifiers") ? json.getAsJsonArray("given_item_modifiers") : new JsonArray();
		for (JsonElement element : modifiers) {
			JsonObject modifier = element.getAsJsonObject();
			boolean addsCost = modifier.has("include_additional_cost_component") && modifier.get("include_additional_cost_component").getAsBoolean();
			switch (modifier.get("function").getAsString()) {
				case "minecraft:enchant_randomly" -> {
					List<Holder<Enchantment>> options = modifier.has("options")
							? holders(Registries.ENCHANTMENT, modifier.get("options"))
							: registries.lookupOrThrow(Registries.ENCHANTMENT).listElements().map(h -> (Holder<Enchantment>) h).toList();
					label = Component.translatable("villagertrades.trade.random_enchantment");
					gives.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
					details.add(Component.translatable("villagertrades.trade.possible_enchantments").withStyle(ChatFormatting.GOLD));

					double min = Double.MAX_VALUE;
					double max = 0;
					List<Component> entries = new ArrayList<>();
					for (Holder<Enchantment> enchantment : sortedByName(options)) {
						Enchantment value = enchantment.value();
						int factor = doublePrice.contains(enchantment) ? 2 : 1;
						// EnchantRandomlyFunction: 2 + random(5 + level * 10) + 3 * level
						double cheapest = clampCost(wantsCount[0] + (2 + 3 * value.getMinLevel()) * factor);
						double priciest = clampCost(wantsCount[1] + (6 + 13 * value.getMaxLevel()) * factor);
						min = Math.min(min, cheapest);
						max = Math.max(max, priciest);
						entries.add(levelRange(enchantment).copy()
								.append(Component.literal(addsCost ? " " + format(new double[]{cheapest, priciest}) : "").withStyle(ChatFormatting.GREEN)));
					}
					details.addAll(pack(entries));
					if (addsCost && min <= max) {
						extraCost = new double[]{min, max};
					}
				}
				case "minecraft:enchant_with_levels" -> {
					double[] levels = range(modifier.get("levels"));
					label = Component.translatable("villagertrades.trade.enchanted_levels", format(levels));
					gives.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
					if (addsCost) {
						extraCost = new double[]{clampCost(wantsCount[0] + levels[0]), clampCost(wantsCount[1] + levels[1])};
					}
				}
				case "minecraft:set_enchantments" -> {
					List<String> names = new ArrayList<>();
					if (modifier.has("enchantments")) {
						for (var entry : modifier.getAsJsonObject("enchantments").entrySet()) {
							Optional<Holder.Reference<Enchantment>> enchantment = registries.lookupOrThrow(Registries.ENCHANTMENT)
									.get(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(entry.getKey())));
							int level = (int) range(entry.getValue())[0];
							enchantment.ifPresent(h -> names.add(Enchantment.getFullname(h, level).getString()));
						}
					}
					if (!names.isEmpty()) {
						label = Component.literal(String.join(", ", names));
					}
					gives.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
				}
				case "minecraft:set_name" -> ComponentSerialization.CODEC.parse(ops, modifier.get("name")).result()
						.ifPresent(name -> gives.set(DataComponents.ITEM_NAME, name));
				case "minecraft:set_random_dyes" -> label = Component.translatable("villagertrades.trade.random_dyes");
				case "minecraft:set_potion" -> registries.lookupOrThrow(Registries.POTION)
						.get(ResourceKey.create(Registries.POTION, Identifier.parse(modifier.get("id").getAsString())))
						.ifPresent(potion -> gives.set(DataComponents.POTION_CONTENTS, new PotionContents(potion)));
				case "minecraft:set_random_potion" -> {
					label = Component.translatable("villagertrades.trade.random_effect");
					details.add(Component.translatable("villagertrades.trade.possible_effects").withStyle(ChatFormatting.GOLD));
					List<Holder<Potion>> potions = modifier.has("options")
							? holders(Registries.POTION, modifier.get("options"))
							: registries.lookupOrThrow(Registries.POTION).listElements().map(h -> (Holder<Potion>) h).toList();
					List<Component> entries = new ArrayList<>();
					for (Holder<Potion> potion : potions) {
						ItemStack example = gives.copy();
						example.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
						entries.add(example.getHoverName());
					}
					details.addAll(pack(entries));
				}
				case "minecraft:set_stew_effect" -> {
					label = Component.translatable("villagertrades.trade.random_effect");
					details.add(Component.translatable("villagertrades.trade.possible_effects").withStyle(ChatFormatting.GOLD));
					if (modifier.has("effects")) {
						List<Component> entries = new ArrayList<>();
						for (JsonElement effect : modifier.getAsJsonArray("effects")) {
							Identifier id = Identifier.parse(effect.getAsJsonObject().get("type").getAsString());
							entries.add(Component.translatable("effect." + id.getNamespace() + "." + id.getPath()));
						}
						details.addAll(pack(entries));
					}
				}
				default -> {
				}
			}
		}

		String wantsText = format(extraCost != null ? extraCost : new double[]{Math.max(1, wantsCount[0]), Math.max(1, wantsCount[1])});

		Component restriction = null;
		if (json.has("merchant_predicate")) {
			List<String> variants = new ArrayList<>();
			findVariants(json.get("merchant_predicate"), variants::add);
			restriction = variants.isEmpty()
					? Component.translatable("villagertrades.trade.conditional")
					: Component.translatable("villagertrades.trade.only_variants", joinVariants(variants));
			details.add(restriction.copy().withStyle(ChatFormatting.YELLOW));
		}

		details.add(Component.translatable("villagertrades.trade.max_uses", format(json.has("max_uses") ? range(json.get("max_uses")) : new double[]{4, 4}))
				.withStyle(ChatFormatting.GRAY));
		if (json.has("xp")) {
			details.add(Component.translatable("villagertrades.trade.xp", format(range(json.get("xp")))).withStyle(ChatFormatting.GRAY));
		}

		return new CataloguePayload.Trade(wantsStack, wantsText, extraStack, extraCount, gives,
				Optional.ofNullable(label), Optional.ofNullable(restriction), details);
	}

	private static Item item(JsonObject cost) {
		return BuiltInRegistries.ITEM.getValue(Identifier.parse(cost.get("id").getAsString()));
	}

	private static double clampCost(double cost) {
		return Mth.clamp(cost, 1, MAX_EMERALDS);
	}

	// "#tag", "id" or ["id", ...]
	@SuppressWarnings("unchecked")
	private <T> List<Holder<T>> holders(ResourceKey<? extends net.minecraft.core.Registry<T>> registry, JsonElement json) {
		HolderLookup.RegistryLookup<T> lookup = registries.lookupOrThrow(registry);
		List<Holder<T>> result = new ArrayList<>();
		if (json.isJsonArray()) {
			for (JsonElement element : json.getAsJsonArray()) {
				result.addAll(holders(registry, element));
			}
		} else {
			String value = json.getAsString();
			if (value.startsWith("#")) {
				lookup.get(TagKey.create(registry, Identifier.parse(value.substring(1)))).ifPresent(set -> set.forEach(result::add));
			} else {
				lookup.get(ResourceKey.create(registry, Identifier.parse(value))).ifPresent(result::add);
			}
		}
		return result;
	}

	private static List<Holder<Enchantment>> sortedByName(List<Holder<Enchantment>> enchantments) {
		return enchantments.stream().sorted(Comparator.comparing(h -> h.value().description().getString())).toList();
	}

	// otherwise the enchantment tooltip is taller than the screen
	private static List<Component> pack(List<Component> entries) {
		List<Component> lines = new ArrayList<>();
		net.minecraft.network.chat.MutableComponent line = null;
		int length = 0;
		for (Component entry : entries) {
			int entryLength = entry.getString().length();
			if (line != null && length + entryLength + 3 > 58) {
				lines.add(line);
				line = null;
			}
			if (line == null) {
				line = Component.literal(" ").append(entry);
				length = entryLength;
			} else {
				line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)).append(entry);
				length += entryLength + 3;
			}
		}
		if (line != null) {
			lines.add(line);
		}
		return lines;
	}

	private static Component levelRange(Holder<Enchantment> enchantment) {
		Enchantment value = enchantment.value();
		if (value.getMinLevel() == value.getMaxLevel()) {
			return Enchantment.getFullname(enchantment, value.getMaxLevel());
		}
		return value.description().copy().append(" ")
				.append(Component.translatable("enchantment.level." + value.getMinLevel()))
				.append("–")
				.append(Component.translatable("enchantment.level." + value.getMaxLevel()));
	}

	private static void findVariants(JsonElement json, Consumer<String> out) {
		if (json.isJsonObject()) {
			for (var entry : json.getAsJsonObject().entrySet()) {
				if (entry.getKey().equals("minecraft:villager/variant")) {
					JsonElement value = entry.getValue();
					if (value.isJsonArray()) {
						value.getAsJsonArray().forEach(v -> out.accept(v.getAsString()));
					} else if (value.isJsonPrimitive()) {
						out.accept(value.getAsString());
					}
				} else {
					findVariants(entry.getValue(), out);
				}
			}
		} else if (json.isJsonArray()) {
			json.getAsJsonArray().forEach(element -> findVariants(element, out));
		}
	}

	private static Component joinVariants(List<String> variants) {
		net.minecraft.network.chat.MutableComponent joined = Component.empty();
		for (int i = 0; i < variants.size(); i++) {
			if (i > 0) {
				joined.append(", ");
			}
			Identifier id = Identifier.parse(variants.get(i));
			String biome = id.getPath().equals("snow") ? "snowy_plains" : id.getPath();
			joined.append(Component.translatable("biome." + id.getNamespace() + "." + biome));
		}
		return joined;
	}

	static double[] range(JsonElement json) {
		if (json == null || json.isJsonNull()) {
			return new double[]{1, 1};
		}
		if (json.isJsonPrimitive()) {
			double value = json.getAsDouble();
			return new double[]{value, value};
		}
		JsonObject object = json.getAsJsonObject();
		String type = object.has("type") ? object.get("type").getAsString() : "minecraft:uniform";
		return switch (type) {
			case "minecraft:constant" -> range(object.get("value"));
			case "minecraft:uniform" -> new double[]{range(object.get("min"))[0], range(object.get("max"))[1]};
			case "minecraft:binomial" -> new double[]{0, range(object.get("n"))[1]};
			case "minecraft:sum" -> {
				double[] sum = {0, 0};
				for (JsonElement summand : object.getAsJsonArray("summands")) {
					double[] part = range(summand);
					sum[0] += part[0];
					sum[1] += part[1];
				}
				yield sum;
			}
			default -> new double[]{1, 1};
		};
	}

	static String format(double[] range) {
		long min = Math.round(range[0]);
		long max = Math.round(range[1]);
		return min == max ? Long.toString(min) : min + "–" + max;
	}
}
