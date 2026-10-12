package net.shiro.createhp.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.shiro.createhp.config.CHPConfig;

/**
 * Einmaliger Warnhinweis zum Drucksystem (Entscheidung des Maintainers): erscheint beim ersten Start vor dem
 * Hauptmenue und erst wieder, wenn jemand pressureWarningSeen in der Client-Config zuruecksetzt. Er erklaert, dass
 * Teile platzen und Netherit explodiert, bevor das in der ersten Welt ueberrascht.
 */
public class PressureWarningScreen extends Screen {

	private static final int TEXT_WIDTH = 360;
	private static final int TITLE_COLOR = 0xFF5555;

	/** Auch wenn das Speichern scheitert: in derselben Sitzung nur einmal. */
	private static boolean shownThisSession;

	private final Screen parent;
	private MultiLineLabel body = MultiLineLabel.EMPTY;
	private int bodyTop;
	private int bodyWidth;

	public PressureWarningScreen(Screen parent) {
		super(Component.translatable("createhp.warning.title"));
		this.parent = parent;
	}

	/**
	 * Haengt sich an das Hauptmenue. Geoeffnet wird erst im naechsten Durchlauf und nur, wenn das Hauptmenue dann
	 * noch offen ist: Mit Quick Play folgt gleich der Verbindungsbildschirm, den der Hinweis sonst abbrechen wuerde.
	 */
	public static void openOver(Minecraft client, Screen screen) {
		if (!(screen instanceof TitleScreen) || shownThisSession || CHPConfig.pressureWarningSeen())
			return;
		client.execute(() -> {
			if (client.screen != screen || shownThisSession)
				return;
			shownThisSession = true;
			client.setScreen(new PressureWarningScreen(screen));
		});
	}

	@Override
	protected void init() {
		bodyWidth = Math.min(width - 40, TEXT_WIDTH);
		body = MultiLineLabel.create(font, Component.translatable("createhp.warning.body"), bodyWidth);
		int textHeight = body.getLineCount() * font.lineHeight;
		int total = 20 + textHeight + 16 + 20;
		bodyTop = Math.max(30, (height - total) / 2 + 20);
		addRenderableWidget(Button.builder(Component.translatable("createhp.warning.button"), button -> onClose())
				.bounds(width / 2 - 100, bodyTop + textHeight + 16, 200, 20)
				.build());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics);
		graphics.drawCenteredString(font, title, width / 2, bodyTop - 20, TITLE_COLOR);
		body.renderLeftAligned(graphics, (width - bodyWidth) / 2, bodyTop, font.lineHeight, 0xFFFFFF);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	// Nur der Knopf schliesst: Ein versehentliches Escape soll den Hinweis nicht ungelesen abhaken.
	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void onClose() {
		CHPConfig.markPressureWarningSeen();
		minecraft.setScreen(parent);
	}
}
