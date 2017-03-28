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
 * A label with an "edit" link that turns it into a text button
 */
public class LabelWithEditLink extends JPanel implements DocumentAccessor  {
  private static final String EDIT_TEXT = "<html><a>Edit</a></html>";
  private static final String DONE_TEXT = "<html><a>Done</a></html>";

  private final HyperlinkLabel myLinkLabel = new HyperlinkLabel();
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

  public LabelWithEditLink() {
    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

    add(myTextField);
    add(myLinkLabel);

    // Start with "edit" and disabled
    myLinkLabel.setHtmlText(EDIT_TEXT);
    myTextField.setEnabled(false);

    myLinkLabel.addHyperlinkListener(e -> {
      if (HyperlinkEvent.EventType.ACTIVATED.equals(e.getEventType())) {
        toggleEdit();
      }
    });

    setFont(UIUtil.getLabelFont());
  }

  private void toggleEdit() {
    boolean isEnabled = myTextField.isEnabled();
    myLinkLabel.setHtmlText(isEnabled ? EDIT_TEXT : DONE_TEXT);
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

    if (font != null && myLinkLabel != null) {
      myTextField.setFont(font);

      float smallFontSize = font.getSize() - 1; // deriveFont() takes a float
      Font smallerFont = (smallFontSize <= 0) ? font : font.deriveFont(smallFontSize);
      myLinkLabel.setFont(smallerFont);
    }
  }

  @Override
  @NotNull
  public Document getDocument() {
    return myTextField.getDocument();
  }
}
