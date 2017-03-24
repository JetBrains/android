/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.structure;

import com.android.tools.idea.uibuilder.lint.LintAnnotationsModel;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;

/**
 * Handler class responsible for drawing badges for each
 * row and handling a click on those badges
 */
public class NlTreeBadgeHandler {

  private static final int BADGE_MARGIN = 5;

  @Nullable private NlModel myNlModel;

  public void setNlModel(@Nullable NlModel nlModel) {
    myNlModel = nlModel;
  }

  public void paintBadges(@NotNull Graphics2D g, @NotNull JTree tree) {
    if (myNlModel == null) {
      return;
    }
    LintAnnotationsModel lintAnnotationsModel = myNlModel.getLintAnnotationsModel();
    if (lintAnnotationsModel == null) {
      return;
    }
    for (int i = 0; i < tree.getRowCount(); i++) {
      TreePath path = tree.getPathForRow(i);
      Icon icon = lintAnnotationsModel.getIssueIcon((NlComponent)path.getLastPathComponent(), false);
      Rectangle pathBounds = tree.getPathBounds(path);
      if (icon != null && pathBounds != null) {
        icon.paintIcon(tree, g, tree.getWidth() - icon.getIconWidth() - BADGE_MARGIN,
                       pathBounds.y + pathBounds.height / 2 - icon.getIconHeight() / 2);
      }
    }
  }
}
