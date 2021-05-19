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
package com.android.tools.adtui.instructions;

import com.intellij.ui.HyperlinkLabel;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An instruction for rendering an URL. It wraps a {@link HyperlinkLabel} which supports all the proper formatting and interactions users
 * would perform on a typical URL.
 * By default, it will handle mouse clicks by browsing to the specified url, unless action is specified, in which case the
 * action will be run when the link is clicked.
 */
public final class HyperlinkInstruction extends RenderInstruction {
  @NotNull private final HyperlinkLabel myHyperlinkLabel;
  @NotNull private final Dimension mySize;

  private HyperlinkInstruction(@NotNull Font font, @NotNull String text, @Nullable String url, @Nullable Runnable action) {
    myHyperlinkLabel = new HyperlinkLabel(text);
    myHyperlinkLabel.setFont(font);

    if (url != null) {
      myHyperlinkLabel.setHyperlinkTarget(url);
    }

    if (action != null) {
      myHyperlinkLabel.addHyperlinkListener(new HyperlinkListener() {
        @Override
        public void hyperlinkUpdate(HyperlinkEvent e) {
          action.run();
        }
      });
    }

    setMouseHandler(evt -> myHyperlinkLabel.dispatchEvent(evt));

    // do this after setting text and icon in HyperlinkLabel
    mySize = myHyperlinkLabel.getPreferredSize();
  }

  public HyperlinkInstruction(@NotNull Font font, @NotNull String text, @NotNull String url) {
    this(font, text, url, null);
  }

  public HyperlinkInstruction(@NotNull Font font, @NotNull String text, @NotNull Runnable action) {
    this(font, text, null, action);
  }

  @NotNull
  @Override
  public Dimension getSize() {
    return mySize;
  }

  @Override
  @Nullable
  public Cursor getCursorIcon() {
    return (myHyperlinkLabel.isCursorSet()) ? myHyperlinkLabel.getCursor() : null;
  }

  @Override
  public void render(@NotNull JComponent c, @NotNull Graphics2D g2d, @NotNull Rectangle bounds) {
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
    g2d.translate(bounds.x, bounds.y);
    myHyperlinkLabel.setBounds(bounds);
    myHyperlinkLabel.paint(g2d);
    g2d.translate(-bounds.x, -bounds.y);
  }
}