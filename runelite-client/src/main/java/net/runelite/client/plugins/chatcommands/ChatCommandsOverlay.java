/*
 * Copyright (c) 2024, Ben <runelite@sololegends.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *	list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *	this list of conditions and the following disclaimer in the documentation
 *	and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.chatcommands;

import com.google.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ChatCommandsOverlay extends Overlay
{

	private final Client client;
	private final ChatCommandsPlugin plugin;
	private final ChatCommandsConfig config;

	private static final int PETS_ICON_WIDTH = 21;
	private static final int PETS_ICON_HEIGHT = 14;

	@Inject
	private TooltipManager tooltipManager;

	@Inject
	private ChatCommandsOverlay(Client client, ChatCommandsPlugin plugin, ChatCommandsConfig config)
	{
		super(plugin);
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(Overlay.PRIORITY_MED);
		this.client = client;
		this.plugin = plugin;
		this.config = config;
	}

	private boolean checkVisible(Widget parent, Widget line)
	{
		int childS = line.getRelativeY() + line.getHeight();
		// Check if child's south most Y coord is within the base bounds of parent's scrolled viewport
		return childS > parent.getScrollY() && childS < parent.getHeight() + parent.getScrollY();
	}

	private List<Integer> extractPetIconIds(String pets)
	{
		int limit = -Integer.MAX_VALUE / 10;
		List<Integer> petIcons = new ArrayList<>();
		boolean reading = false;
		int intCache = 0;
		for (int i = 0; i < pets.length(); i++)
		{
			char c = pets.charAt(i);
			// If at the start of presumably the numbers
			if (c == '=')
			{
				reading = true;
			}
			// End reference point
			else if (reading && c == '>')
			{
				reading = false;
				petIcons.add(-intCache);
				intCache = 0;
			}
			else if (reading)
			{
				// Accumulating negatively avoids surprises near MAX_VALUE
				int digit = Character.digit(c, 10);
				if (digit < 0 || intCache < limit)
				{
					continue;
				}
				intCache *= 10;
				if (intCache < limit + digit)
				{
					continue;
				}
				intCache -= digit;
			}
		}
		return petIcons;
	}

	private void petsOverlay(Graphics2D graphics)
	{
		// Still loading icon data
		if (plugin.petsIconIdx == -1)
		{
			return;
		}
		Point mousePos = client.getMouseCanvasPosition();
		// Get the chat box lines
		Widget messageLines = client.getWidget(ComponentID.CHATBOX_MESSAGE_LINES);
		if (messageLines == null || !messageLines.contains(mousePos))
		{
			return;
		}
		Widget[] lines = messageLines.getChildren();
		if (lines == null)
		{
			return;
		}
		for (Widget line : lines)
		{
			// ensure visible, text is not null, and matching pets text
			if (line.getText() != null
					&& checkVisible(messageLines, line)
					&& line.getText().contains("Pets: (")
					&& line.getText().contains("<img="))
			{
				// Get the bounds
				Rectangle bounds = line.getBounds();
				// Get mouse position
				// If mouse not-in message bounds, continue
				if (!bounds.contains(mousePos.getX(), mousePos.getY()))
				{
					continue;
				}
				// Extract the pets list by icon ids
				List<Integer> petIconIds = extractPetIconIds(line.getText().substring(line.getText().indexOf(")")));

				// Generate the offset based on the pets width
				int petsTextOffset = line.getFont().getTextWidth("Pets (" + petIconIds.size() + ")");

				// Is it double height with many pets?
				int petsPerLine = petIconIds.size();
				if (bounds.height > PETS_ICON_HEIGHT)
				{
					petsPerLine = (bounds.width - petsTextOffset) / PETS_ICON_WIDTH;
				}

				// Get the hovered pet by the mouse position within chat line bounds
				int iconPos = Math.floorDiv((int) (mousePos.getX() - (petsTextOffset + bounds.getX())),
						PETS_ICON_WIDTH);
				if (mousePos.getY() >= bounds.getY() + PETS_ICON_HEIGHT)
				{
					iconPos = Math.floorDiv((int) (mousePos.getX() - bounds.getX()),
							PETS_ICON_WIDTH) + petsPerLine;
				}
				// If in bounds of a known icon position
				if (iconPos >= 0 && iconPos < petIconIds.size())
				{
					int petIcon = petIconIds.get(iconPos);
					// Bounds verification!
					if (petIcon - plugin.petsIconIdx >= 0 && petIcon - plugin.petsIconIdx < plugin.pets.length)
					{
						String name = client.getItemDefinition(plugin.pets[petIcon - plugin.petsIconIdx]).getName();
						// Draw a tool tip with the pet's name
						tooltipManager.add(new Tooltip("Pet: " + name));
					}
				}
			}
		}
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		// Only render the pets if the pets commands is active
		if (config.pets())
		{
			petsOverlay(graphics);
		}
		return null;
	}

}
