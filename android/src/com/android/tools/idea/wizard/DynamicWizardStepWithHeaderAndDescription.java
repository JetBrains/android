/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.wizard;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import java.awt.*;

import static com.android.tools.idea.wizard.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.ScopedStateStore.createKey;

/**
 * Base class for wizard steps with standard design.
 *
 * Subclasses should call {@link #setBodyComponent(javax.swing.JComponent)} from the constructor.
 */
public abstract class DynamicWizardStepWithHeaderAndDescription extends DynamicWizardStep {
  protected static final Key<String> KEY_DESCRIPTION =
    createKey(DynamicWizardStepWithHeaderAndDescription.class + ".description", ScopedStateStore.Scope.STEP, String.class);

  private JPanel myRootPane;
  private JBLabel myTitleLabel;
  private JBLabel myMessageLabel;
  private JBLabel myIcon;
  private JLabel myDescriptionText;
  private JBLabel myErrorWarningLabel;

  public DynamicWizardStepWithHeaderAndDescription(@NotNull String title, @Nullable String message, @Nullable Icon icon) {
    myTitleLabel.setText(title);
    myMessageLabel.setText(message);
    myIcon.setIcon(icon);
    myMessageLabel.setVisible(!StringUtil.isEmpty(message));
    int fontHeight = myMessageLabel.getFont().getSize();
    myTitleLabel.setBorder(BorderFactory.createEmptyBorder(fontHeight, 0, fontHeight, 0));
    myMessageLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, fontHeight, 0));
    Font font = myTitleLabel.getFont();
    if (font == null) {
      font = UIUtil.getLabelFont();
    }
    font = new Font(font.getName(), font.getStyle() | Font.BOLD, font.getSize() + 4);
    myTitleLabel.setFont(font);

    myErrorWarningLabel.setForeground(JBColor.red);
  }

  protected static CompoundBorder createBodyBorder() {
    int fontSize = UIUtil.getLabelFont().getSize();
    Border insetBorder = BorderFactory.createEmptyBorder(fontSize * 4, fontSize * 2, fontSize * 4, fontSize * 2);
    return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UIUtil.getBorderColor()), insetBorder);
  }

  protected final void setBodyComponent(JComponent component) {
    myRootPane.add(component, BorderLayout.CENTER);
  }

  @Override
  public void init() {
    register(KEY_DESCRIPTION, getDescriptionText(), new ComponentBinding<String, JLabel>() {
      @Override
      public void setValue(String newValue, @NotNull JLabel component) {
        setDescriptionText(newValue);
      }
    });
  }

  /**
   * Subclasses may override this method if they want to provide a custom description label.
   */
  protected JLabel getDescriptionText() {
    return myDescriptionText;
  }

  protected final void setDescriptionText(@Nullable String templateDescription) {
    getDescriptionText().setText(ImportUIUtil.makeHtmlString(templateDescription));
  }

  @NotNull
  @Override
  public final JComponent getComponent() {
    return myRootPane;
  }

  @NotNull
  @Override
  public final JBLabel getMessageLabel() {
    return myErrorWarningLabel;
  }
}
