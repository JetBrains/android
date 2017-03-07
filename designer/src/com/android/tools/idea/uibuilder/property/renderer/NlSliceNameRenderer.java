/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.renderer;

import com.android.tools.idea.uibuilder.property.NlResourceHeader;
import com.android.tools.idea.uibuilder.property.ptable.PNameRenderer;
import com.android.tools.idea.uibuilder.property.ptable.PTableItem;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import java.awt.*;

import static com.android.SdkConstants.*;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.STYLE_BOLD;
import static com.intellij.ui.SimpleTextAttributes.STYLE_SMALLER;

public class NlSliceNameRenderer extends ColoredTableCellRenderer implements PNameRenderer {
  public static final JBColor NAMESPACE_COLOR = new JBColor(new Color(128, 0, 128), new Color(151, 118, 169));
  public static final JBColor ATTRIBUTE_COLOR = new JBColor(new Color(0, 0, 255), Gray._192);
  public static final JBColor TAG_COLOR = new JBColor(new Color(0, 0, 128), new Color(180, 180, 0));

  @Override
  protected void customizeCellRenderer(JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
    PTableItem item = (PTableItem)value;
    if (item == null) {
      return;
    }
    JBColor color = ATTRIBUTE_COLOR;
    if (item instanceof NlResourceHeader) {
      color = TAG_COLOR;
      setBorder(
        BorderFactory.createCompoundBorder(
          IdeBorderFactory.createBorder(SideBorder.BOTTOM),
          BorderFactory.createEmptyBorder(6, 0, 0, 0)));
    }
    SimpleTextAttributes attr = REGULAR_ATTRIBUTES.derive(STYLE_SMALLER | STYLE_BOLD, color, null, null);
    if (!StringUtil.isEmpty(item.getNamespace())) {
      SimpleTextAttributes attrNamespace = attr.derive(-1, NAMESPACE_COLOR, null, null);
      append(getNamespaceLabel(item.getNamespace()), attrNamespace, false);
      append(":", attr, false);
    }
    append(item.getName(), attr, true);
  }

  @Override
  public void acquireState(JTable table, boolean isSelected, boolean hasFocus, int row, int column) {
    // Do not change background color if a cell has focus
    super.acquireState(table, isSelected, false, row, column);
  }

  @NotNull
  private static String getNamespaceLabel(@NotNull String namespace) {
    switch (namespace) {
      case ANDROID_URI:
        return "android";
      case TOOLS_URI:
        return "tools";
      case AUTO_URI:
        return "app";
      default:
        return "other";
    }
  }
}
