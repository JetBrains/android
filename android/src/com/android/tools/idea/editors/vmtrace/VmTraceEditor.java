/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.editors.vmtrace;

import com.android.tools.perflib.vmtrace.VmTraceData;
import com.android.tools.perflib.vmtrace.VmTraceParser;
import com.google.common.base.Throwables;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.io.File;

public class VmTraceEditor implements FileEditor {
  private final TraceViewPanel myTraceViewPanel;

  public VmTraceEditor(@NotNull final Project project, @NotNull final VirtualFile file) {
    myTraceViewPanel = new TraceViewPanel(project);
    parseTraceFileInBackground(project, file);
  }

  private void parseTraceFileInBackground(@NotNull final Project project, @NotNull final VirtualFile file) {
    final Task.Modal parseTask = new Task.Modal(project, "Parsing trace file", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);

        File traceFile = VfsUtilCore.virtualToIoFile(file);
        VmTraceParser parser = new VmTraceParser(traceFile);
        try {
          parser.parse();
        }
        catch (final Throwable throwable) {
          ApplicationManager.getApplication().invokeAndWait(new Runnable() {
            @Override
            public void run() {
              //noinspection ThrowableResultOfMethodCallIgnored
              Messages.showErrorDialog(project, "Unexpected error while parsing trace file: " +
                                                Throwables.getRootCause(throwable).getMessage(), getName());
            }
          }, ModalityState.defaultModalityState());
          return;
        }

        final VmTraceData vmTraceData = parser.getTraceData();
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            myTraceViewPanel.setTrace(vmTraceData);
          }
        });
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
    return myTraceViewPanel.getComponent();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @NotNull
  @Override
  public String getName() {
    return "Traceview";
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

  @Nullable
  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return null;
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
  }
}
