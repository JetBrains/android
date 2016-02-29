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
package com.android.tools.idea.devservices;

import com.android.tools.idea.structure.services.DeveloperService;
import com.android.tools.idea.structure.services.DeveloperServiceCreator;
import com.android.tools.idea.structure.services.DeveloperServiceCreators;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Panel for Developer Services on-boarding materials (e.g. introductions, tutorials, etc.)
 *
 * TODO: Refactor package to reflect current use, something like
 * "AssistantSidePanel" may be appropriate.
 */
public final class DeveloperServicesSidePanel extends JPanel {

  public DeveloperServicesSidePanel(@NotNull String actionId) {
    Component customPanel = null;

    // TODO: Move layout to a form.
    setLayout(new BorderLayout());
    setBorder(BorderFactory.createEmptyBorder());
    setOpaque(false);

    for (DeveloperServiceCreators creators : DeveloperServiceCreators.EP_NAME.getExtensions()) {
      if (creators.getBundleId().equals(actionId)) {
        customPanel = creators.getPanel();
        break;
      }
    }

    if (customPanel == null) {
      throw new RuntimeException("Unable to find configuration for the selected action: " + actionId);
    }
    add(customPanel);
  }
}
