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

import com.android.annotations.concurrency.UiThread;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.utils.HtmlBuilder;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.event.HyperlinkListener;
import org.jetbrains.annotations.NotNull;

/**
 * Form that displays either a {@link WiFiPairingContentPanel} or an error message, used when
 * ADB initialization failed.
 */
@UiThread
public class WiFiPairingCenterPanel {
  @NotNull private JPanel myContentPanel;
  @NotNull private JPanel myErrorContainerPanel;
  @NotNull private JEditorPane myErrorText;
  @NotNull private JPanel myRoot;
  @NotNull private JBScrollPane myScrollPane;
  @NotNull private JPanel myErrorTopPanel;
  @NotNull private JPanel myErrorBottomPanel;
  @NotNull private JPanel myErrorTextPanel;

  public WiFiPairingCenterPanel(HyperlinkListener hyperlinkListener) {
    setupUI();
    myContentPanel.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myErrorTextPanel.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myErrorText.setFont(AdtUiUtils.EMPTY_TOOL_WINDOW_FONT);
    myErrorText.setForeground(UIColors.ERROR_TEXT);
    myErrorText.addHyperlinkListener(hyperlinkListener);

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

  private void setupUI() {
    createUIComponents();
    myRoot = new JPanel();
    myRoot.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), 0, 0));
    myScrollPane = new JBScrollPane();
    myRoot.add(myScrollPane, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                 null, 0, false));
    myContentPanel = new JPanel();
    myContentPanel.setLayout(new BorderLayout(0, 0));
    myScrollPane.setViewportView(myContentPanel);
    myErrorContainerPanel = new JPanel();
    myErrorContainerPanel.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), 0, 0));
    myRoot.add(myErrorContainerPanel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                          null, null, null, 0, false));
    myErrorTopPanel = new JPanel();
    myErrorTopPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    myErrorContainerPanel.add(myErrorTopPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                   GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                   null, null, null, 0, false));
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setHorizontalAlignment(0);
    jBLabel1.setHorizontalTextPosition(0);
    jBLabel1.setText("");
    myErrorTopPanel.add(jBLabel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_WANT_GROW, null, null,
                                                      null, 0, false));
    myErrorTextPanel = new JPanel();
    myErrorTextPanel.setLayout(new GridLayoutManager(3, 3, new Insets(0, 0, 0, 0), -1, -1));
    myErrorContainerPanel.add(myErrorTextPanel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                    GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                    GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    myErrorText.setName("errorText");
    myErrorText.setText("");
    myErrorTextPanel.add(myErrorText, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                          null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    myErrorTextPanel.add(spacer1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                      GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    final Spacer spacer2 = new Spacer();
    myErrorTextPanel.add(spacer2, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                      GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    final Spacer spacer3 = new Spacer();
    myErrorTextPanel.add(spacer3, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1,
                                                      new Dimension(50, 0), null, null, 0, false));
    final Spacer spacer4 = new Spacer();
    myErrorTextPanel.add(spacer4, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1,
                                                      new Dimension(50, 0), null, null, 0, false));
    myErrorBottomPanel = new JPanel();
    myErrorBottomPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    myErrorContainerPanel.add(myErrorBottomPanel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                      null, null, null, 0, false));
    final JBLabel jBLabel2 = new JBLabel();
    jBLabel2.setHorizontalAlignment(0);
    jBLabel2.setHorizontalTextPosition(0);
    jBLabel2.setText("");
    myErrorBottomPanel.add(jBLabel2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                         GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_WANT_GROW, null, null,
                                                         null, 0, false));
  }
}
