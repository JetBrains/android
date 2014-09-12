/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome;

import com.google.common.collect.ImmutableMap;
import com.intellij.ide.BrowserUtil;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.font.TextAttribute;

/**
 * Functions to style controls for a consistent user UI
 */
public final class WelcomeUIUtils {

  private WelcomeUIUtils() {
    // No instantiation
  }

  /**
   * Turns {@link javax.swing.JButton} into a hyperlink by removing fill, border,
   * changing the cursor and font style. This is preferred to styling a {@link JLabel}
   * as button is focusable control and better supports accessibility.
   */
  public static void makeButtonAHyperlink(JButton button) {
    button.setBorderPainted(false);
    button.setBorder(null);
    button.setForeground(JBColor.blue);
    button.setOpaque(false);
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    button.setFont(button.getFont().deriveFont(ImmutableMap.of(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_LOW_ONE_PIXEL)));
  }

  public static void makeButtonAHyperlink(@NotNull JButton button, @NotNull final String url) {
    makeButtonAHyperlink(button);
    button.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        BrowserUtil.browse(url);
      }
    });
  }
}
