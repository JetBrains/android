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
package com.android.tools.adtui;

import com.intellij.ui.HyperlinkLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.Document;
import java.awt.*;

/**
 * A label with an "edit" button that turns it into a text field
 */
public class LabelWithEditButton extends JPanel implements DocumentAccessor  {
  private static final String EDIT_TEXT = "Edit";
  private static final String DONE_TEXT = "Done";

  private final JButton myButton = new JButton();
  private final JTextField myTextField = new JTextField() {
    @Override
    public Border getBorder() {
      // createEmptyBorder() always returns the same instance
      return isEnabled() ? super.getBorder() : BorderFactory.createEmptyBorder();
    }

    @Override
    public Color getBackground() {
      return (isEnabled() || getParent() == null) ? super.getBackground() : getParent().getBackground();
    }
  };

  public LabelWithEditButton() {
    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

    add(myTextField);
    add(myButton);

    // Start with "edit" and disabled
    myButton.setLabel(EDIT_TEXT);
    myTextField.setEnabled(false);

    myButton.addActionListener(e -> toggleEdit());

    setFont(UIUtil.getLabelFont());
  }

  private void toggleEdit() {
    boolean isEnabled = myTextField.isEnabled();
    myButton.setLabel(isEnabled ? EDIT_TEXT : DONE_TEXT);
    myTextField.setEnabled(!isEnabled);

    if (!isEnabled) {
      myTextField.requestFocusInWindow();
    }
  }

  @Override
  public void setText(@NotNull String text) {
    myTextField.setText(text);
  }

  @Override
  @NotNull
  public String getText() {
    return myTextField.getText();
  }

  @Override
  public void setFont(Font font) {
    super.setFont(font);

    if (font != null && myTextField != null) {
      myTextField.setFont(font);
    }
  }

  @Override
  @NotNull
  public Document getDocument() {
    return myTextField.getDocument();
  }
}
