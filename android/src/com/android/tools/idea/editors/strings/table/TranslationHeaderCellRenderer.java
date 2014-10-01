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
package com.android.tools.idea.editors.strings.table;

import com.android.tools.idea.configurations.LocaleMenuAction;
import com.android.tools.idea.rendering.Locale;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

/**
 * Renderer for the header of a column that shows the translations for a locale.  This header displays a flag icon
 * and, depending on the current column width, either the full name of the locale - e.g., Czech (cs) - or a
 * brief name - e.g., cs.
 */
public class TranslationHeaderCellRenderer implements HeaderCellRenderer {
  private final Locale myLocale;
  private boolean myBrief;

  private final int myCollapsedWidth;
  private final int myExpandedWidth;
  private final int myNameSwitchWidth;

  public TranslationHeaderCellRenderer(@NotNull FontMetrics metrics, @NotNull Locale locale) {
    myLocale = locale;
    myCollapsedWidth =
      PADDING + metrics.stringWidth(myLocale.getFlagImage().getIconWidth() + LocaleMenuAction.getLocaleLabel(myLocale, true));
    myExpandedWidth =
      PADDING + metrics.stringWidth(String.valueOf(ConstantColumn.DEFAULT_VALUE.sampleData));
    myNameSwitchWidth =
      PADDING + metrics.stringWidth(myLocale.getFlagImage().getIconWidth() + LocaleMenuAction.getLocaleLabel(myLocale, false));
    myBrief = true;
  }

  @Override
  public int getCollapsedWidth() {
    return myCollapsedWidth;
  }

  @Override
  public int getFullExpandedWidth() {
    return myExpandedWidth;
  }

  @Override
  public int getMinimumExpandedWidth() {
    return myNameSwitchWidth;
  }

  public void setUseBriefName(boolean brief) {
    myBrief = brief;
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused, int row, int column) {
    TableCellRenderer defaultRenderer = table.getTableHeader().getDefaultRenderer();
    Component component = defaultRenderer.getTableCellRendererComponent(table, value, selected, focused, row, column);
    if (component instanceof JLabel) {
      JLabel labelComponent = (JLabel) component;
      labelComponent.setIcon(myLocale.getFlagImage());
      labelComponent.setText(LocaleMenuAction.getLocaleLabel(myLocale, myBrief));
    }
    return component;
  }
}
