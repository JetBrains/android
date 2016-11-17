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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * All {@link WorkBench}es of a specified name will use the same {@link FloatingToolWindow}
 * for a given tool window name.
 * This class is responsible for switching the content to the content of the currently
 * active {@link WorkBench}.
 */
public class FloatingToolWindowManager implements ProjectComponent {
  private final Application myApplication;
  private final Project myProject;
  private final StartupManager myStartupManager;
  private final FileEditorManager myEditorManager;
  private final MyFileEditorManagerListener myEditorManagerListener;
  private final Map<FileEditor, WorkBench> myWorkBenchMap;
  private final HashMap<String, FloatingToolWindow> myToolWindowMap;
  private MessageBusConnection myConnection;

  public static FloatingToolWindowManager getInstance(@NotNull Project project) {
    return project.getComponent(FloatingToolWindowManager.class);
  }

  public FloatingToolWindowManager(@NotNull Application application,
                                   @NotNull Project project,
                                   @NotNull StartupManager startupManager,
                                   @NotNull FileEditorManager fileEditorManager) {
    myApplication = application;
    myProject = project;
    myStartupManager = startupManager;
    myEditorManager = fileEditorManager;
    myEditorManagerListener = new MyFileEditorManagerListener();
    myWorkBenchMap = new IdentityHashMap<>(13);
    myToolWindowMap = new HashMap<>(8);
  }

  public void register(@Nullable FileEditor fileEditor, @NotNull WorkBench workBench) {
    if (fileEditor != null) {
      myWorkBenchMap.put(fileEditor, workBench);
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
    return FloatingToolWindowManager.class.getSimpleName();
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  @Override
  public void projectOpened() {
    myStartupManager.runWhenProjectIsInitialized((DumbAwareRunnable)() -> {
      myConnection = myProject.getMessageBus().connect(myProject);
      myConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, myEditorManagerListener);
      updateToolWindowsForWorkBench(getActiveWorkBench());
    });
  }

  @Override
  public void projectClosed() {
    if (myConnection != null) {
      myConnection.disconnect();
      myConnection = null;
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
    for (FileEditor editor : selectedEditors) {
      if (SwingUtilities.isDescendingFrom(focusOwner, editor.getComponent())) {
        return myWorkBenchMap.get(editor);
      }
    }
    return myWorkBenchMap.get(selectedEditors[0]);
  }

  public <T> void updateToolWindowsForWorkBench(@Nullable WorkBench<T> workBench) {
    Set<String> ids = new HashSet<>(myToolWindowMap.keySet());
    if (workBench != null) {
      List<AttachedToolWindow<T>> floatingToolWindows = workBench.getFloatingToolWindows();
      for (AttachedToolWindow<T> tool : floatingToolWindows) {
        ToolWindowDefinition<T> definition = tool.getDefinition();
        String id = definition.getName();
        //noinspection unchecked
        FloatingToolWindow<T> floatingToolWindow = myToolWindowMap.get(id);
        if (floatingToolWindow == null) {
          floatingToolWindow = new FloatingToolWindow<>(myProject, definition);
          Disposer.register(myProject, floatingToolWindow);
          myToolWindowMap.put(id, floatingToolWindow);
        }
        floatingToolWindow.show(tool);
        ids.remove(id);
      }
    }
    ids.forEach(id -> myToolWindowMap.get(id).hide());
  }

  private class MyFileEditorManagerListener implements FileEditorManagerListener {

    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
      updateToolWindowsForWorkBench(getActiveWorkBench());
    }

    @Override
    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
      myApplication.invokeLater(() -> updateToolWindowsForWorkBench(getActiveWorkBench()));
    }

    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
      updateToolWindowsForWorkBench(myWorkBenchMap.get(event.getNewEditor()));
    }
  }
}
