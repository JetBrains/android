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
package com.android.tools.idea.editors.hprof;

import com.android.tools.perflib.heap.HprofParser;
import com.android.tools.perflib.heap.Snapshot;
import com.android.tools.perflib.heap.io.MemoryMappedFileBuffer;
import com.google.common.base.Throwables;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.io.*;

public class HprofEditor extends UserDataHolderBase implements FileEditor {

  private final HprofViewPanel myHprofViewPanel;

  public HprofEditor(@NotNull final Project project, @NotNull final VirtualFile file) {
    myHprofViewPanel = new HprofViewPanel(project);
    parseHprofFileInBackground(project, file);
  }

  private void parseHprofFileInBackground(final Project project, final VirtualFile file) {
    final Task.Modal parseTask = new Task.Modal(project, "Parsing hprof file", false) {
      private String myErrorMessage;
      private Snapshot mySnapshot;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);

        final File hprofFile = VfsUtilCore.virtualToIoFile(file);
        try {
          // Currently HprofParser takes too much memory and cripples the IDE.]
          // new HprofParser(new MemoryMappedFileBuffer(hprofFile)).parse();
          // TODO: Use HprofParser
          mySnapshot = new Snapshot(null);
        } catch(Throwable throwable){
          throwable.printStackTrace();
          //noinspection ThrowableResultOfMethodCallIgnored
          myErrorMessage = "Unexpected error while parsing hprof file: "
                           + Throwables.getRootCause(throwable).getMessage();
          throw new ProcessCanceledException();
        }
      }

      @Override
      public void onSuccess() {
        myHprofViewPanel.setSnapshot(mySnapshot);
      }

      @Override
      public void onCancel() {
        Messages.showErrorDialog(project, myErrorMessage, getName());
      }
    };
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
    return myHprofViewPanel.getComponent();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @NotNull
  @Override
  public String getName() {
    return "HprofView";
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
    // TODO: handle deletion of the underlying file?
    return true;
  }

  @Override
  public void selectNotify() {
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
}
