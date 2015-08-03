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
package com.android.tools.idea.ui;

import com.android.tools.idea.ui.DocumentAccessor;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.TextAccessor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.Document;
import java.awt.*;

/**
 * A label with an "edit" link that turns it into a text button
 */
public class LabelWithEditLink extends JPanel implements DocumentAccessor  {
  private static final String EDIT_TEXT = "<html><a>Edit</a></html>";
  private static final String DONE_TEXT = "<html><a>Done</a></html>";
  public static final String DISPLAY = "Display";
  public static final String EDIT = "Edit";
  private JBLabel myContentLabel = new JBLabel();
  private HyperlinkLabel myEditLabel = new HyperlinkLabel();
  private CardLayout myCardLayout = new CardLayout();
  private JPanel myCardPanel = new JPanel(myCardLayout);
  private JTextField myEditField = new JTextField();

  private boolean myInEditMode = false;

  public LabelWithEditLink() {
    setLayout(new BorderLayout());
    myCardPanel.add(EDIT, myEditField);
    myCardPanel.add(DISPLAY, myContentLabel);
    myCardPanel.revalidate();
    myCardPanel.repaint();
    myCardLayout.show(myCardPanel, DISPLAY);

    add(myCardPanel, BorderLayout.CENTER);
    add(myEditLabel, BorderLayout.EAST);

    myContentLabel.setHorizontalTextPosition(SwingConstants.RIGHT);
    myEditLabel.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        if (HyperlinkEvent.EventType.ACTIVATED.equals(e.getEventType())) {
          toggleEdit();
        }
      }
    });
    myContentLabel.setForeground(JBColor.gray);
    setFont(UIUtil.getLabelFont());
    myEditLabel.setHtmlText(EDIT_TEXT);
  }

  private void toggleEdit() {
    if (myInEditMode) {
      myCardLayout.show(myCardPanel, DISPLAY);
      myEditLabel.setHtmlText(EDIT_TEXT);
      myContentLabel.setText(myEditField.getText());
    } else {
      myCardLayout.show(myCardPanel, EDIT);
      myEditLabel.setHtmlText(DONE_TEXT);
      myEditField.setText(myContentLabel.getText());
      myEditField.requestFocusInWindow();
    }
    myInEditMode = !myInEditMode;
  }

  public void setText(@NotNull String text) {
    myEditField.setText(text);
    myContentLabel.setText(text);
  }

  @NotNull
  public String getText() {
    if (myEditField.getText() != null) {
      return myEditField.getText();
    }
    if (myContentLabel.getText() != null) {
      return myContentLabel.getText();
    }
    return "";
  }

  @Override
  public void setFont(Font font) {
    if (font == null || myContentLabel == null) {
      return;
    }
    super.setFont(font);
    myContentLabel.setFont(font);
    float smallFontSize = font.getSize() - 1;
    if (smallFontSize <= 0) {
      smallFontSize = font.getSize();
    }
    Font smallerFont = font.deriveFont(smallFontSize);
    myEditLabel.setFont(smallerFont);
  }

  @NotNull
  public Document getDocument() {
    return myEditField.getDocument();
  }
}
