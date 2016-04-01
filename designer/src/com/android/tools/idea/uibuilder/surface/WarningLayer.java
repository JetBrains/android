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
package com.android.tools.idea.uibuilder.surface;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.tools.idea.uibuilder.lint.LintAnnotationsModel;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.SwingCoordinate;
import com.google.common.collect.Lists;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * The {@link WarningLayer} paints an icon over each view that contains at least one
 * lint error (unless the view is smaller than the icon)
 */
public class WarningLayer extends Layer {
  public static final int PADDING = 5;
  private final ScreenView myScreenView;
  private final List<NlComponent> myAnnotatedComponents = Lists.newArrayList();

  public WarningLayer(@NotNull ScreenView screenView) {
    myScreenView = screenView;
  }

  @Override
  public boolean paint(@NotNull Graphics2D gc) {
    myAnnotatedComponents.clear();

    LintAnnotationsModel lintModel = myScreenView.getModel().getLintAnnotationsModel();
    if (lintModel == null) {
      return false;
    }

    for (NlComponent component : lintModel.getComponentsWithIssues()) {
      if (!component.isShowing()) {
        continue;
      }

      Icon icon = lintModel.getIssueIcon(component);
      if (icon == null) {
        continue;
      }

      int x = Coordinates.getSwingX(myScreenView, component.x);
      int y = Coordinates.getSwingY(myScreenView, component.y);
      int w = Coordinates.getSwingDimension(myScreenView, component.w);

      // paint the icon at the top right corner of the component
      icon.paintIcon(null, gc, x + w - icon.getIconWidth() - PADDING, y + PADDING);
      myAnnotatedComponents.add(component);
    }
    return false;
  }

  @Nullable
  @Override
  public String getTooltip(@SwingCoordinate int mx, @SwingCoordinate int my) {
    LintAnnotationsModel lintModel = myScreenView.getModel().getLintAnnotationsModel();
    if (lintModel == null) {
      return null;
    }

    // linear search through all the components with annotations
    for (NlComponent component : myAnnotatedComponents) {
      Icon icon = lintModel.getIssueIcon(component);
      if (icon == null) {
        continue;
      }

      int x = Coordinates.getSwingX(myScreenView, component.x);
      int y = Coordinates.getSwingY(myScreenView, component.y);
      int w = Coordinates.getSwingDimension(myScreenView, component.w);

      if (mx > (x + w - icon.getIconWidth() - PADDING) && mx < (x + w - PADDING)) {
        if (my > (y + PADDING) && my < (y + PADDING + icon.getIconHeight())) {
          // TODO: show all messages?
          return lintModel.getIssueMessage(component);
        }
      }
    }

    return null;
  }
}
