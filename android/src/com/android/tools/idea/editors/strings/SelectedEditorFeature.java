/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * this class handles the fact that {@link FileEditor#selectNotify} is called sometimes several times,
 * and sometimes not called at all
 */
public abstract class SelectedEditorFeature implements FileEditorManagerListener {
  private final Project myProject;
  private final FileEditor myEditor;

  private boolean myOpen;
  private boolean mySelected;
  private boolean myFeatureStarted;

  public SelectedEditorFeature(@NotNull FileEditor editor, @NotNull Project project) {
    myProject = project;
    myEditor = editor;

    MessageBusConnection connection = myProject.getMessageBus().connect(editor);
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this);
  }

  public void selectNotify() {
    if (!myOpen) {
      myOpen = true;
      mySelected = Arrays.asList(FileEditorManager.getInstance(myProject).getSelectedEditors()).contains(myEditor);
      startFeature();
    }
  }

  public void deselectNotify() {
    if (myOpen) {
      myOpen = false;
      mySelected = false;
      stopFeature();
    }
  }

  private void startFeature() {
    if (myOpen && mySelected && !myFeatureStarted) {
      if (isReady()) {
        open();
        myFeatureStarted = true;
      }
      else {
        AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
          ApplicationManager.getApplication().invokeLater(this::startFeature);
        }, 100, TimeUnit.MILLISECONDS);
      }
    }
  }

  private void stopFeature() {
    if (myFeatureStarted) {
      myFeatureStarted = false;
      close();
    }
  }


  public abstract boolean isReady();

  public abstract void open();

  public abstract void close();

  @Override
  public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {

  }

  @Override
  public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {

  }

  @Override
  public void selectionChanged(@NotNull FileEditorManagerEvent event) {
    if (event.getNewEditor() == myEditor) {
      myOpen = true;
      mySelected = true;
      startFeature();
    }
    else if (event.getOldEditor() == myEditor) {
      mySelected = false;
      stopFeature();
    }
  }
}
