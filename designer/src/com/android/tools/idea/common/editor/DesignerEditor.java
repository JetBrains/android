/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.editor;

import com.android.tools.idea.common.lint.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import java.beans.PropertyChangeListener;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a generic designer editor. Subclasses are specific editors (e.g. navigation, layout), and should have their own ID (specified
 * through {@link #getEditorId()}) and create their {@link DesignerEditorPanel} using {@link #createEditorPanel()}.
 */
public abstract class DesignerEditor extends UserDataHolderBase implements FileEditor, SplitEditorPreviewNotificationHandler {

  protected final Project myProject;
  protected final VirtualFile myFile;

  private DesignerEditorPanel myEditorPanel;
  private BackgroundEditorHighlighter myBackgroundHighlighter;

  public DesignerEditor(@NotNull VirtualFile file, @NotNull Project project) {
    myProject = project;
    myFile = file;
  }

  @NotNull
  public abstract String getEditorId();

  @NotNull
  protected abstract DesignerEditorPanel createEditorPanel();

  @NotNull
  @Override
  public DesignerEditorPanel getComponent() {
    if (myEditorPanel == null) {
      myEditorPanel = createEditorPanel();
      Disposer.register(this, myEditorPanel);
    }
    return myEditorPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return getComponent().getPreferredFocusedComponent();
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
  }

  @Override
  public void dispose() {
  }

  @Override
  public boolean isValid() {
    return myFile.isValid();
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @NotNull
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    // The designer should display components that have problems detected by inspections. Ideally, we'd just get the result
    // of all the inspections on the XML file. However, it doesn't look like there is an API to obtain this for a file
    // (there are test APIs). So we add a single highlighter which uses lint.
    if (myBackgroundHighlighter == null) {
      myBackgroundHighlighter = new BackgroundEditorHighlighter(myEditorPanel, myEditorPanel.getModelLintIssueAnnotator());
    }
    return myBackgroundHighlighter;
  }

  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }

  @Nullable
  @Override
  public VirtualFile getFile() {
    return myFile;
  }

  @Override
  public void updateNotifications() {
    DesignerEditorPanel currentPanel = myEditorPanel;
    if (currentPanel != null) {
      currentPanel.updateNotifications(getFile(), this, myProject);
    }
  }
}
