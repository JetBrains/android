/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.assistant;

import com.android.tools.idea.structure.services.DeveloperServiceCreators;
import com.android.tools.idea.structure.services.DeveloperServiceMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Panel for Developer Services on-boarding materials (e.g. introductions, tutorials, etc.)
 *
 * TODO: Refactor package to reflect current use, something like
 * "AssistantSidePanel" may be appropriate.
 */
public final class AssistSidePanel extends JPanel {

  public AssistSidePanel(@NotNull String actionId, @NotNull DeveloperServiceMap serviceMap) {
    super(new BorderLayout());
    setBorder(BorderFactory.createEmptyBorder());
    setOpaque(false);

    Component customPanel = null;

    for (DeveloperServiceCreators creators : DeveloperServiceCreators.EP_NAME.getExtensions()) {
      if (creators.getBundleId().equals(actionId)) {
        customPanel = creators.getPanel(serviceMap);
        break;
      }
    }

    if (customPanel == null) {
      throw new RuntimeException("Unable to find configuration for the selected action: " + actionId);
    }
    add(customPanel);
  }
}
