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

import com.android.tools.adtui.ptable.PNameRenderer;
import com.android.tools.adtui.ptable.PTable;
import com.android.tools.adtui.ptable.PTableCellRenderer;
import com.android.tools.adtui.ptable.PTableItem;
import com.android.tools.idea.uibuilder.property.AddPropertyItem;
import com.android.tools.idea.uibuilder.property.NlResourceHeader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.android.SdkConstants.*;
import static com.intellij.ui.SimpleTextAttributes.*;

public class NlXmlNameRenderer extends PTableCellRenderer implements PNameRenderer {
  public static final JBColor NAMESPACE_COLOR = new JBColor(new Color(128, 0, 128), new Color(151, 118, 169));
  public static final JBColor ATTRIBUTE_COLOR = new JBColor(new Color(0, 0, 255), Gray._192);
  public static final JBColor TAG_COLOR = new JBColor(new Color(0, 0, 128), new Color(180, 180, 0));
  public static final JBColor NEW_VALUE_COLOR = new JBColor(Gray._192, Gray._128);

  @Override
  protected void customizeCellRenderer(@NotNull PTable table, @NotNull PTableItem item,
                                       boolean selected, boolean hasFocus, int row, int col) {
    Color color = ATTRIBUTE_COLOR;
    String name = item.getName();
    if (item instanceof NlResourceHeader) {
      color = TAG_COLOR;
      if (!selected) {
        setBackground(JBColor.border());
      }
      setBorder(BorderFactory.createCompoundBorder(
          IdeBorderFactory.createBorder(SideBorder.BOTTOM),
          BorderFactory.createEmptyBorder(6, 0, 0, 0)));
    }
    else if (item instanceof AddPropertyItem && name.isEmpty()) {
      name = "+ name";
      color = NEW_VALUE_COLOR;
    }
    if (selected && hasFocus) {
      color = null;
    }
    SimpleTextAttributes attr = REGULAR_ATTRIBUTES.derive(STYLE_SMALLER | STYLE_BOLD, color, null, null);
    if (!StringUtil.isEmpty(item.getNamespace())) {
      color = selected && hasFocus ? null : NAMESPACE_COLOR;
      SimpleTextAttributes attrNamespace = attr.derive(-1, color, null, null);
      append(getNamespaceLabel(item.getNamespace()), attrNamespace, false);
      append(":", attr, false);
    }
    append(name, attr, true);
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
