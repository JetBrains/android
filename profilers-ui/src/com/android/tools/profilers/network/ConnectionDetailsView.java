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
package com.android.tools.profilers.network;

import com.android.tools.adtui.TabularLayout;
import com.android.tools.profilers.ProfilerColors;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.labels.ActionLink;
import com.intellij.ui.components.labels.BoldLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * View to display a single network request and its response's detailed information.
 */
public class ConnectionDetailsView extends JPanel {
  private final JPanel myResponsePanel;
  private final JPanel myEditorPanel;
  private final JPanel myFieldsPanel;
  private final JPanel myCallStackView;

  @NotNull
  private final NetworkProfilerStageView myStageView;

  public ConnectionDetailsView(@NotNull NetworkProfilerStageView stageView) {
    super(new BorderLayout());
    myStageView = stageView;
    // Create 2x2 pane
    //     * Fit
    // Fit _ _
    // *   _ _
    //
    // where main contents span the whole area and a close button fits into the top right
    JPanel rootPanel = new JPanel(new TabularLayout("*,Fit", "Fit,*"));

    JBTabbedPane tabPanel = new JBTabbedPane();

    myResponsePanel = new JPanel(new BorderLayout());
    myEditorPanel = new JPanel(new BorderLayout());
    myResponsePanel.add(myEditorPanel, BorderLayout.CENTER);

    myFieldsPanel = new JPanel(new TabularLayout("Fit,20px,*").setVGap(10));
    JBScrollPane scrollPane = new JBScrollPane(myFieldsPanel);
    scrollPane.setBorder(BorderFactory.createEmptyBorder(5, 0, 10, 0));
    myResponsePanel.add(scrollPane, BorderLayout.SOUTH);

    myCallStackView = new JPanel(new VerticalFlowLayout());
    myCallStackView.setBackground(ProfilerColors.MONITOR_BACKGROUND);
    tabPanel.addTab("Response", myResponsePanel);
    tabPanel.addTab("Call Stack", new JBScrollPane(myCallStackView));

    IconButton closeIcon = new IconButton("Close", AllIcons.Actions.Close, AllIcons.Actions.CloseHovered);
    InplaceButton closeButton = new InplaceButton(closeIcon, e -> this.update((HttpData) null));
    closeButton.setMinimumSize(closeButton.getPreferredSize()); // Prevent layout phase from squishing this button

    rootPanel.add(closeButton, new TabularLayout.Constraint(0, 1));
    rootPanel.add(tabPanel, new TabularLayout.Constraint(0, 0, 2, 2));

    add(rootPanel);
  }

  /**
   * Updates the view to show given data. If given {@code httpData} is not null, show the details and set the view to be visible;
   * otherwise, clears the view and set view to be invisible.
   */
  public void update(@Nullable HttpData httpData) {
    setBackground(JBColor.background());
    myEditorPanel.removeAll();
    myFieldsPanel.removeAll();
    myCallStackView.removeAll();

    if (httpData != null) {
      JComponent fileViewer = myStageView.getIdeComponents().getFileViewer(httpData.getResponsePayloadFile());
      if (fileViewer != null) {
        myEditorPanel.add(fileViewer, BorderLayout.CENTER);
      }

      int row = 0;
      myFieldsPanel.add(new NoWrapBoldLabel("Request"), new TabularLayout.Constraint(row, 0));
      myFieldsPanel.add(new JLabel(HttpData.getUrlName(httpData.getUrl())), new TabularLayout.Constraint(row, 2));

      String contentType = httpData.getResponseField(HttpData.FIELD_CONTENT_TYPE);
      if (contentType != null) {
        row++;
        myFieldsPanel.add(new NoWrapBoldLabel("Content type"), new TabularLayout.Constraint(row, 0));
        // Content type looks like "type/subtype;" or "type/subtype; parameters".
        // Always convert to "type"
        contentType = contentType.split(";")[0];
        myFieldsPanel.add(new JLabel(contentType), new TabularLayout.Constraint(row, 2));
      }

      HyperlinkLabel urlLabel = new HyperlinkLabel(httpData.getUrl());
      urlLabel.setHyperlinkTarget(httpData.getUrl());
      row++;
      myFieldsPanel.add(new NoWrapBoldLabel("URL"), new TabularLayout.Constraint(row, 0));
      myFieldsPanel.add(urlLabel, new TabularLayout.Constraint(row, 2));

      String contentLength = httpData.getResponseField(HttpData.FIELD_CONTENT_LENGTH);
      if (contentLength != null) {
        contentLength = contentLength.split(";")[0];
        row++;
        myFieldsPanel.add(new NoWrapBoldLabel("Content length"), new TabularLayout.Constraint(row, 0));
        myFieldsPanel.add(new JLabel(contentLength), new TabularLayout.Constraint(row, 2));
      }
      for (String line : httpData.getTrace().split("\\n")) {
        JLabel label = new ActionLink(line, new AnAction() {
          @Override
          public void actionPerformed(AnActionEvent e) {
            myStageView.getIdeServices().navigateToStackTraceLine(line);
          }
        });
        myCallStackView.add(label);
      }

      repaint();
    }
    setVisible(httpData != null);
    revalidate();
  }

  @VisibleForTesting
  @Nullable
  Component getFileViewer() {
    return myEditorPanel.getComponentCount() > 0 ? myEditorPanel.getComponent(0) : null;
  }

  @VisibleForTesting
  JPanel getCallStackView() {
    return myCallStackView;
  }


  @VisibleForTesting
  int getFieldComponentIndex(String labelText) {
    for (int i = 0; i < myFieldsPanel.getComponentCount(); i+=2) {
      if (myFieldsPanel.getComponent(i) instanceof JLabel) {
        JLabel label = (JLabel) myFieldsPanel.getComponent(i);
        if (label.getText().contains(labelText)) {
          return i;
        }
      }
    }
    return -1;
  }

  @VisibleForTesting
  @Nullable
  Component getFieldComponent(int index) {
    return index >= 0 && index < myFieldsPanel.getComponentCount() ? myFieldsPanel.getComponent(index) : null;
  }

  private static final class NoWrapBoldLabel extends BoldLabel {
    public NoWrapBoldLabel(String text) {
      super("<nobr>" + text + "</nobr>");
    }
  }
}
