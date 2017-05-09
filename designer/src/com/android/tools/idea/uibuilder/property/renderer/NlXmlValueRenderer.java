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

import com.android.tools.adtui.ptable.PTable;
import com.android.tools.adtui.ptable.PTableCellRenderer;
import com.android.tools.adtui.ptable.PTableItem;
import com.android.tools.idea.uibuilder.property.AddPropertyItem;
import com.android.tools.idea.uibuilder.property.NlResourceHeader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.SideBorder;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.android.tools.idea.uibuilder.property.renderer.NlXmlNameRenderer.NEW_VALUE_COLOR;
import static com.intellij.ui.SimpleTextAttributes.*;

public class NlXmlValueRenderer extends PTableCellRenderer {
  public static final JBColor VALUE_COLOR = new JBColor(new Color(0, 128, 80), new Color(98, 150, 85));

  @Override
  protected void customizeCellRenderer(@NotNull PTable table, @NotNull PTableItem item,
                                       boolean selected, boolean hasFocus, int row, int column) {
    if (item instanceof NlResourceHeader) {
      setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
      if (!selected) {
        setBackground(JBColor.border());
      }
    }
    String value = item.getValue();
    Color color = VALUE_COLOR;
    if (StringUtil.isEmpty(value) && item instanceof AddPropertyItem) {
      value = "value";
      color = NEW_VALUE_COLOR;
    }
    if (selected && hasFocus) {
      color = null;
    }
    if (value == null) {
      return;
    }
    SimpleTextAttributes attr = REGULAR_ATTRIBUTES.derive(STYLE_SMALLER | STYLE_BOLD, color, null, null);
    append(value, attr, true);
  }
}
