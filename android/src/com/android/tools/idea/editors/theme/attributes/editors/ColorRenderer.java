/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.theme.attributes.editors;

import com.android.tools.idea.editors.theme.ThemeEditorContext;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.rendering.ResourceHelper;
import com.android.tools.swing.SwatchComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.List;

public class ColorRenderer implements TableCellRenderer {
  static final String LABEL_EMPTY = "(empty)";
  static final String LABEL_TEMPLATE = "<html><nobr><b><font color=\"#%1$s\">%2$s</font></b><font color=\"#9B9B9B\"> - %3$s</font>";
  static final JBColor DEFAULT_COLOR = new JBColor(new Color(0x6F6F6F)/*light*/,new Color(0xAAAAA)/*dark*/);

  private static final Logger LOG = Logger.getInstance(ColorRenderer.class);

  private final ResourceComponent myComponent;
  private final ThemeEditorContext myContext;

  public ColorRenderer(@NotNull ThemeEditorContext context) {
    myContext = context;
    myComponent = new ResourceComponent();
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object obj, boolean isSelected, boolean hasFocus, int row, int column) {
    if (obj instanceof EditedStyleItem) {
      final EditedStyleItem item = (EditedStyleItem) obj;
      final List<Color> colors = ResourceHelper.resolveMultipleColors(myContext.getResourceResolver(), item.getItemResourceValue());
      String colorText = colors.isEmpty() ? LABEL_EMPTY : ResourceHelper.colorToString(colors.get(0));
      myComponent.setSwatchIcons(SwatchComponent.colorListOf(colors));
      myComponent.setNameText(String.format(LABEL_TEMPLATE, DEFAULT_COLOR.toString(), item.getName(), colorText));
      myComponent.setValueText(item.getValue());
    } else {
      LOG.error(String.format("Object passed to ColorRendererEditor has class %1$s instead of ItemResourceValueWrapper", obj.getClass().getName()));
    }

    return myComponent;
  }
}
