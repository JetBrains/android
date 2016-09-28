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
package com.android.tools.idea.editors.allocations;

import com.android.ddmlib.AllocationInfo;
import com.android.ddmlib.AllocationsParser;
import com.android.ddmlib.ByteBufferUtil;
import com.google.common.base.Throwables;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.progress.ProcessCanceledException;
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
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AllocationsEditor implements FileEditor {
  private final JPanel myPanel;

  public AllocationsEditor(@NotNull final Project project, @NotNull final VirtualFile file) {
    myPanel = new JPanel(new BorderLayout());
    parseAllocationsFileInBackground(project, file);
  }

  private void parseAllocationsFileInBackground(final Project project, final VirtualFile file) {
    final Task.Modal parseTask = new Task.Modal(project, "Parsing allocations file", false) {
      private AllocationInfo[] myAllocations;
      private String myErrorMessage;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);

        final File allocationsFile = VfsUtilCore.virtualToIoFile(file);
        ByteBuffer data;
        try {
          data = ByteBufferUtil.mapFile(allocationsFile, 0, ByteOrder.BIG_ENDIAN);
        } catch (IOException ex) {
          myErrorMessage = "Error reading from allocations file " + allocationsFile.getAbsolutePath();
          throw new ProcessCanceledException();
        }

        try {
          myAllocations = AllocationsParser.parse(data);
        }
        catch (final Throwable throwable) {
          //noinspection ThrowableResultOfMethodCallIgnored
          myErrorMessage = "Unexpected error while parsing allocations file: " + Throwables.getRootCause(throwable).getMessage();
          throw new ProcessCanceledException();
        }
      }

      @Override
      public void onSuccess() {
        AllocationsView view = new AllocationsView(project, myAllocations);
        myPanel.add(view.getComponent(), BorderLayout.CENTER);
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
    return myPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @NotNull
  @Override
  public String getName() {
    return "AllocationsView";
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