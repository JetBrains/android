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
package com.android.tools.adtui.stdui;

import com.android.tools.adtui.common.AdtUiUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * A simple tabbed pane
 */
public class CommonTabbedPane extends JTabbedPane {

  @NotNull private final CommonTabbedPaneUI myUi;

  enum ActionDirection {
    LEFT,
    RIGHT
  }

  final class NavigateAction extends AbstractAction {
    private final ActionDirection myDirection;

    NavigateAction(ActionDirection direction) {
      myDirection = direction;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      int tabCount = getTabCount();
      if (myDirection == ActionDirection.RIGHT) {
        setSelectedIndex((getSelectedIndex() + 1) % tabCount);
      }
      else {
        setSelectedIndex((getSelectedIndex() - 1 + tabCount) % tabCount);
      }
    }
  }

  public CommonTabbedPane() {
    myUi = new CommonTabbedPaneUI();
    setUI(myUi);
    setFont(AdtUiUtils.DEFAULT_FONT);
    getActionMap().put("navigatePrevious", new NavigateAction(ActionDirection.LEFT));
    getActionMap().put("navigateNext", new NavigateAction(ActionDirection.RIGHT));
    getActionMap().put("navigateLeft", new NavigateAction(ActionDirection.LEFT));
    getActionMap().put("navigateRight", new NavigateAction(ActionDirection.RIGHT));
    // Sets up mouse listeners to support hover state rendering.
  }

  @Override
  public void updateUI() {
    // Always set the UI back the our ui instance as the hover state functionality depends on it.
    setUI(myUi);
  }
}
