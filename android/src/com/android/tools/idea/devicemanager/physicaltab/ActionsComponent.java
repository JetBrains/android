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
package com.android.tools.idea.devicemanager.physicaltab;

import com.android.tools.idea.devicemanager.TableCellRenderers;
import com.intellij.icons.AllIcons;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.scale.JBUIScale;
import java.awt.Component;
import javax.swing.AbstractButton;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ActionsComponent extends JBPanel<ActionsComponent> implements TableCellRenderer {
  private @Nullable AbstractButton myMoreButton;

  ActionsComponent() {
    super(null);

    initMoreButton();
    setLayout();
  }

  private void initMoreButton() {
    myMoreButton = new JButton(AllIcons.Actions.More);

    myMoreButton.setBorderPainted(false);
    myMoreButton.setContentAreaFilled(false);
  }

  private void setLayout() {
    GroupLayout layout = new GroupLayout(this);

    Group horizontalGroup = layout.createSequentialGroup()
      .addGap(0, 0, Short.MAX_VALUE)
      .addComponent(myMoreButton, GroupLayout.PREFERRED_SIZE, JBUIScale.scale(22), GroupLayout.PREFERRED_SIZE);

    Group verticalGroup = layout.createSequentialGroup()
      .addGap(0, 0, Short.MAX_VALUE)
      .addGroup(layout.createParallelGroup()
                  .addComponent(myMoreButton, GroupLayout.PREFERRED_SIZE, JBUIScale.scale(22), GroupLayout.PREFERRED_SIZE))
      .addGap(0, 0, Short.MAX_VALUE);

    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    setLayout(layout);
  }

  @Override
  public @NotNull Component getTableCellRendererComponent(@NotNull JTable table,
                                                          @NotNull Object value,
                                                          boolean selected,
                                                          boolean focused,
                                                          int viewRowIndex,
                                                          int viewColumnIndex) {
    setBackground(TableCellRenderers.getBackground(table, selected));
    setBorder(TableCellRenderers.getBorder(selected, focused));

    return this;
  }
}
