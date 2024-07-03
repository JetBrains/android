/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.property;

import com.android.tools.adtui.common.AdtSecondaryPanel;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import javax.swing.Icon;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import org.jetbrains.annotations.NotNull;

/**
 * Panel for showing the selected tag in the Motion properties panel.
 */
public class SelectedTagPanel extends AdtSecondaryPanel {
  private static final int VERTICAL_BORDER = 2;
  private static final int HORIZONTAL_BORDER = 6;

  public SelectedTagPanel(@NotNull String label, @NotNull String id, @NotNull Icon icon, boolean includeTopBorder) {
    super(new BorderLayout());
    JBLabel component = new JBLabel(label, icon, SwingConstants.LEADING);
    JBLabel description = new JBLabel(id);
    component.setForeground(new JBColor(Gray._192, Gray._128));
    if (includeTopBorder) {
      Border border = JBUI.Borders.empty(VERTICAL_BORDER, HORIZONTAL_BORDER, 0, HORIZONTAL_BORDER);
      border = JBUI.Borders.merge(border,
                                  JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0), true);
      border = JBUI.Borders.merge(border,
                                  JBUI.Borders.emptyTop(VERTICAL_BORDER), true);
      setBorder(border);
    }
    else {
      setBorder(JBUI.Borders.empty(0, HORIZONTAL_BORDER));
    }
    add(component, BorderLayout.WEST);
    add(description, BorderLayout.EAST);
  }
}
