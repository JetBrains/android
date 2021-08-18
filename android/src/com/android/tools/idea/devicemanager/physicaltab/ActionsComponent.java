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

import com.android.tools.idea.devicemanager.Buttons;
import com.android.tools.idea.flags.StudioFlags;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.ui.components.JBPanel;
import javax.swing.AbstractButton;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import org.jetbrains.annotations.NotNull;

final class ActionsComponent extends JBPanel<ActionsComponent> {
  private final @NotNull AbstractButton myActivateDeviceFileExplorerWindowButton;
  private final @NotNull AbstractButton myEditDeviceNameButton;
  private final @NotNull AbstractButton myRemoveButton;
  private final @NotNull AbstractButton myViewDetailsButton;
  private final @NotNull AbstractButton myMoreButton;

  ActionsComponent() {
    this(false);
  }

  @VisibleForTesting
  ActionsComponent(boolean addViewDetailsButton) {
    super(null);

    myActivateDeviceFileExplorerWindowButton = Buttons.newIconButton(AllIcons.General.OpenDiskHover);
    myEditDeviceNameButton = Buttons.newIconButton(AllIcons.Actions.Edit);
    myRemoveButton = Buttons.newIconButton(AllIcons.Actions.GC);
    myViewDetailsButton = Buttons.newIconButton(AllIcons.Actions.Preview);
    myMoreButton = Buttons.newIconButton(AllIcons.Actions.More);

    GroupLayout layout = new GroupLayout(this);

    Group horizontalGroup = layout.createSequentialGroup()
      .addGap(0, 0, Short.MAX_VALUE)
      .addComponent(myActivateDeviceFileExplorerWindowButton)
      // .addComponent(myEditDeviceNameButton)
      .addComponent(myRemoveButton);

    if (addViewDetailsButton) {
      horizontalGroup.addComponent(myViewDetailsButton);
    }

    boolean pairingAssistantEnabled = StudioFlags.WEAR_OS_VIRTUAL_DEVICE_PAIRING_ASSISTANT_ENABLED.get();

    if (pairingAssistantEnabled) {
      horizontalGroup.addComponent(myMoreButton);
    }

    Group group = layout.createParallelGroup()
      .addComponent(myActivateDeviceFileExplorerWindowButton)
      // .addComponent(myEditDeviceNameButton)
      .addComponent(myRemoveButton);

    if (addViewDetailsButton) {
      group.addComponent(myViewDetailsButton);
    }

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

  @NotNull AbstractButton getViewDetailsButton() {
    return myViewDetailsButton;
  }

  @NotNull AbstractButton getMoreButton() {
    return myMoreButton;
  }
}
