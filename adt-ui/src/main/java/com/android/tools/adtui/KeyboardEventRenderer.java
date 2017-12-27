/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.adtui;

import com.android.tools.adtui.model.event.EventAction;
import com.android.tools.adtui.model.event.KeyboardAction;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.JBFont;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.Map;

public class KeyboardEventRenderer<E> implements SimpleEventRenderer<E> {

  private static final JBFont FONT = JBFont.create(new Font(null, Font.BOLD, 11));
  private static final int ROUND_ARC = 5;
  private static final int POINT_HEIGHT_OFFSET = 5;
  private static final int PADDING = 2;
  private static final JBColor BACKGROUND_COLOR = new JBColor(0x988b8e, 0x999a9a);
  private static final JBColor TEXT_COLOR = new JBColor(0xfafafa, 0x313335);
  private static final Map<String, Icon> KEYBOARD_ICON_LOOKUP;
  static
  {
    Map<String, Icon> keyboardIcons = new HashMap<>();
    keyboardIcons.put("KEYCODE_BACK", load("/icons/events/back-button.png"));
    keyboardIcons.put("KEYCODE_VOLUME_DOWN", load("/icons/events/volume-down.png"));
    keyboardIcons.put("KEYCODE_VOLUME_UP", load("/icons/events/volume-up.png"));
    KEYBOARD_ICON_LOOKUP = keyboardIcons;
  }


  @NotNull
  private static Icon load(String path) {
      return IconLoader.getIcon(path, KeyboardEventRenderer.class);
  }

  @Override
  public void draw(Component parent, Graphics2D g2d, AffineTransform transform, double length, EventAction<E> action) {
    //Cache off current state of g2d.
    if (!(action instanceof KeyboardAction)) {
      return;
    }
    KeyboardAction keyAction = (KeyboardAction)action;
    boolean drawString = !KEYBOARD_ICON_LOOKUP.containsKey(keyAction.getData().toString());
    if (drawString) {
      drawString(g2d, transform, keyAction);
    } else {
      drawIcon(parent, g2d, transform, keyAction);
    }
  }

  private void drawString(Graphics2D g2d, AffineTransform transform, KeyboardAction action) {
    Color currentColor = g2d.getColor();
    Font currentFont = g2d.getFont();
    AffineTransform originalTransform = g2d.getTransform();

    // Set state for String rendering.
    g2d.setFont(FONT);
    g2d.setColor(BACKGROUND_COLOR);

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

    //Build and draw geometry used for background of string
    Polygon poly = new Polygon(new int[]{-PADDING, pointWidth, (pointWidth) / 2},
                               new int[]{paddedHeight, paddedHeight, paddedHeight + POINT_HEIGHT_OFFSET}, 3);

    g2d.fillRoundRect(-PADDING, PADDING, width + PADDING + ROUND_ARC, height + PADDING, ROUND_ARC, ROUND_ARC);
    g2d.fillPolygon(poly);

    // Draw String
    g2d.setColor(TEXT_COLOR);
    g2d.drawString(textToDraw, PADDING / 2, height);

    //Reset g2d state
    g2d.setColor(currentColor);
    g2d.setFont(currentFont);
    g2d.setTransform(originalTransform);
  }

  private void drawIcon(Component parent, Graphics2D g2d, AffineTransform transform, KeyboardAction action) {
    Icon icon = KEYBOARD_ICON_LOOKUP.get(action.getData().toString());
    AffineTransform originalTransform = g2d.getTransform();
    g2d.transform(transform);
    icon.paintIcon(parent, g2d, -icon.getIconWidth() / 2, 0);
    g2d.setTransform(originalTransform);
  }
}
