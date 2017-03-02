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

import com.android.tools.swing.ui.NavigationComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.WeakHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.uibuilder.editor.NlEditorProvider.DESIGNER_ID;

public class LayoutNavigationManager implements Disposable {
  private final Project myProject;

  private static class LayoutNavigationItem extends NavigationComponent.Item {
    private final VirtualFile myFile;

    public LayoutNavigationItem(@NotNull VirtualFile layout) {
      myFile = layout;
    }

    @NotNull
    @Override
    public String getDisplayText() {
      return myFile.getNameWithoutExtension();
    }
  }

  private static WeakHashMap<FileEditor, NavigationComponent<LayoutNavigationItem>> ourNavigationBarCache = new WeakHashMap<>();

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

  public void updateNavigation(@NotNull FileEditor sourceEditor, VirtualFile source, FileEditor destinationEditor, VirtualFile destination) {
    FileEditorManager manager = FileEditorManager.getInstance(myProject);

    NavigationComponent<LayoutNavigationItem> sourceNavigationComponent = ourNavigationBarCache.get(sourceEditor);

    if (sourceNavigationComponent == null) {
      sourceNavigationComponent = new NavigationComponent<>();
      sourceNavigationComponent.push(new LayoutNavigationItem(source));
      NavigationComponent<LayoutNavigationItem> finalNavigationComponent = sourceNavigationComponent;
      sourceNavigationComponent.addItemListener((item) -> {
        OpenFileDescriptor openFileDescriptor =
          new OpenFileDescriptor(myProject, item.myFile);
        manager.openEditor(openFileDescriptor, true);
        finalNavigationComponent.goTo(item);
        finalNavigationComponent.pop();
        manager.removeTopComponent(destinationEditor, finalNavigationComponent);
        ourNavigationBarCache.remove(destinationEditor);
      });
    }
    NavigationComponent<LayoutNavigationItem> previousComponent = ourNavigationBarCache.put(destinationEditor, sourceNavigationComponent);
    if (previousComponent != null) {
      manager.removeTopComponent(destinationEditor, previousComponent);
    }
    NavigationComponent<LayoutNavigationItem> navigationComponent = sourceNavigationComponent;

    String sourceProviderId = sourceEditor instanceof NlEditor ? DESIGNER_ID : TextEditorProvider.getInstance().getEditorTypeId();
    navigationComponent.push(new LayoutNavigationItem(destination));
    manager.addTopComponent(destinationEditor, navigationComponent);
  }

  @Override
  public void dispose() {
    ourNavigationBarCache.clear();
  }
}
