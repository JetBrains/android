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
package com.android.tools.idea.editors.strings;

import com.android.tools.idea.rendering.LocalResourceRepositoryAsVirtualFile;
import com.android.tools.idea.rendering.StringResourceData;
import com.android.tools.idea.rendering.StringResourceParser;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;

public class StringResourceEditor extends UserDataHolderBase implements FileEditor {
  public static final Icon ICON = AndroidIcons.Globe;
  public static final String NAME = "String Resource Editor";

  private final Project myProject;
  private final LocalResourceRepositoryAsVirtualFile myRepositoryFile;
  private final StringResourceViewPanel myViewPanel;

  private long myModificationCount;

  public StringResourceEditor(@NotNull Project project, @NotNull VirtualFile repositoryFile) {
    myProject = project;
    myRepositoryFile = (LocalResourceRepositoryAsVirtualFile) repositoryFile;
    myViewPanel = new StringResourceViewPanel();
    parseStringResourceDataInBackground();
  }

  private void parseStringResourceDataInBackground() {
    final Task.Modal parseTask = new ParseTask("Parsing string resource data");
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        parseTask.queue();
      }
    });
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myViewPanel.getComponent();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myViewPanel.getPreferredFocusedComponent();
  }

  @NotNull
  @Override
  public String getName() {
    return NAME;
  }

  @NotNull
  @Override
  public FileEditorState getState(@NotNull FileEditorStateLevel level) {
    return FileEditorState.INSTANCE;
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void selectNotify() {
    // TODO Doesn't refresh if a strings.xml file is deleted while the editor is visible
    if (myModificationCount != myRepositoryFile.getModificationCount()) {
      final Task.Modal parseTask = new ParseTask("Updating string resource data");
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          parseTask.queue();
        }
      });
    }
  }

  @Override
  public void deselectNotify() {
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Nullable
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }

  @Override
  public void dispose() {
  }

  private class ParseTask extends Task.Modal {
    private StringResourceData myData;

    public ParseTask(@NotNull String title) {
      super(StringResourceEditor.this.myProject, title, false);
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(true);
      myModificationCount = myRepositoryFile.getModificationCount();
      myData = StringResourceParser.parse(myRepositoryFile.getRepository());
    }

    @Override
    public void onSuccess() {
      myViewPanel.setStringResourceData(myData);
    }
  }
}
