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
package com.android.tools.idea.uibuilder.editor;

import com.android.tools.idea.common.editor.NlEditor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.WeakHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.tools.idea.common.editor.NlEditorProvider.DESIGNER_ID;

public class LayoutNavigationManager implements Disposable {

  /**
   * The map associate a "destination" file with a "source" file.
   * It is used to navigate back from the destination editor the source editor.
   */
  private static final WeakHashMap<VirtualFile, VirtualFile> ourNavigationCache = new WeakHashMap<>();

  private final Project myProject;

  public VirtualFile get(VirtualFile file) {
    return ourNavigationCache.get(file);
  }

  private LayoutNavigationManager(@NotNull Project project) {
    myProject = project;

    myProject.getMessageBus().connect(myProject).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
      }
    });
  }

  public static LayoutNavigationManager getInstance(@NotNull Project project) {
    return project.getComponent(LayoutNavigationManager.class);
  }

  /**
   * Open the given destination file and add it to the file stack after source
   *
   * @param source      The file below destination on the stack.
   * @param destination The file to open
   * @return true if the editor for destination file has been open
   */
  public boolean pushFile(@NotNull VirtualFile source, @NotNull VirtualFile destination) {
    ourNavigationCache.put(destination, source);
    FileEditorManager manager = FileEditorManager.getInstance(myProject);
    FileEditor sourceEditor = manager.getSelectedEditor(source);
    FileEditor destinationEditor = manager.getSelectedEditor(destination);
    if (destinationEditor == null) {
      FileEditor[] editors = manager.openFile(destination, true);
      if (editors.length == 0) {
        return false;
      }
    }
    else {
      OpenFileDescriptor openFileDescriptor = new OpenFileDescriptor(myProject, destination);
      List<FileEditor> editors = manager.openEditor(openFileDescriptor, true);
      if (!editors.contains(destinationEditor)) {
        Logger.getInstance(LayoutNavigationManager.class).error("The editor was supposed to be already open");
        return false;
      }
    }
    boolean isInDesignerMode = sourceEditor instanceof NlEditor;
    manager.setSelectedEditor(destination, isInDesignerMode ? DESIGNER_ID : TextEditorProvider.getInstance().getEditorTypeId());
    return true;
  }

  /**
   * Focus the parentFile and remove fileToPop from the NavigationCache
   **/
  public void popFile(@NotNull VirtualFile fileToPop,
                      @NotNull VirtualFile parentFile) {
    FileEditorManager manager = FileEditorManager.getInstance(myProject);
    OpenFileDescriptor previousOpenFileDescriptor = new OpenFileDescriptor(myProject, parentFile);
    manager.openEditor(previousOpenFileDescriptor, true);
    ourNavigationCache.remove(fileToPop);
  }

  @Override
  public void dispose() {
    ourNavigationCache.clear();
  }
}
