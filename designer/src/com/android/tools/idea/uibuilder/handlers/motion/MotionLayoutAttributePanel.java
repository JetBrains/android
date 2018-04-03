/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.api.AccessoryPanelInterface;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.surface.AccessoryPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

class MotionLayoutAttributePanel implements AccessoryPanelInterface {

  private final ViewGroupHandler.AccessoryPanelVisibility myVisibilityCallback;
  private final NlComponent myMotionLayout;
  private JPanel myPanel;

  public MotionLayoutAttributePanel(@NotNull NlComponent parent, @NotNull ViewGroupHandler.AccessoryPanelVisibility visibility) {
    myMotionLayout = parent;
    myVisibilityCallback = visibility;
  }

  @Override
  public @NotNull JPanel getPanel() {
    if (myPanel == null) {
      myPanel = createPanel(AccessoryPanel.Type.EAST_PANEL);
    }
    return myPanel;
  }

  @Override
  public @NotNull JPanel createPanel(@NotNull AccessoryPanel.Type type) {
    JPanel panel = new JPanel(new BorderLayout()) {
      {
        setPreferredSize(new Dimension(250, 250));
      }
    };

    JLabel label = new JLabel("ML Attribute Panel");
    panel.add(label, BorderLayout.CENTER);
    return panel;
  }

  @Override
  public void updateAccessoryPanelWithSelection(@NotNull AccessoryPanel.Type type, @NotNull List<NlComponent> selection) {

  }

  @Override
  public void deactivate() {

  }

}
