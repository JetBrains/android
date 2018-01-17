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
package com.android.tools.idea.ui.wizard;

import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

/**
 * A panel that provides a standard look and feel across wizard steps used in Android Studio.
 */
public final class StudioWizardStepPanel extends JPanel {

  private JPanel myRootPanel;

  public StudioWizardStepPanel(@NotNull JPanel innerPanel) {
    super(new BorderLayout());

    myRootPanel.add(innerPanel);
    add(myRootPanel);
  }

  /**
   * When creating a StudioWizardStepPanel which may be so tall as to require vertical scrolling,
   * using this helper method to automatically wrap it with an appropriate JScrollPane.
   */
  @NotNull
  public static JBScrollPane wrappedWithVScroll(@NotNull JPanel innerPanel) {
    JBScrollPane sp = new JBScrollPane(new StudioWizardStepPanel(innerPanel), VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
    sp.setBorder(BorderFactory.createEmptyBorder());
    return sp;
  }
}
