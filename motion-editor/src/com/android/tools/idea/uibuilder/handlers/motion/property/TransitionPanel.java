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
import com.intellij.util.ui.JBUI;
import icons.StudioIcons;
import java.awt.FlowLayout;
import javax.swing.JLabel;
import org.jetbrains.annotations.Nullable;

/**
 * Panel for showing a transition in the Motion properties panel.
 */
public class TransitionPanel extends AdtSecondaryPanel {
  private static final int TEXT_SPACER = 4;

  public TransitionPanel(@Nullable String start, @Nullable String end) {
    super(new FlowLayout(FlowLayout.CENTER, 0, 0));
    add(new JLabel(start));
    add(new JLabel(StudioIcons.LayoutEditor.Motion.TRANSITION));
    add(new JLabel(end));
    JLabel textLabel = new JLabel("Transition");
    textLabel.setForeground(new JBColor(Gray._192, Gray._128));
    textLabel.setBorder(JBUI.Borders.emptyLeft(TEXT_SPACER));
    add(textLabel);
    setBorder(JBUI.Borders.empty(0, 6));
  }
}
