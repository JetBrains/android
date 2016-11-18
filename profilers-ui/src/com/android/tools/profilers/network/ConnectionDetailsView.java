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

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * View to display a single network request and its response's detailed information.
 */
public class ConnectionDetailsView extends JPanel {

  private final Splitter myResponseSplitter;
  private final JPanel myResponsePanel;
  private final JPanel myFieldsPanel;

  @Nullable
  private FileEditor myEditor;

  public ConnectionDetailsView() {
    myResponseSplitter = new Splitter(true);
    myResponsePanel = new JPanel();
    myResponseSplitter.setFirstComponent(myResponsePanel);
    myFieldsPanel = new JPanel();
    myFieldsPanel.setLayout(new BoxLayout(myFieldsPanel, BoxLayout.Y_AXIS));
    myResponseSplitter.setSecondComponent(myFieldsPanel);

    setLayout(new BorderLayout());
    add(myResponseSplitter, BorderLayout.CENTER);
  }

  /**
   * Updates the view to show given data, if the given {@code httpData} is null, clears the view.
   */
  public void update(@Nullable HttpData httpData) {
    setBackground(JBColor.background());
    myResponsePanel.removeAll();
    myFieldsPanel.removeAll();

    if (httpData == null) {
      return;
    }

    // TODO: Get payload file from id.
    VirtualFile testPayloadFile = null;
    if (testPayloadFile != null) {
      // TODO: Find proper project to refactor this.
      Project project = ProjectManager.getInstance().getDefaultProject();
      FileEditorProvider editorProvider = FileEditorProviderManager.getInstance().getProviders(project, testPayloadFile)[0];
      myEditor = editorProvider.createEditor(project, testPayloadFile);
      myResponsePanel.add(myEditor.getComponent());
    }

    myFieldsPanel.add(new JLabel(httpData.getUrl()));
    Map<String, String> responseFields = httpData.getHttpResponseFields();
    if (responseFields != null && responseFields.containsKey("content-type")) {
      myFieldsPanel.add(new JLabel(responseFields.get("content-type")));
    }

    myResponseSplitter.repaint();
  }
}
