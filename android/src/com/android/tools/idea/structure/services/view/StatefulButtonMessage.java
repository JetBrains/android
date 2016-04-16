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
package com.android.tools.idea.structure.services.view;

import com.intellij.icons.AllIcons;
import com.intellij.ui.components.JBLabel;

import javax.swing.*;
import java.awt.*;

/**
 * Displays a message in lieu of a button when an action may not be completed. Note, this is not an extension of JBLabel as it will display
 * other elements such as an edit link and potentially support progress indication.
 *
 * TODO: Migrate the related classes when assist panel refactoring begins.
 * TODO: Add support for an edit action (not yet spec'd out).
 */
public class StatefulButtonMessage extends JPanel {
  public ActionState myState;
  private String myMessage;
  private String myEditAction;

  public StatefulButtonMessage(String message, ActionState state, String editAction) {
    super(new FlowLayout(FlowLayout.LEFT, 0, 0));
    setBorder(BorderFactory.createEmptyBorder());
    setOpaque(false);

    JBLabel messageDisplay = new JBLabel(message);
    messageDisplay.setOpaque(false);
    switch (state) {
      case SUCCESS:
        messageDisplay.setIcon(AllIcons.RunConfigurations.TestPassed);
        messageDisplay.setForeground(UIUtils.getSuccessColor());
        break;
      case ERROR:
        messageDisplay.setIcon(AllIcons.RunConfigurations.TestFailed);
        messageDisplay.setForeground(UIUtils.getFailureColor());
        break;
    }

    add(messageDisplay);
  }

  public enum ActionState {
    SUCCESS,
    ERROR,
    NOT_APPLICABLE
  }
}
