/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.adb.wireless;

import com.android.tools.adtui.common.AdtUiUtils;
import com.android.utils.HtmlBuilder;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import org.jetbrains.annotations.NotNull;

public class PairingCenterPanel {
  @NotNull private JPanel myContentPanel;
  @NotNull private JPanel myErrorContainerPanel;
  @NotNull private JEditorPane myErrorText;
  @NotNull private JPanel myRoot;
  @NotNull private JBScrollPane myScrollPane;
  private JPanel myErrorTopPanel;
  private JPanel myErrorBottomPanel;
  private JPanel myErrorTextPanel;

  public PairingCenterPanel() {
    myContentPanel.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myErrorTextPanel.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myErrorText.setFont(AdtUiUtils.EMPTY_TOOL_WINDOW_FONT);
    myErrorText.setForeground(UIColors.ERROR_TEXT);

    Border line = new CustomLineBorder(UIColors.ONE_PIXEL_DIVIDER, 1, 0, 0, 0);
    Border c = new CompoundBorder(line, JBUI.Borders.empty(5, 0));
    myErrorTopPanel.setBorder(c);
    myErrorTopPanel.setMinimumSize(new JBDimension(0, 30));

    Border line2 = new CustomLineBorder(UIColors.ONE_PIXEL_DIVIDER, 0, 0, 1, 0);
    Border c2 = new CompoundBorder(line2, JBUI.Borders.empty(5, 0));
    myErrorBottomPanel.setBorder(c2);
    myErrorBottomPanel.setMinimumSize(new JBDimension(0, 30));

    myErrorTextPanel.setBorder(new CustomLineBorder(UIColors.ONE_PIXEL_DIVIDER, 1, 1, 1, 1));
  }

  @NotNull
  public JComponent getComponent() {
    return myRoot;
  }

  public void setContentComponent(@NotNull JComponent component) {
    myContentPanel.removeAll();
    myContentPanel.add(component, BorderLayout.CENTER);
  }

  public void showEmptyContent() {
    showError(new HtmlBuilder());
  }

  public void showError(@NotNull HtmlBuilder html) {
    EditorPaneUtils.setHtml(myErrorText, html, null);
    myScrollPane.setVisible(false);
    myErrorContainerPanel.setVisible(true);
  }

  public void showContent() {
    myErrorText.setText("");
    myScrollPane.setVisible(true);
    myErrorContainerPanel.setVisible(false);
  }

  private void createUIComponents() {
    myErrorText = EditorPaneUtils.createHtmlEditorPane();
  }
}
