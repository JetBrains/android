/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.adtui.eventrenderer;

import static icons.StudioIcons.Profiler.Events.BACK_BUTTON;
import static icons.StudioIcons.Profiler.Events.VOLUME_DOWN;
import static icons.StudioIcons.Profiler.Events.VOLUME_UP;

import com.android.tools.adtui.model.event.EventAction;
import com.android.tools.adtui.model.event.KeyboardAction;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBFont;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.geom.AffineTransform;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KeyboardEventRenderer<E> implements EventRenderer<E> {

  private static final JBFont FONT = JBFont.create(new Font(null, Font.BOLD, 11));
  private static final int ROUND_ARC = 5;
  private static final int POINT_HEIGHT_OFFSET = 5;
  private static final int PADDING = 2;
  private static final int BORDER_MARGIN = 2;
  private static final JBColor BACKGROUND_COLOR = new JBColor(0x988b8e, 0x999a9a);
  private static final JBColor TEXT_COLOR = new JBColor(0xfafafa, 0x313335);
  private static final Map<String, Icon> KEYBOARD_ICON_LOOKUP;
  private static final Map<KeyboardIconCacheKey, Icon> BORDERED_KEYBOARD_ICON_CACHE = new HashMap<>();

  static {
    Map<String, Icon> keyboardIcons = new HashMap<>();
    keyboardIcons.put("KEYCODE_BACK", BACK_BUTTON);
    keyboardIcons.put("KEYCODE_VOLUME_DOWN", VOLUME_DOWN);
    keyboardIcons.put("KEYCODE_VOLUME_UP", VOLUME_UP);
    KEYBOARD_ICON_LOOKUP = keyboardIcons;
  }

  @Override
  public void draw(@NotNull Component parent,
                   @NotNull Graphics2D g2d,
                   @NotNull AffineTransform transform,
                   double length,
                   boolean isMouseOver,
                   @Nullable EventAction<E> action) {
    if (!(action instanceof KeyboardAction)) {
      return;
    }
    KeyboardAction keyAction = (KeyboardAction)action;
    boolean drawString = !KEYBOARD_ICON_LOOKUP.containsKey(keyAction.getData().toString());
    if (drawString) {
      drawString(parent, g2d, transform, keyAction);
    }
    else {
      drawIcon(parent, g2d, transform, keyAction);
    }
  }

  private void drawString(Component parent, Graphics2D g2d, AffineTransform transform, KeyboardAction action) {
    Color currentColor = g2d.getColor();
    Font currentFont = g2d.getFont();
    AffineTransform originalTransform = g2d.getTransform();

    // Set state for String rendering.
    g2d.setFont(FONT);

    // Get current string information.
    FontMetrics metrics = g2d.getFontMetrics();
    String textToDraw = action.getData().toString();
    int width = metrics.stringWidth(textToDraw);
    int height = metrics.getHeight();
    int paddedHeight = height + PADDING;
    int pointWidth = width + ROUND_ARC;

    // Offset the word by half width to ensure arrow is pointing at the exact time of the event.
    transform.translate(-width / 2.0, 0);
    g2d.transform(transform);

    // Build and draw geometry used for background of string
    // Draw the background with border margin first. The border should have the same color with parent Component.
    // Draw the background without border margin with BORDER_MARGIN then.
    for (int margin : Arrays.asList(BORDER_MARGIN, 0)) {
      g2d.setColor(margin == 0 ? BACKGROUND_COLOR : parent.getBackground());
      Polygon polygon = new Polygon();
      polygon.addPoint(-PADDING - margin, paddedHeight); // left point
      polygon.addPoint(pointWidth + margin, paddedHeight); // right point
      polygon.addPoint(pointWidth / 2, paddedHeight + POINT_HEIGHT_OFFSET + margin); // bottom point
      g2d.fillRoundRect(-PADDING - margin, PADDING - margin, width + PADDING + ROUND_ARC + margin * 2,
                        height + PADDING + margin * 2, ROUND_ARC, ROUND_ARC);
      g2d.fillPolygon(polygon);
    }
    // Draw String
    g2d.setColor(TEXT_COLOR);
    g2d.drawString(textToDraw, PADDING / 2, height);

    //Reset g2d state
    g2d.setColor(currentColor);
    g2d.setFont(currentFont);
    g2d.setTransform(originalTransform);
  }

  private void drawIcon(Component parent, Graphics2D g2d, AffineTransform transform, KeyboardAction action) {
    Icon icon = BORDERED_KEYBOARD_ICON_CACHE
      .computeIfAbsent(new KeyboardIconCacheKey(action.getData().toString(), parent.getBackground(), BORDER_MARGIN),
                       key -> EventRenderer
                         .createImageIconWithBackgroundBorder(KEYBOARD_ICON_LOOKUP.get(action.getData().toString()), BORDER_MARGIN,
                                                              parent.getBackground(), g2d));
    AffineTransform originalTransform = g2d.getTransform();
    g2d.transform(transform);
    icon.paintIcon(parent, g2d, -icon.getIconWidth() / 2, 0);
    g2d.setTransform(originalTransform);
  }

  private static class KeyboardIconCacheKey {
    @NotNull private final String myKeyString;
    @NotNull private final Color myBackgroundColor;
    private final int myBorderMargin;

    public KeyboardIconCacheKey(@NotNull String string, @NotNull Color color, int margin) {
      myKeyString = string;
      myBackgroundColor = color;
      myBorderMargin = margin;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof KeyboardIconCacheKey) {
        KeyboardIconCacheKey key = (KeyboardIconCacheKey)obj;
        return myKeyString.equals(key.myKeyString) &&
               myBackgroundColor.equals(key.myBackgroundColor) &&
               myBorderMargin == key.myBorderMargin;
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(new int[]{myKeyString.hashCode(), myBackgroundColor.hashCode(), myBorderMargin});
    }
  }
}
