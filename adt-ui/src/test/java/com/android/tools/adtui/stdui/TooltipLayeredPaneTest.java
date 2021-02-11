/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.adtui.stdui;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.adtui.common.AdtUiUtils;
import java.awt.Container;
import java.awt.Cursor;
import javax.swing.JButton;
import javax.swing.JPanel;
import org.junit.Test;

public class TooltipLayeredPaneTest {
  @Test
  public void setCursorOnLayeredPane() {
    JButton button = new JButton();
    JPanel panel = new JPanel();
    panel.add(button);
    TooltipLayeredPane layeredPane = new TooltipLayeredPane(panel);
    Cursor cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    Container cursorSettingContainer = AdtUiUtils.setTooltipCursor(button, cursor);
    assertThat(layeredPane).isSameAs(cursorSettingContainer);
    assertThat(cursor).isEqualTo(layeredPane.getCursor());
  }

  @Test
  public void setCursorOnLayeredPaneNotFound() {
    JButton button = new JButton();
    JPanel panel = new JPanel();
    panel.add(button);
    Cursor cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
    assertThat(AdtUiUtils.setTooltipCursor(button, cursor)).isNull();
  }
}