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
package com.android.tools.idea.uibuilder.api;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.surface.AccessoryPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * This is the interface to Accessory Panels they are returned by ViewGroupHandlers if they want to have a panel on the bottom of the design
 * surface.
 * It is used by the motionLayout handler to display the timeline.
 */
public interface AccessoryPanelInterface {

  /**
   * The panel to be put onto the user interface
   * @return
   */
  @NotNull JPanel getPanel();

  @NotNull JPanel createPanel(AccessoryPanel.Type type);

  /**
   * Give a chance for the handler to populate the accessory panel
   *
   * @param type      type of accessory panel
   * @param selection the current selected components
   */
  void updateAccessoryPanelWithSelection(@NotNull AccessoryPanel.Type type,
                                         @NotNull List<NlComponent> selection);

  /**
   * Called to inform the Panel that it is no longer in use.
   */
  void deactivate();

  void updateAfterModelDerivedDataChanged();

  /**
   * Returns the currently selected object to be displayed.
   */
  @Nullable
  Object getSelectedAccessory();

  /**
   * Returns the currently selected object type.
   */
  @Nullable
  Object getSelectedAccessoryType();

  void addListener(@NotNull AccessorySelectionListener listener);

  void removeListener(@NotNull AccessorySelectionListener listener);
}
