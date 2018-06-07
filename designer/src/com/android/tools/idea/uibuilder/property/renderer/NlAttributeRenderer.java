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
package com.android.tools.idea.uibuilder.property.renderer;

import com.android.tools.adtui.common.AdtSecondaryPanel;
import com.android.tools.adtui.ptable.PTable;
import com.android.tools.adtui.ptable.PTableCellRenderer;
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.BrowsePanel;
import com.android.tools.idea.uibuilder.property.editors.NlTableCellEditor;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Set;

public abstract class NlAttributeRenderer extends PTableCellRenderer {
  private final JPanel myPanel;
  private final BrowsePanel myBrowsePanel;

  public NlAttributeRenderer() {
    myBrowsePanel = new BrowsePanel();
    myPanel = new AdtSecondaryPanel(new BorderLayout(SystemInfo.isMac ? 0 : 2, 0));
    myPanel.add(this, BorderLayout.CENTER);
    myPanel.add(myBrowsePanel, BorderLayout.LINE_END);
  }

  public JPanel getContentPanel() {
    return myPanel;
  }

  @Override
  public Component getTableCellRendererComponent(@NotNull JTable table, @NotNull Object value,
                                                 boolean isSelected, boolean hasFocus, int row, int col) {
    if (!(table instanceof PTable)) {
      return myPanel;
    }

    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);

    myPanel.setForeground(getForeground());
    myPanel.setBackground(getBackground());
    myBrowsePanel.setForeground(getForeground());
    myBrowsePanel.setBackground(getBackground());

    boolean hover = ((PTable)table).isHover(row, col);
    myBrowsePanel.setVisible(hover);
    myBrowsePanel.setDesignState(NlTableCellEditor.getDesignState(table, row));
    if (value instanceof NlProperty) {
      myBrowsePanel.setProperty((NlProperty)value);
    }

    return myPanel;
  }

  public abstract boolean canRender(@NotNull NlProperty property, @NotNull Set<AttributeFormat> formats);

  public void mousePressed(@NotNull MouseEvent event, @NotNull Rectangle rectRightColumn) {
    myBrowsePanel.mousePressed(event, rectRightColumn);
  }

  public void mouseMoved(@NotNull PTable table, @NotNull MouseEvent event, @NotNull Rectangle rectRightColumn) {
    myBrowsePanel.mouseMoved(table, event, rectRightColumn);
  }
}
