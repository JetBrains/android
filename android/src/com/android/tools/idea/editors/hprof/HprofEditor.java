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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.status.InlineProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.io.File;

public class HprofEditor extends UserDataHolderBase implements FileEditor {
  @NotNull private static final Logger LOG = Logger.getInstance(HprofEditor.class);
  private final JPanel myPanel;
  private boolean myIsValid = true;

  public HprofEditor(@NotNull final Project project, @NotNull final VirtualFile file) {
    myPanel = new JPanel();
    parseHprofFileInBackground(project, file);
  }

  private void parseHprofFileInBackground(@NotNull final Project project, @NotNull final VirtualFile file) {
    TaskInfo taskInfo = new TaskInfo() {
      @NotNull
      @Override
      public String getTitle() {
        return "";
      }

      @Override
      public String getCancelText() {
        return null;
      }

      @Override
      public String getCancelTooltipText() {
        return null;
      }

      @Override
      public boolean isCancellable() {
        return false;
      }

      @Override
      public String getProcessId() {
        return null;
      }
    };

    final InlineProgressIndicator indicator = new InlineProgressIndicator(true, taskInfo) {
      @Override
      protected void queueProgressUpdate(Runnable update) {
        ApplicationManager.getApplication().invokeLater(update);
      }

      @Override
      protected void queueRunningUpdate(Runnable update) {
        ApplicationManager.getApplication().invokeLater(update);
      }
    };

    JPanel indicatorWrapper = new JPanel();
    indicatorWrapper.add(indicator.getComponent());
    myPanel.setLayout(new GridBagLayout());
    myPanel.add(indicatorWrapper);

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      private Snapshot mySnapshot;

      @Override
      public void run() {
        final File hprofFile = VfsUtilCore.virtualToIoFile(file);
        try {
          indicator.setFraction(0.0);
          indicator.setText("Parsing hprof file...");
          mySnapshot = new HprofParser(new MemoryMappedFileBuffer(hprofFile)).parse();

          indicator.setFraction(0.5);
          indicator.setText("Computing dominators...");
          mySnapshot.computeDominators();
        }
        catch (Throwable throwable) {
          LOG.info(throwable);
          //noinspection ThrowableResultOfMethodCallIgnored
          final String errorMessage = "Unexpected error while processing hprof file: " + Throwables.getRootCause(throwable).getMessage();
          indicator.cancel();
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              Messages.showErrorDialog(project, errorMessage, getName());
            }
          });
        }
        finally {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              myPanel.removeAll();
              myPanel.setLayout(new BorderLayout());
              if (mySnapshot != null) {
                myPanel.add(new HprofViewPanel(project, HprofEditor.this, mySnapshot).getComponent(), BorderLayout.CENTER);
              }
            }
          });
        }
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

  public void setInvalid() {
    myIsValid = false;
  }

  @Override
  public boolean isValid() {
    // TODO: handle deletion of the underlying file?
    return myIsValid;
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
