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

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_TOP_BORDER;
import static com.android.tools.profilers.ProfilerLayout.createToolbarLayout;

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.model.AspectModel;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerLayout;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.instancefilters.CaptureObjectInstanceFilter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;

public class MemoryInstanceFilterView extends AspectModel<MemoryProfilerAspect> {
  @NotNull private final MemoryProfilerStage myStage;

  @NotNull private JPanel myFilterToolbar = new JPanel(createToolbarLayout());
  @NotNull private JPanel myFilterDescriptionPanel = new JPanel(new TabularLayout("Fit,*"));

  MemoryInstanceFilterView(@NotNull MemoryProfilerStage stage) {
    myStage = stage;

    myFilterDescriptionPanel.setBorder(JBUI.Borders.merge(ProfilerLayout.TOOLBAR_LABEL_BORDER, DEFAULT_TOP_BORDER, true));
    myFilterDescriptionPanel.setBackground(ProfilerColors.WARNING_BAR_COLOR);
    myFilterDescriptionPanel.setVisible(false);

    myStage.getAspect().addDependency(this).onChange(MemoryProfilerAspect.CURRENT_LOADED_CAPTURE, this::updateFilters);
  }

  @NotNull
  JComponent getFilterToolbar() {
    return myFilterToolbar;
  }

  JComponent getFilterDescription() {
    return myFilterDescriptionPanel;
  }

  private void updateFilters() {
    myFilterToolbar.removeAll();
    CaptureObject captureObject = myStage.getSelectedCapture();

    if (captureObject == null) {
      return;
    }

    for (CaptureObjectInstanceFilter supportedFilter : captureObject.getSupportedInstanceFilters()) {
      JBCheckBox filterCheckBox = new JBCheckBox(supportedFilter.getDisplayName());
      filterCheckBox.setBorder(new JBEmptyBorder(0, 4, 0, 4));
      filterCheckBox.setToolTipText(supportedFilter.getSummaryDescription());
      filterCheckBox.addActionListener(l -> {
        if (filterCheckBox.isSelected()) {
          captureObject.addInstanceFilter(supportedFilter, SwingUtilities::invokeLater);
          myStage.getStudioProfilers().getIdeServices().getFeatureTracker().trackMemoryProfilerInstanceFilter(supportedFilter);
        }
        else {
          captureObject.removeInstanceFilter(supportedFilter, SwingUtilities::invokeLater);
        }

        boolean hasFilterDescription = false;
        myFilterDescriptionPanel.removeAll();
        // Update the selected filter description.
        Set<CaptureObjectInstanceFilter> selectedFilters = captureObject.getSelectedInstanceFilters();
        int i = 0;
        for (CaptureObjectInstanceFilter filter : selectedFilters) {
          String description = filter.getDetailedDescription();
          if (description != null) {
            HyperlinkLabel label = new HyperlinkLabel();
            String docLink = filter.getDocumentationLink();
            if (docLink != null) {
              label.setHyperlinkText(description + " Please see the ", "documentation", " for details.");
              label.setHyperlinkTarget(docLink);
            }
            else {
              label.setHyperlinkText(description, "", "");
            }
            myFilterDescriptionPanel.add(label, new TabularLayout.Constraint(i++, 0));
            hasFilterDescription = true;
          }
        }

        myFilterDescriptionPanel.setVisible(hasFilterDescription);
      });
      myFilterToolbar.add(filterCheckBox);
    }
  }
}
