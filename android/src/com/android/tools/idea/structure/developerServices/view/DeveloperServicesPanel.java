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
package com.android.tools.idea.structure.developerServices.view;

import com.android.tools.idea.structure.EditorPanel;
import com.android.tools.idea.structure.developerServices.ServiceCategory;
import com.android.tools.idea.structure.developerServices.DeveloperService;
import com.android.tools.idea.structure.developerServices.DeveloperServices;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.SeparatorComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.border.TitledBorder;
import java.util.List;


/**
 * A vertical list of {@link DeveloperServicePanel}s.
 */
public final class DeveloperServicesPanel extends EditorPanel {

  // Keep a copy of service panel children so we can iterate over them directly
  private final List<DeveloperServicePanel> myPanels = Lists.newArrayList();

  public DeveloperServicesPanel(@NotNull Module module, @NotNull ServiceCategory serviceCategory) {
    super(new VerticalFlowLayout());

    for (DeveloperService service : DeveloperServices.getFor(module, serviceCategory)) {
      myPanels.add(new DeveloperServicePanel(service));
    }

    setBorder(new TitledBorder(serviceCategory.getDisplayName()));
    for (DeveloperServicePanel panel : myPanels) {
      if (getComponentCount() > 0) {
        add(new SeparatorComponent());
      }
      add(panel);
    }
  }

  @Override
  public void apply() {
    for (DeveloperServicePanel panel : myPanels) {
      panel.apply();
    }
  }

  @Override
  public boolean isModified() {
    for (DeveloperServicePanel panel : myPanels) {
      if (panel.isModified()) {
        return true;
      }
    }

    return false;
  }
}
