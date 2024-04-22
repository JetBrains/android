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
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.Utils;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import icons.StudioIcons;
import java.awt.BorderLayout;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.Nullable;

/**
 * Panel for showing a constraint set in the Motion properties panel.
 */
public class ConstraintSetPanel extends AdtSecondaryPanel {

  public ConstraintSetPanel(@Nullable String id) {
    super(new BorderLayout());
    String text = (id != null ? Utils.stripID(id) + " " : "") + "Constraint Set";
    JBLabel component = new JBLabel(text, StudioIcons.LayoutEditor.Motion.CONSTRAINT_SET, SwingConstants.CENTER);
    setBorder(JBUI.Borders.empty(0, 6));
    add(component, BorderLayout.CENTER);
  }
}
