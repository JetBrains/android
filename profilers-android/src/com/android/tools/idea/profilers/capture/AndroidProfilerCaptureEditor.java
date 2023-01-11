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
package com.android.tools.idea.profilers.capture;

import com.android.tools.idea.profilers.AndroidProfilerToolWindow;
import com.android.tools.idea.profilers.AndroidProfilerToolWindowFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import java.awt.BorderLayout;
import java.beans.PropertyChangeListener;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Opens an .hprof or .trace file in Android Profiler by creating an imported session. The editor is responsible for opening the
 * {@link AndroidProfilerToolWindow} and provide it a file so it can create the session. Finally, the editor should make sure it gets
 * closed, so users don't end up seeing an empty editor in their IDE.
 */
public class AndroidProfilerCaptureEditor implements FileEditor {

  /**
   * Sample empty panel as a not-null component is required by the editor. Shouldn't be visible, as the editor gets closed as we open
   * the file as a capture in Android Profiler.
   */
  private final JPanel myPanel;

  private final Project myProject;

  private final VirtualFile myFile;

  public AndroidProfilerCaptureEditor(@NotNull final Project project, @NotNull final VirtualFile file) {
    myProject = project;
    myFile = file;
    myPanel = new JPanel(new BorderLayout());
    importFileIntoAndroidProfiler(myProject, myFile);
  }

  private static void importFileIntoAndroidProfiler(@NotNull final Project project, VirtualFile file) {
    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(AndroidProfilerToolWindowFactory.ID);
    if (window != null) {
      window.setShowStripeButton(true);
      // Makes sure the window is visible because opening a file is an explicit indication that the user wants to view the file,
      // and for that we need the profiler window to be open.
      if (!window.isVisible()) {
        window.show(null);
      }
      AndroidProfilerToolWindow profilerToolWindow = AndroidProfilerToolWindowFactory.getProfilerToolWindow(project);
      if (profilerToolWindow != null) {
        profilerToolWindow.openFile(file);
      }
    }
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @NotNull
  @Override
  public VirtualFile getFile() {
    return myFile;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @NotNull
  @Override
  public String getName() {
    return "AndroidProfilerCaptureEditor";
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
    if (FileEditorManager.getInstance(myProject).isFileOpen(myFile)) {
      ApplicationManager.getApplication().invokeLater(() -> {
        // Captures should be open in the Android Profiler window, so make sure to close the panel when selecting this editor.
        FileEditorManager.getInstance(myProject).closeFile(myFile);
      });
    }
  }

  @Override
  public void deselectNotify() {
    FileEditor.super.deselectNotify();
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
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
