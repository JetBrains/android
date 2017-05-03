/*
 * Copyright (C) 2016 The Android Open Source Project
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

import javax.swing.*;
import java.awt.*;

/**
 * Displays a message in lieu of a button when an action may not be completed. Note, this is not an extension of JBLabel as it will display
 * other elements such as an edit link and potentially support progress indication.
 */
public class StatefulButtonMessage extends JPanel {
  @VisibleForTesting
  @NonNull
  final JBLabel myMessageDisplay;

  public StatefulButtonMessage(@NonNull String message, @NonNull AssistActionState state) {
    super(new FlowLayout(FlowLayout.LEFT, 0, 0));
    setBorder(BorderFactory.createEmptyBorder());
    setOpaque(false);

    myMessageDisplay = new JBLabel(message);
    myMessageDisplay.setOpaque(false);
    myMessageDisplay.setIcon(state.getIcon());
    myMessageDisplay.setForeground(state.getForeground());
    add(myMessageDisplay);
  }
}
