package de.villagertrades.client;

import de.villagertrades.network.CataloguePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MerchantBookScreen extends Screen {
	private static final int MAX_BOOK_WIDTH = 400;
	private static final int MAX_BOOK_HEIGHT = 230;
	private static final int LEFT_PAGE_WIDTH = 116;
	private static final int PROFESSION_ROW = 18;
	private static final int LEVEL_HEADER = 16;
	private static final int TRADE_ROW = 24;
	private static final int LEVEL_GAP = 4;
	private static final int TOOLTIP_LINES = 6;
	private static final int DETAIL_ROW = 10;

	private static final int COVER = 0xFF2E6B3A;
	private static final int COVER_EDGE = 0xFF1D4526;
	private static final int PAGE = 0xFFF4E9CD;
	private static final int PAGE_SHADE = 0xFFE3D4AE;
	private static final int INK = 0xFF3A2A18;
	private static final int INK_SOFT = 0xFF7A6A52;
	private static final int RULE = 0xFFD8C8A2;
	private static final int EMERALD = 0xFF1E8A3C;
	private static final int RESTRICTION = 0xFF9A6A1E;

	// reopen on the last profession
	private static String lastSelected;

	private final List<CataloguePayload.Profession> professions;
	private int selected;
	private double professionScroll;
	private double tradeScroll;
	/** Trade whose details fill the right page, set by clicking a row. */
	private CataloguePayload.Trade opened;
	private double detailScroll;

	private int left;
	private int top;
	private int bookWidth;
	private int bookHeight;

	public MerchantBookScreen(CataloguePayload payload) {
		super(Component.translatable("item.villagertrades.merchant_book"));
		List<CataloguePayload.Profession> sorted = new ArrayList<>(payload.professions());
		// wandering trader is sent last, keep it at the bottom
		CataloguePayload.Profession wandering = sorted.isEmpty() ? null : sorted.removeLast();
		sorted.sort((a, b) -> a.name().getString().compareToIgnoreCase(b.name().getString()));
		if (wandering != null) {
			sorted.add(wandering);
		}
		this.professions = sorted;
		for (int i = 0; i < professions.size(); i++) {
			if (professions.get(i).name().getString().equals(lastSelected)) {
				selected = i;
			}
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		bookWidth = Math.min(MAX_BOOK_WIDTH, width - 16);
		bookHeight = Math.min(MAX_BOOK_HEIGHT, height - 16);
		left = (width - bookWidth) / 2;
		top = (height - bookHeight) / 2;
		professionScroll = Math.clamp(professionScroll, 0, maxProfessionScroll());
		tradeScroll = Math.clamp(tradeScroll, 0, maxTradeScroll());
	}

	private int listTop() {
		return top + 26;
	}

	private int listBottom() {
		return top + bookHeight - 10;
	}

	private int leftX() {
		return left + 12;
	}

	private int rightX() {
		return left + LEFT_PAGE_WIDTH + 24;
	}

	private int rightWidth() {
		return left + bookWidth - 14 - rightX();
	}

	private int maxProfessionScroll() {
		return Math.max(0, professions.size() * PROFESSION_ROW - (listBottom() - listTop()));
	}

	private int contentHeight() {
		if (professions.isEmpty()) {
			return 0;
		}
		int height = 0;
		for (CataloguePayload.Level level : professions.get(selected).levels()) {
			height += LEVEL_HEADER + level.trades().size() * TRADE_ROW + LEVEL_GAP;
		}
		return height;
	}

	private int maxTradeScroll() {
		return Math.max(0, contentHeight() - (listBottom() - listTop()));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);

		g.fill(left, top, left + bookWidth, top + bookHeight, COVER);
		g.outline(left, top, bookWidth, bookHeight, COVER_EDGE);
		int spine = left + LEFT_PAGE_WIDTH + 18;
		g.fill(left + 6, top + 5, spine - 1, top + bookHeight - 5, PAGE);
		g.fill(spine + 1, top + 5, left + bookWidth - 6, top + bookHeight - 5, PAGE);
		g.fill(spine - 1, top + 3, spine + 1, top + bookHeight - 3, COVER_EDGE);

		List<Component> tooltip = null;
		ItemStack hoveredStack = ItemStack.EMPTY;

		// left page
		g.text(font, title.copy().withStyle(ChatFormatting.BOLD), leftX(), top + 11, INK, false);
		g.fill(leftX(), listTop() - 3, leftX() + LEFT_PAGE_WIDTH, listTop() - 2, RULE);
		g.enableScissor(leftX() - 4, listTop(), leftX() + LEFT_PAGE_WIDTH + 2, listBottom());
		for (int i = 0; i < professions.size(); i++) {
			int rowY = listTop() + i * PROFESSION_ROW - (int) professionScroll;
			if (rowY + PROFESSION_ROW < listTop() || rowY > listBottom()) {
				continue;
			}
			CataloguePayload.Profession profession = professions.get(i);
			boolean hovered = isInList(mouseY) && mouseX >= leftX() - 4 && mouseX < leftX() + LEFT_PAGE_WIDTH
					&& mouseY >= rowY && mouseY < rowY + PROFESSION_ROW;
			if (i == selected || hovered) {
				g.fill(leftX() - 4, rowY, leftX() + LEFT_PAGE_WIDTH, rowY + PROFESSION_ROW, PAGE_SHADE);
			}
			if (i == selected) {
				g.fill(leftX() - 4, rowY, leftX() - 2, rowY + PROFESSION_ROW, EMERALD);
			}
			g.item(profession.icon(), leftX(), rowY + 1);
			String name = ellipsize(profession.name().getString(), LEFT_PAGE_WIDTH - 22);
			g.text(font, name, leftX() + 20, rowY + 5, i == selected ? EMERALD : INK, false);
		}
		g.disableScissor();
		scrollbar(g, leftX() + LEFT_PAGE_WIDTH + 1, professionScroll, maxProfessionScroll(), professions.size() * PROFESSION_ROW);

		if (professions.isEmpty()) {
			return;
		}

		// right page
		CataloguePayload.Profession profession = professions.get(selected);
		int x = rightX();
		int width = rightWidth();

		if (opened != null) {
			drawDetails(g, x, width);
			return;
		}

		g.text(font, profession.name().copy().withStyle(ChatFormatting.BOLD), x, top + 11, INK, false);
		g.fill(x, listTop() - 3, x + width, listTop() - 2, RULE);

		g.enableScissor(x - 4, listTop(), x + width + 4, listBottom());
		int y = listTop() - (int) tradeScroll;
		for (CataloguePayload.Level level : profession.levels()) {
			if (y + LEVEL_HEADER > listTop() && y < listBottom()) {
				g.text(font, level.title(), x, y + 4, EMERALD, false);
				Component picks = Component.translatable("villagertrades.screen.picks", level.picks(), level.trades().size());
				g.text(font, picks, x + width - font.width(picks), y + 4, INK_SOFT, false);
			}
			y += LEVEL_HEADER;

			for (CataloguePayload.Trade trade : level.trades()) {
				if (y + TRADE_ROW > listTop() && y < listBottom()) {
					boolean rowHovered = isInList(mouseY) && mouseX >= x - 4 && mouseX < x + width + 4 && mouseY >= y && mouseY < y + TRADE_ROW;
					if (rowHovered) {
						g.fill(x - 4, y, x + width + 4, y + TRADE_ROW - 1, PAGE_SHADE);
						tooltip = preview(trade);
					}
					ItemStack stack = drawTrade(g, trade, x, y, width, mouseX, mouseY, rowHovered);
					if (!stack.isEmpty()) {
						hoveredStack = stack;
					}
				}
				y += TRADE_ROW;
			}
			y += LEVEL_GAP;
		}
		g.disableScissor();
		scrollbar(g, x + width + 3, tradeScroll, maxTradeScroll(), contentHeight());

		if (!hoveredStack.isEmpty()) {
			g.setTooltipForNextFrame(font, hoveredStack, mouseX, mouseY);
		} else if (tooltip != null && !tooltip.isEmpty()) {
			g.setTooltipForNextFrame(font, tooltip, Optional.empty(), mouseX, mouseY);
		}
	}

	private ItemStack drawTrade(GuiGraphicsExtractor g, CataloguePayload.Trade trade, int x, int y, int width, int mouseX, int mouseY, boolean rowHovered) {
		ItemStack hovered = ItemStack.EMPTY;
		int itemY = y + 4;

		hovered = stack(g, trade.wants(), x, itemY, mouseX, mouseY, rowHovered, hovered);
		g.text(font, trade.wantsCount(), x + 17, itemY + 8, INK, false);

		if (!trade.extra().isEmpty()) {
			g.text(font, "+", x + 45, itemY + 4, INK_SOFT, false);
			hovered = stack(g, trade.extra(), x + 52, itemY, mouseX, mouseY, rowHovered, hovered);
			if (!trade.extraCount().equals("1")) {
				g.text(font, trade.extraCount(), x + 69, itemY + 8, INK, false);
			}
		}

		g.text(font, "→", x + 82, itemY + 4, INK, false);
		hovered = stack(g, trade.gives(), x + 93, itemY, mouseX, mouseY, rowHovered, hovered);
		g.itemDecorations(font, trade.gives(), x + 93, itemY);

		int textX = x + 113;
		int textWidth = width - 113;
		Component label = trade.label().orElse(trade.gives().getHoverName());
		if (trade.restriction().isPresent()) {
			g.text(font, ellipsize(label.getString(), textWidth), textX, y + 3, INK, false);
			g.text(font, ellipsize(trade.restriction().get().getString(), textWidth), textX, y + 13, RESTRICTION, false);
		} else {
			g.text(font, ellipsize(label.getString(), textWidth), textX, y + 8, INK, false);
		}

		g.fill(x, y + TRADE_ROW - 1, x + width, y + TRADE_ROW, RULE);
		return hovered;
	}

	/**
	 * The hover tooltip only shows the first few lines. A librarian's book trade lists every
	 * tradeable enchantment, which filled the whole screen - that list goes on the page instead.
	 */
	private List<Component> preview(CataloguePayload.Trade trade) {
		List<Component> details = trade.details();
		if (details.size() <= TOOLTIP_LINES) {
			return details;
		}
		List<Component> lines = new ArrayList<>(details.subList(0, TOOLTIP_LINES));
		lines.add(Component.translatable("villagertrades.screen.more", details.size() - TOOLTIP_LINES)
				.withStyle(ChatFormatting.DARK_GRAY));
		lines.add(Component.translatable("villagertrades.screen.click_for_details").withStyle(ChatFormatting.YELLOW));
		return lines;
	}

	private void drawDetails(GuiGraphicsExtractor g, int x, int width) {
		Component label = opened.label().orElse(opened.gives().getHoverName());
		g.item(opened.gives(), x, top + 8);
		g.text(font, ellipsize(label.getString(), width - 22), x + 20, top + 12, INK, false);
		g.fill(x, listTop() - 3, x + width, listTop() - 2, RULE);

		g.enableScissor(x - 4, listTop(), x + width + 4, listBottom());
		int y = listTop() - (int) detailScroll;
		for (Component line : detailLines(width)) {
			if (y + DETAIL_ROW > listTop() && y < listBottom()) {
				g.text(font, line, x, y, INK, false);
			}
			y += DETAIL_ROW;
		}
		g.disableScissor();
		scrollbar(g, x + width + 3, detailScroll, maxDetailScroll(width), detailHeight(width));

		Component back = Component.translatable("villagertrades.screen.back");
		g.text(font, back, x + width - font.width(back), top + 12, INK_SOFT, false);
	}

	/** Detail lines wrapped to the page width. */
	private List<Component> detailLines(int width) {
		List<Component> lines = new ArrayList<>();
		for (Component line : opened.details()) {
			String text = line.getString();
			while (font.width(text) > width) {
				String head = font.plainSubstrByWidth(text, width);
				int lastSpace = head.lastIndexOf(' ');
				if (lastSpace > 8) {
					head = head.substring(0, lastSpace);
				}
				lines.add(Component.literal(head).withStyle(line.getStyle()));
				text = text.substring(head.length()).stripLeading();
			}
			lines.add(Component.literal(text).withStyle(line.getStyle()));
		}
		return lines;
	}

	private int detailHeight(int width) {
		return detailLines(width).size() * DETAIL_ROW;
	}

	private int maxDetailScroll(int width) {
		return Math.max(0, detailHeight(width) - (listBottom() - listTop()));
	}

	private ItemStack stack(GuiGraphicsExtractor g, ItemStack stack, int x, int y, int mouseX, int mouseY, boolean rowHovered, ItemStack hovered) {
		g.item(stack, x, y);
		if (rowHovered && mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
			return stack;
		}
		return hovered;
	}

	private void scrollbar(GuiGraphicsExtractor g, int x, double scroll, int maxScroll, int contentHeight) {
		if (maxScroll <= 0) {
			return;
		}
		int trackHeight = listBottom() - listTop();
		int thumbHeight = Math.max(12, trackHeight * trackHeight / contentHeight);
		int thumbY = listTop() + (int) ((trackHeight - thumbHeight) * scroll / maxScroll);
		g.fill(x, listTop(), x + 2, listBottom(), RULE);
		g.fill(x, thumbY, x + 2, thumbY + thumbHeight, INK_SOFT);
	}

	private String ellipsize(String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		return font.plainSubstrByWidth(text, maxWidth - font.width("…")) + "…";
	}

	private boolean isInList(double mouseY) {
		return mouseY >= listTop() && mouseY < listBottom();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		if (opened != null) {
			opened = null;
			detailScroll = 0;
			return true;
		}
		CataloguePayload.Trade clicked = tradeAt(mouseX, mouseY);
		if (clicked != null && clicked.details().size() > TOOLTIP_LINES) {
			opened = clicked;
			detailScroll = 0;
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
			return true;
		}
		if (isInList(mouseY) && mouseX >= leftX() - 4 && mouseX < leftX() + LEFT_PAGE_WIDTH) {
			int index = (int) ((mouseY - listTop() + professionScroll) / PROFESSION_ROW);
			if (index >= 0 && index < professions.size()) {
				select(index);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** Which trade row sits under the cursor, mirroring the layout the right page draws. */
	private CataloguePayload.Trade tradeAt(double mouseX, double mouseY) {
		if (professions.isEmpty() || !isInList(mouseY)
				|| mouseX < rightX() - 4 || mouseX > rightX() + rightWidth() + 4) {
			return null;
		}
		int y = listTop() - (int) tradeScroll;
		for (CataloguePayload.Level level : professions.get(selected).levels()) {
			y += LEVEL_HEADER;
			for (CataloguePayload.Trade trade : level.trades()) {
				if (mouseY >= y && mouseY < y + TRADE_ROW) {
					return trade;
				}
				y += TRADE_ROW;
			}
			y += LEVEL_GAP;
		}
		return null;
	}

	public void select(int index) {
		if (index != selected) {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
		}
		selected = index;
		tradeScroll = 0;
		opened = null;
		detailScroll = 0;
		lastSelected = professions.get(index).name().getString();
	}

	public List<CataloguePayload.Profession> professions() {
		return professions;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (opened != null) {
			detailScroll = Math.clamp(detailScroll - scrollY * DETAIL_ROW * 3, 0, maxDetailScroll(rightWidth()));
		} else if (mouseX < rightX() - 8) {
			professionScroll = Math.clamp(professionScroll - scrollY * PROFESSION_ROW, 0, maxProfessionScroll());
		} else {
			tradeScroll = Math.clamp(tradeScroll - scrollY * TRADE_ROW, 0, maxTradeScroll());
		}
		return true;
	}

	@Override
	public void onClose() {
		// Esc goes back to the trade list first, then out of the book.
		if (opened != null) {
			opened = null;
			detailScroll = 0;
			return;
		}
		super.onClose();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// E closes it like an inventory
		if (minecraft != null && minecraft.options.keyInventory.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}
}
