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
package com.android.tools.idea.devicemanager;

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Optional;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;

public final class DetailsPanelPanelMouseListener<D> extends MouseAdapter {
  private final @NotNull DetailsPanelPanel<@NotNull D> myPanel;

  public DetailsPanelPanelMouseListener(@NotNull DetailsPanelPanel<@NotNull D> panel) {
    myPanel = panel;
  }

  @Override
  public void mousePressed(@NotNull MouseEvent event) {
    if (!((Component)event.getSource()).isEnabled()) {
      return;
    }

    if (!SwingUtilities.isLeftMouseButton(event)) {
      return;
    }

    if (event.isConsumed()) {
      return;
    }

    Optional<D> device = myPanel.getSelectedDevice();

    if (!device.isPresent()) {
      return;
    }

    if (myPanel.containsDetailsPanel()) {
      myPanel.removeDetailsPanel();
    }

    myPanel.initDetailsPanel(device.get());
    myPanel.layOut();
  }
}
