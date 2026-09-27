package de.villagertrades.test;

import de.villagertrades.client.MerchantBookScreen;
import de.villagertrades.network.CataloguePayload;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.item.Items;

import java.util.List;

// ./gradlew runClientGameTest, screenshots end up in build/run/clientGameTest/screenshots
public class MerchantBookClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("clear @a");
			server.runCommand("give @a villagertrades:merchant_book");
			context.waitTicks(10);
			context.takeScreenshot("merchant-book-in-hand");

			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(MerchantBookScreen.class);

			List<CataloguePayload.Profession> professions = context.computeOnClient(mc -> ((MerchantBookScreen) mc.gui.screen()).professions());
			for (CataloguePayload.Profession profession : professions) {
				System.out.println("[catalogue] " + profession.name().getString() + ": " + profession.levels().stream()
						.map(level -> level.title().getString() + " " + level.picks() + "/" + level.trades().size()).toList());
			}
			check(professions.size() == 14, "expected 13 professions + wandering trader, got " + professions.size());
			for (CataloguePayload.Profession profession : professions) {
				if (!profession.name().getString().equals("Wandering Trader")) {
					check(profession.levels().size() == 5, profession.name().getString() + " should have 5 levels");
				}
				check(profession.levels().stream().allMatch(level -> !level.trades().isEmpty()), profession.name().getString() + " has an empty level");
			}

			CataloguePayload.Profession librarian = find(professions, "Librarian");
			CataloguePayload.Trade book = librarian.levels().getFirst().trades().stream()
					.filter(trade -> trade.gives().is(Items.ENCHANTED_BOOK)).findFirst().orElseThrow();
			System.out.println("[catalogue] librarian book: " + book.wantsCount() + " " + book.label().map(c -> c.getString()).orElse("")
					+ " details=" + book.details().stream().map(c -> c.getString()).toList());
			check(book.label().isPresent() && book.details().size() > 10, "enchanted book trade should list its possible enchantments");

			context.getInput().setCursorPos(0, 0);
			for (String name : List.of("Librarian", "Cartographer", "Wandering Trader")) {
				int index = professions.indexOf(find(professions, name));
				context.runOnClient(mc -> ((MerchantBookScreen) mc.gui.screen()).select(index));
				context.waitTicks(3);
				context.takeScreenshot("merchant-book-" + name.toLowerCase().replace(" ", "-"));
			}

			// hover the enchanted book trade
			int librarianIndex = professions.indexOf(librarian);
			int bookRow = librarian.levels().getFirst().trades().indexOf(book);
			context.runOnClient(mc -> ((MerchantBookScreen) mc.gui.screen()).select(librarianIndex));
			double[] cursor = context.computeOnClient(mc -> {
				double scale = mc.getWindow().getGuiScale();
				int bookWidth = Math.min(400, mc.getWindow().getGuiScaledWidth() - 16);
				int bookHeight = Math.min(230, mc.getWindow().getGuiScaledHeight() - 16);
				int left = (mc.getWindow().getGuiScaledWidth() - bookWidth) / 2;
				int top = (mc.getWindow().getGuiScaledHeight() - bookHeight) / 2;
				return new double[]{(left + 116 + 24 + 150) * scale, (top + 26 + 16 + bookRow * 24 + 12) * scale};
			});
			context.getInput().setCursorPos(cursor[0], cursor[1]);
			context.waitTicks(3);
			context.takeScreenshot("merchant-book-librarian-tooltip");

			// clicking the row moves the long enchantment list onto the page
			context.getInput().pressMouse(0);
			context.waitTicks(3);
			context.takeScreenshot("merchant-book-enchantment-list");
			check(context.computeOnClient(mc -> mc.gui.screen() instanceof MerchantBookScreen),
					"the detail page should stay inside the book");

			context.getInput().pressMouse(0);
			context.waitTicks(3);

			context.getInput().pressKey(options -> options.keyInventory);
			context.waitTicks(2);
			check(context.computeOnClient(mc -> mc.gui.screen() == null), "the inventory key should close the merchant book");
		}
	}

	private static CataloguePayload.Profession find(List<CataloguePayload.Profession> professions, String name) {
		return professions.stream().filter(p -> p.name().getString().equals(name)).findFirst()
				.orElseThrow(() -> new AssertionError("profession " + name + " missing"));
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
