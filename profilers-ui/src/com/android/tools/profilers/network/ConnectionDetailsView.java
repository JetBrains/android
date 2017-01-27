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
import com.android.tools.profilers.common.StackTraceView;
import com.android.tools.profilers.common.TabsPanel;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.BoldLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

/**
 * View to display a single network request and its response's detailed information.
 */
public class ConnectionDetailsView extends JPanel {
  private static final int PAGE_VGAP = 32;
  private static final int SECTION_VGAP = 16;
  private static final int HGAP = 22;

  private final JPanel myEditorPanel;
  private final JPanel myFieldsPanel;
  private final JPanel myHeadersPanel;

  @NotNull
  private final StackTraceView myStackTraceView;

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

    TabsPanel tabsPanel = stageView.getIdeComponents().createTabsPanel();

    JPanel responsePanel = new JPanel(new BorderLayout());
    myEditorPanel = new JPanel(new BorderLayout());
    myEditorPanel.setBorder(BorderFactory.createEmptyBorder(PAGE_VGAP, HGAP, 0, HGAP));
    responsePanel.add(myEditorPanel, BorderLayout.CENTER);

    myFieldsPanel = new JPanel(new TabularLayout("Fit,20px,*").setVGap(SECTION_VGAP));
    myFieldsPanel.setBorder(BorderFactory.createEmptyBorder(PAGE_VGAP, HGAP, PAGE_VGAP, 0));
    JBScrollPane scrollPane = new JBScrollPane(myFieldsPanel);
    responsePanel.add(scrollPane, BorderLayout.SOUTH);

    tabsPanel.addTab("Response", responsePanel);

    myHeadersPanel = new JPanel(new VerticalFlowLayout(0, PAGE_VGAP));
    myHeadersPanel.setName("Headers");
    tabsPanel.addTab("Headers", new JBScrollPane(myHeadersPanel));

    myStackTraceView = myStageView.getIdeComponents().createStackView(null);
    tabsPanel.addTab("Call Stack", myStackTraceView.getComponent());

    IconButton closeIcon = new IconButton("Close", AllIcons.Actions.Close, AllIcons.Actions.CloseHovered);
    InplaceButton closeButton = new InplaceButton(closeIcon, e -> this.update((HttpData)null));
    closeButton.setMinimumSize(closeButton.getPreferredSize()); // Prevent layout phase from squishing this button

    rootPanel.add(closeButton, new TabularLayout.Constraint(0, 1));
    rootPanel.add(tabsPanel.getComponent(), new TabularLayout.Constraint(0, 0, 2, 2));

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
    myHeadersPanel.removeAll();
    myStackTraceView.clearStackFrames();

    JComponent fileViewer = getFileViewer(httpData);
    if (fileViewer != null) {
      myEditorPanel.add(fileViewer, BorderLayout.CENTER);
    }

    if (httpData != null) {
      updateFields(httpData);

      myHeadersPanel.add(createHeaderSection("Response Headers", httpData.getResponseHeaders()));
      myHeadersPanel.add(new JSeparator());
      myHeadersPanel.add(createHeaderSection("Request Headers", httpData.getRequestHeaders()));

      myStackTraceView.setStackFrames(httpData.getTrace());
      repaint();
    }
    setVisible(httpData != null);
    revalidate();
  }

  private void updateFields(@NotNull HttpData httpData) {
    int row = 0;
    myFieldsPanel.add(new NoWrapBoldLabel("Request"), new TabularLayout.Constraint(row, 0));
    myFieldsPanel.add(new JLabel(HttpData.getUrlName(httpData.getUrl())), new TabularLayout.Constraint(row, 2));

    row++;
    myFieldsPanel.add(new NoWrapBoldLabel("Method"), new TabularLayout.Constraint(row, 0));
    myFieldsPanel.add(new JLabel(httpData.getMethod()), new TabularLayout.Constraint(row, 2));

    if (httpData.getStatusCode() != HttpData.NO_STATUS_CODE) {
      row++;
      myFieldsPanel.add(new NoWrapBoldLabel("Status"), new TabularLayout.Constraint(row, 0));
      myFieldsPanel.add(new JLabel(String.valueOf(httpData.getStatusCode())), new TabularLayout.Constraint(row, 2));
    }

    if (httpData.getContentType() != null) {
      row++;
      myFieldsPanel.add(new NoWrapBoldLabel("Content type"), new TabularLayout.Constraint(row, 0));
      myFieldsPanel.add(new JLabel(httpData.getContentType().getMimeType()), new TabularLayout.Constraint(row, 2));
    }

    String contentLength = httpData.getResponseField(HttpData.FIELD_CONTENT_LENGTH);
    if (contentLength != null) {
      contentLength = contentLength.split(";")[0];
      row++;
      myFieldsPanel.add(new NoWrapBoldLabel("Content length"), new TabularLayout.Constraint(row, 0));
      myFieldsPanel.add(new JLabel(contentLength), new TabularLayout.Constraint(row, 2));
    }

    HyperlinkLabel urlLabel = new HyperlinkLabel(httpData.getUrl());
    urlLabel.setHyperlinkTarget(httpData.getUrl());
    row++;
    myFieldsPanel.add(new NoWrapBoldLabel("URL"), new TabularLayout.Constraint(row, 0));
    myFieldsPanel.add(urlLabel, new TabularLayout.Constraint(row, 2));
  }

  @NotNull
  private static JPanel createHeaderSection(@NotNull String title, @NotNull Map<String, String> map) {
    JPanel panel = new JPanel(new GridLayout(0, 1, 0, SECTION_VGAP));
    panel.setBorder(BorderFactory.createEmptyBorder(0, HGAP, 0, 0));

    JLabel titleLabel = new NoWrapBoldLabel(title);
    titleLabel.setFont(titleLabel.getFont().deriveFont(16.f));
    panel.add(titleLabel);

    if (map.isEmpty()) {
      JLabel emptyLabel = new JLabel("No data available");
      // TODO: Adjust color.
      panel.add(emptyLabel);
    } else {
      Map<String, String> sortedMap = new TreeMap<>(map);
      Border rowKeyBorder = BorderFactory.createEmptyBorder(0, 0, 0, HGAP);
      for (Map.Entry<String, String> entry : sortedMap.entrySet()) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JLabel keyLabel = new NoWrapBoldLabel(entry.getKey() + ":");
        keyLabel.setBorder(rowKeyBorder);
        row.add(keyLabel);
        row.add(new JLabel(entry.getValue()));
        row.setName(entry.getKey());
        panel.add(row);
      }
    }

    // Set name so tests can get a handle to this panel.
    panel.setName(title);
    return panel;
  }

  @VisibleForTesting
  @Nullable
  Component getFileViewer() {
    return myEditorPanel.getComponentCount() > 0 ? myEditorPanel.getComponent(0) : null;
  }

  @NotNull
  public StackTraceView getStackTraceView() {
    return myStackTraceView;
  }

  @VisibleForTesting
  int getFieldComponentIndex(String labelText) {
    for (int i = 0; i < myFieldsPanel.getComponentCount(); i += 2) {
      if (myFieldsPanel.getComponent(i) instanceof JLabel) {
        JLabel label = (JLabel)myFieldsPanel.getComponent(i);
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

  @Nullable
  private static JComponent getFileViewer(@Nullable HttpData httpData) {
    if (httpData == null || httpData.getResponsePayloadFile() == null) {
      return null;
    }

    String contentType = httpData.getResponseField(HttpData.FIELD_CONTENT_TYPE);
    if (contentType != null && StringUtil.containsIgnoreCase(contentType, "image")) {
      BufferedImage image = null;
      try {
        image = httpData.getResponsePayloadFile() != null ? ImageIO.read(httpData.getResponsePayloadFile()) : null;
      }
      catch (IOException ignored) {
      }
      return image != null ? new ImageComponent(image) : null;
    }

    // TODO: Fix the viewer for html, json and etc.
    JTextArea textArea = new JTextArea();
    try {
      BufferedReader reader = new BufferedReader(new FileReader(httpData.getResponsePayloadFile()));
      textArea.read(reader, null);
      reader.close();
    }
    catch (IOException ignored) {
    }
    textArea.setEditable(false);
    return new JBScrollPane(textArea);
  }

  private static final class ImageComponent extends JComponent {
    @NotNull
    private final Image myImage;

    public ImageComponent(@NotNull Image image) {
      myImage = image;
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Image scaledInstance = myImage.getScaledInstance(getParent().getWidth(), getParent().getHeight(), Image.SCALE_SMOOTH);
      g.drawImage(scaledInstance, 0, 0, this);
    }
  }
}
