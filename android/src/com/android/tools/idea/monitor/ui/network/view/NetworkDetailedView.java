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
package com.android.tools.idea.monitor.ui.network.view;

import com.android.annotations.VisibleForTesting;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class NetworkDetailedView extends JPanel {

  private final Project myProject;
  private FileEditor myEditor;

  // TODO: Fix visual test and remove this constructor.
  @VisibleForTesting
  public NetworkDetailedView() {
    myProject = null;
  }

  public NetworkDetailedView(@NotNull Project project) {
    myProject = project;
    setPreferredSize(new Dimension(350, 0));
    setBorder(BorderFactory.createLineBorder(JBColor.black));
  }

  public void showConnectionDetails(@NotNull VirtualFile payloadFile) {
    if (myEditor != null) {
      remove(myEditor.getComponent());
    }
    FileEditorProvider editorProvider = FileEditorProviderManager.getInstance().getProviders(myProject, payloadFile)[0];
    myEditor = editorProvider.createEditor(myProject, payloadFile);
    add(myEditor.getComponent());
  }
}
