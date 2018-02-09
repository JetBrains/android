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
package com.android.tools.idea.assistant.view;


import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.assistant.AssistActionState;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Displays a message in lieu of a button when an action may not be completed. Note, this is not an extension of JBLabel as it will display
 * other elements such as an edit link and potentially support progress indication.
 */
public class StatefulButtonMessage extends JPanel {
  @VisibleForTesting
  @Nullable
  JBLabel myMessageDisplay;

  public StatefulButtonMessage(@NonNull String message, @NonNull AssistActionState state) {
    super(new GridBagLayout());
    setBorder(BorderFactory.createEmptyBorder());
    setOpaque(false);

    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 0.01;
    c.anchor = GridBagConstraints.NORTHWEST;

    if (state.getIcon() != null) {
      myMessageDisplay = new JBLabel();
      myMessageDisplay.setOpaque(false);
      myMessageDisplay.setIcon(state.getIcon());
      myMessageDisplay.setForeground(state.getForeground());
      add(myMessageDisplay, c);
      c.gridx++;
    }

    JEditorPane section = new JEditorPane();
    section.setOpaque(false);
    section.setBorder(BorderFactory.createEmptyBorder());
    section.setDragEnabled(false);
    UIUtils.setHtml(section, message, "body {color: " + UIUtils.getCssColor(state.getForeground()) + "}");
    c.fill = GridBagConstraints.HORIZONTAL;
    c.weightx = 0.99;
    add(section, c);
  }
}
