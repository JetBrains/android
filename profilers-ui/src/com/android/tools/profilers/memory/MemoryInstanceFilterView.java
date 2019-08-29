/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.memory;

import static com.android.tools.profilers.ProfilerLayout.createToolbarLayout;

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.instancefilters.CaptureObjectInstanceFilter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.JBEmptyBorder;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;

public class MemoryInstanceFilterView extends AspectObserver {
  @NotNull private final MemoryProfilerStage myStage;


  @NotNull private JPanel myFilterToolbar = new JPanel(createToolbarLayout());

  MemoryInstanceFilterView(@NotNull MemoryProfilerStage stage) {
    myStage = stage;

    myStage.getAspect().addDependency(this).onChange(MemoryProfilerAspect.CURRENT_LOADED_CAPTURE, this::updateFilters);
  }

  @NotNull
  JComponent getComponent() {
    return myFilterToolbar;
  }

  private void updateFilters() {
    myFilterToolbar.removeAll();
    CaptureObject captureObject = myStage.getSelectedCapture();

    if (captureObject == null) {
      return;
    }

    for (CaptureObjectInstanceFilter supportedFilter : captureObject.getSupportedInstanceFilters()) {
      JBCheckBox filterCheckBox = new JBCheckBox(supportedFilter.getDisplayName());
      filterCheckBox.setBorder(new JBEmptyBorder(0, 2, 0, 2));
      filterCheckBox.setToolTipText(supportedFilter.getDescription());
      filterCheckBox.addActionListener(l -> {
        if (filterCheckBox.isSelected()) {
          captureObject.addInstanceFilter(supportedFilter, SwingUtilities::invokeLater);
        }
        else {
          captureObject.removeInstanceFilter(supportedFilter, SwingUtilities::invokeLater);
        }
      });
      myFilterToolbar.add(filterCheckBox);
    }
  }
}
