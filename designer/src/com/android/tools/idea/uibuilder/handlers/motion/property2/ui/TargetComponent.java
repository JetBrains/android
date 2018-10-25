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
package com.android.tools.idea.uibuilder.handlers.motion.property2.ui;

import com.android.tools.adtui.common.AdtSecondaryPanel;
import com.android.tools.idea.uibuilder.handlers.motion.property2.model.TargetModel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import org.jetbrains.annotations.NotNull;

/**
 * Simple component for displaying an NLComponent (with icon) and
 * a label for the context.
 */
public class TargetComponent extends AdtSecondaryPanel {

  public TargetComponent(@NotNull TargetModel model) {
    super(new BorderLayout());
    JBLabel component = new JBLabel();
    component.setIcon(model.getComponentIcon());
    component.setText(model.getComponentName());
    JBLabel description = new JBLabel();
    description.setText(model.getElementDescription());
    description.setForeground(JBColor.LIGHT_GRAY);
    setBorder(JBUI.Borders.empty(0, 6));
    add(component, BorderLayout.WEST);
    add(description, BorderLayout.EAST);
  }
}
