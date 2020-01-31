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
package com.android.tools.adtui;

import com.intellij.ide.IdeTooltipManager;
import com.intellij.ui.HintHint;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
 * Copied from com/intellij/ui/GotItPanel but customized for Studio
 */
public class GotItPanel {
  public static final JBColor BACKGROUND = new JBColor(new Color(81, 135, 219), new Color(55,76,97));
  public static final String TEXT_COLOR = "#BBDDFF";

  JPanel myButton;
  JPanel myRoot;
  JLabel myTitle;
  JEditorPane myMessage;
  JPanel myMessagePanel;
  JLabel myButtonLabel;

  private void createUIComponents() {
    myButton = new JPanel(new BorderLayout()) {
      {
        enableEvents(AWTEvent.MOUSE_EVENT_MASK);
      }
    };

    myMessage = IdeTooltipManager.initPane("", new HintHint().setAwtTooltip(true), null);
    myMessage.setFont(UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().getSize() + 2f));
  }
}
