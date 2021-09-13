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

import com.android.tools.idea.devicemanager.IconButton;
import com.android.tools.idea.devicemanager.Tables;
import com.android.tools.idea.flags.StudioFlags;
import com.intellij.icons.AllIcons;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.scale.JBUIScale;
import java.awt.Color;
import java.awt.Component;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import javax.swing.AbstractButton;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.JTable;
import javax.swing.border.Border;
import org.jetbrains.annotations.NotNull;

final class ActionsComponent extends JBPanel<ActionsComponent> {
  private final @NotNull IconButton myActivateDeviceFileExplorerWindowButton;
  private final @NotNull AbstractButton myEditDeviceNameButton;
  private final @NotNull IconButton myRemoveButton;
  private final @NotNull IconButton myMoreButton;

  ActionsComponent() {
    super(null);

    myActivateDeviceFileExplorerWindowButton = new IconButton(AllIcons.Actions.MenuOpen);
    myEditDeviceNameButton = new IconButton(AllIcons.Actions.Edit);
    myRemoveButton = new IconButton(AllIcons.Actions.GC);
    myMoreButton = new IconButton(AllIcons.Actions.More);

    GroupLayout layout = new GroupLayout(this);
    int size = JBUIScale.scale(12);

    Group horizontalGroup = layout.createSequentialGroup()
      .addGap(0, 0, Short.MAX_VALUE)
      .addComponent(myActivateDeviceFileExplorerWindowButton)
      .addGap(size)
      // .addComponent(myEditDeviceNameButton)
      .addComponent(myRemoveButton);

    boolean pairingAssistantEnabled = StudioFlags.WEAR_OS_VIRTUAL_DEVICE_PAIRING_ASSISTANT_ENABLED.get();

    if (pairingAssistantEnabled) {
      horizontalGroup
        .addGap(size)
        .addComponent(myMoreButton);
    }

    Group group = layout.createParallelGroup()
      .addComponent(myActivateDeviceFileExplorerWindowButton)
      // .addComponent(myEditDeviceNameButton)
      .addComponent(myRemoveButton);

    if (pairingAssistantEnabled) {
      group.addComponent(myMoreButton);
    }

    Group verticalGroup = layout.createSequentialGroup()
      .addGap(0, 0, Short.MAX_VALUE)
      .addGroup(group)
      .addGap(0, 0, Short.MAX_VALUE);

    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    setLayout(layout);
  }

  @NotNull AbstractButton getActivateDeviceFileExplorerWindowButton() {
    return myActivateDeviceFileExplorerWindowButton;
  }

  @NotNull AbstractButton getEditDeviceNameButton() {
    return myEditDeviceNameButton;
  }

  @NotNull AbstractButton getRemoveButton() {
    return myRemoveButton;
  }

  @NotNull AbstractButton getMoreButton() {
    return myMoreButton;
  }

  @NotNull Component getTableCellComponent(@NotNull JTable table,
                                           boolean selected,
                                           boolean focused,
                                           int viewRowIndex,
                                           @NotNull BiFunction<@NotNull Boolean, @NotNull Boolean, @NotNull Border> getBorder) {
    boolean online = ((PhysicalDeviceTable)table).getDeviceAt(viewRowIndex).isOnline();
    Color foreground = Tables.getForeground(table, selected);

    myActivateDeviceFileExplorerWindowButton.setEnabled(online);
    myActivateDeviceFileExplorerWindowButton.setForeground(foreground);
    myActivateDeviceFileExplorerWindowButton.setSelectedInTableCell(selected);

    myRemoveButton.setEnabled(!online);
    myRemoveButton.setForeground(foreground);
    myRemoveButton.setSelectedInTableCell(selected);

    myMoreButton.setForeground(foreground);
    myMoreButton.setSelectedInTableCell(selected);

    setBackground(Tables.getBackground(table, selected));
    setBorder(getBorder.apply(selected, focused));

    return this;
  }

  @NotNull Optional<@NotNull Component> getFirstEnabledComponent(int startIndex) {
    return IntStream.range(startIndex, getComponentCount())
      .mapToObj(this::getComponent)
      .filter(Component::isEnabled)
      .findFirst();
  }

  @NotNull Optional<@NotNull Component> getLastEnabledComponent(int startIndex) {
    for (int j = startIndex; j >= 0; j--) {
      Component component = getComponent(j);

      if (!component.isEnabled()) {
        continue;
      }

      return Optional.of(component);
    }

    return Optional.empty();
  }

  @NotNull Optional<@NotNull Component> getFirstEnabledComponentAfterFocusOwner() {
    OptionalInt index = IntStream.range(0, getComponentCount())
      .filter(i -> getComponent(i).isFocusOwner())
      .findFirst();

    if (!index.isPresent()) {
      return Optional.empty();
    }

    return getFirstEnabledComponent(index.getAsInt() + 1);
  }

  @NotNull Optional<@NotNull Component> getFirstEnabledComponentBeforeFocusOwner() {
    int i = getComponentCount() - 1;

    for (; i >= 0; i--) {
      if (getComponent(i).isFocusOwner()) {
        break;
      }
    }

    assert i >= 0;
    return getLastEnabledComponent(i - 1);
  }
}
