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
import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.labels.BoldLabel;
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
  private final JTextArea myCallstackView;

  public ConnectionDetailsView() {
    super(new BorderLayout());

    JBTabbedPane tabPanel = new JBTabbedPane();

    myResponsePanel = new JPanel(new BorderLayout());
    myEditorPanel = new JPanel(new BorderLayout());
    myResponsePanel.add(myEditorPanel, BorderLayout.CENTER);

    myFieldsPanel = new JPanel(new TabularLayout("Fit,20px,*").setVGap(10));
    JBScrollPane scrollPane = new JBScrollPane(myFieldsPanel);
    scrollPane.setBorder(BorderFactory.createEmptyBorder(5, 0, 10, 0));
    myResponsePanel.add(scrollPane, BorderLayout.SOUTH);

    myCallstackView = new JTextArea();
    myCallstackView.setEditable(false);

    tabPanel.addTab("Response", myResponsePanel);
    tabPanel.addTab("Call Stack", myCallstackView);

    IconButton closeIcon = new IconButton("Close", AllIcons.Actions.Close, AllIcons.Actions.CloseHovered);
    InplaceButton closeButton = new InplaceButton(closeIcon, e -> this.update((HttpData) null));

    // TODO: In a followup CL, update the logic so that the close button overlaps the tab panel.
    // So far I've tried using OverlapLayout as well as JLayeredPade+GridBadLayout, but both
    // approaches ended up being sensitive. Some changes to one of our own custom layout managers
    // may make this trivial, however.
    JPanel closePanel = new JPanel(new BorderLayout());
    closePanel.add(closeButton, BorderLayout.EAST);

    add(closePanel, BorderLayout.NORTH);
    add(tabPanel);
  }

  /**
   * Updates the view to show given data. If given {@code httpData} is not null, show the details and set the view to be visible;
   * otherwise, clears the view and set view to be invisible.
   */
  public void update(@Nullable HttpData httpData) {
    setBackground(JBColor.background());
    myEditorPanel.removeAll();
    myFieldsPanel.removeAll();
    myCallstackView.setText("");

    if (httpData != null) {
      VirtualFile payloadVirtualFile = httpData.getResponsePayloadFile() != null
                                       ? LocalFileSystem.getInstance().findFileByIoFile(httpData.getResponsePayloadFile()) : null;
      if (payloadVirtualFile != null) {
        // TODO: Find proper project to refactor this.
        Project project = ProjectManager.getInstance().getDefaultProject();
        FileEditorProvider editorProvider = FileEditorProviderManager.getInstance().getProviders(project, payloadVirtualFile)[0];
        FileEditor editor = editorProvider.createEditor(project, payloadVirtualFile);
        myEditorPanel.add(editor.getComponent(), BorderLayout.CENTER);
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

      // TODO: We are showing the callstack but we can't currently click on any of the links to
      // navigate to the code.
      myCallstackView.setText(httpData.getTrace());

      repaint();
    }
    setVisible(httpData != null);
    revalidate();
  }

  private static final class NoWrapBoldLabel extends BoldLabel {
    public NoWrapBoldLabel(String text) {
      super("<nobr>" + text + "</nobr>");
    }
  }
}
