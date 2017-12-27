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
package com.android.tools.adtui.workbench;

import com.android.annotations.VisibleForTesting;
import com.intellij.ide.actions.ToggleDistractionFreeModeAction;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * All {@link WorkBench}es of a specified name will use the same {@link DetachedToolWindow}
 * for a given tool window name.
 * This class is responsible for switching the content to the content of the currently
 * active {@link WorkBench}.
 */
public class DetachedToolWindowManager implements ProjectComponent {
  private final Application myApplication;
  private final Project myProject;
  private final FileEditorManager myEditorManager;
  private final Map<FileEditor, WorkBench> myWorkBenchMap;
  private final HashMap<String, DetachedToolWindow> myToolWindowMap;
  private DetachedToolWindowFactory myDetachedToolWindowFactory;
  private FileEditor myLastSelectedEditor;

  public static DetachedToolWindowManager getInstance(@NotNull Project project) {
    return project.getComponent(DetachedToolWindowManager.class);
  }

  public DetachedToolWindowManager(@NotNull Application application,
                                   @NotNull Project currentProject,
                                   @NotNull FileEditorManager fileEditorManager) {
    myApplication = application;
    myProject = currentProject;
    myEditorManager = fileEditorManager;
    myWorkBenchMap = new IdentityHashMap<>(13);
    myToolWindowMap = new HashMap<>(8);
    //noinspection unchecked
    myDetachedToolWindowFactory = DetachedToolWindow::new;
  }

  @VisibleForTesting
  void setDetachedToolWindowFactory(@NotNull DetachedToolWindowFactory factory) {
    myDetachedToolWindowFactory = factory;
  }

  public void register(@Nullable FileEditor fileEditor, @NotNull WorkBench workBench) {
    if (fileEditor != null) {
      myWorkBenchMap.put(fileEditor, workBench);
      if (fileEditor == myLastSelectedEditor) {
        updateToolWindowsForWorkBench(workBench);
      }
    }
  }

  public void unregister(@Nullable FileEditor fileEditor) {
    if (fileEditor != null) {
      myWorkBenchMap.remove(fileEditor);
    }
  }

  @NotNull
  @Override
  public String getComponentName() {
    return DetachedToolWindowManager.class.getSimpleName();
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  @Override
  public void projectOpened() {
    myProject.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new MyFileEditorManagerListener());
  }

  @Override
  public void projectClosed() {
    for (DetachedToolWindow detachedToolWindow : myToolWindowMap.values()) {
      detachedToolWindow.updateSettingsInAttachedToolWindow();
    }
  }

  @Nullable
  private WorkBench getActiveWorkBench() {
    Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (focusOwner instanceof WorkBench) {
      return (WorkBench)focusOwner;
    }
    WorkBench current = (WorkBench)SwingUtilities.getAncestorOfClass(WorkBench.class, focusOwner);
    if (current != null) {
      return current;
    }
    FileEditor[] selectedEditors = myEditorManager.getSelectedEditors();
    if (selectedEditors.length == 0) {
      return null;
    }
    if (selectedEditors.length == 1) {
      return myWorkBenchMap.get(selectedEditors[0]);
    }
    if (focusOwner != null) {
      for (FileEditor editor : selectedEditors) {
        if (SwingUtilities.isDescendingFrom(focusOwner, editor.getComponent())) {
          return myWorkBenchMap.get(editor);
        }
      }
    }
    return myWorkBenchMap.get(selectedEditors[0]);
  }

  public void updateToolWindowsForWorkBench(@Nullable WorkBench workBench) {
    Set<String> ids = new HashSet<>(myToolWindowMap.keySet());
    if (workBench != null) {
      //noinspection unchecked
      List<AttachedToolWindow> detachedToolWindows = workBench.getDetachedToolWindows();
      for (AttachedToolWindow tool : detachedToolWindows) {
        ToolWindowDefinition definition = tool.getDefinition();
        String id = definition.getName();
        DetachedToolWindow detachedToolWindow = myToolWindowMap.get(id);
        if (detachedToolWindow == null) {
          detachedToolWindow = myDetachedToolWindowFactory.create(myProject, definition);
          Disposer.register(myProject, detachedToolWindow);
          myToolWindowMap.put(id, detachedToolWindow);
        }
        if (!ToggleDistractionFreeModeAction.isDistractionFreeModeEnabled()) {
          //noinspection unchecked
          detachedToolWindow.show(tool);
        }
        ids.remove(id);
      }
    }
    ids.forEach(id -> myToolWindowMap.get(id).hide());
  }

  public void restoreDefaultLayout() {
    myApplication.invokeLater(() -> updateToolWindowsForWorkBench(getActiveWorkBench()));
  }

  private class MyFileEditorManagerListener implements FileEditorManagerListener {

    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
      myApplication.invokeLater(() -> updateToolWindowsForWorkBench(getActiveWorkBench()));
    }

    @Override
    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
      myApplication.invokeLater(() -> updateToolWindowsForWorkBench(getActiveWorkBench()));
    }

    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
      myLastSelectedEditor = event.getNewEditor();
      updateToolWindowsForWorkBench(myWorkBenchMap.get(myLastSelectedEditor));
    }
  }

  interface DetachedToolWindowFactory {
    DetachedToolWindow create(@NotNull Project project, @NotNull ToolWindowDefinition definition);
  }
}
