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

import java.util.Optional;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.jetbrains.annotations.NotNull;

public final class DetailsPanelPanelListSelectionListener<D> implements ListSelectionListener {
  private final @NotNull DetailsPanelPanel<@NotNull D> myPanel;

  public DetailsPanelPanelListSelectionListener(@NotNull DetailsPanelPanel<@NotNull D> panel) {
    myPanel = panel;
  }

  @Override
  public void valueChanged(@NotNull ListSelectionEvent event) {
    if (event.getValueIsAdjusting()) {
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
