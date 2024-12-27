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
package com.android.tools.idea.wizard.ui.deprecated;

import com.intellij.ui.components.JBLabel;
import java.util.Locale;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * A panel that provides a standard look and feel across wizard steps used in Android Studio.
 */
@Deprecated
public final class StudioWizardStepPanel extends JPanel {

  private JPanel myRootPanel;
  private JBLabel myDescriptionLabel;

  public StudioWizardStepPanel(@NotNull JPanel innerPanel, @Nullable String description) {
    super(new BorderLayout());
    setupUI();

    myDescriptionLabel.setText(description != null ? description : "");

    myRootPanel.add(innerPanel);
    add(myRootPanel);
  }

  private void setupUI() {
    myRootPanel = new JPanel();
    myRootPanel.setLayout(new BorderLayout(0, 15));
    myDescriptionLabel = new JBLabel();
    Font myDescriptionLabelFont = getFont(null, Font.BOLD, 16, myDescriptionLabel.getFont());
    if (myDescriptionLabelFont != null) myDescriptionLabel.setFont(myDescriptionLabelFont);
    myDescriptionLabel.setText("(Step description)");
    myRootPanel.add(myDescriptionLabel, BorderLayout.NORTH);
  }

  private Font getFont(String fontName, int style, int size, Font currentFont) {
    if (currentFont == null) return null;
    String resultName;
    if (fontName == null) {
      resultName = currentFont.getName();
    }
    else {
      Font testFont = new Font(fontName, Font.PLAIN, 10);
      if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
        resultName = fontName;
      }
      else {
        resultName = currentFont.getName();
      }
    }
    Font font = new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
    boolean isMac = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).startsWith("mac");
    Font fontWithFallback = isMac
                            ? new Font(font.getFamily(), font.getStyle(), font.getSize())
                            : new StyleContext().getFont(font.getFamily(), font.getStyle(), font.getSize());
    return fontWithFallback instanceof FontUIResource ? fontWithFallback : new FontUIResource(fontWithFallback);
  }
}
