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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.WeakHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.tools.idea.uibuilder.editor.NlEditorProvider.DESIGNER_ID;

public class LayoutNavigationManager implements Disposable {
  private static final WeakHashMap<FileEditor, NavigationComponent<LayoutNavigationItem>> ourNavigationBarCache = new WeakHashMap<>();

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
    FileEditorManager manager = FileEditorManager.getInstance(myProject);
    FileEditor sourceEditor = manager.getSelectedEditor(source);
    FileEditor destinationEditor = manager.getSelectedEditor(destination);
    if (destinationEditor == null) {
      FileEditor[] editors = manager.openFile(destination, true);
      if (editors.length == 0) {
        return false;
      }
      destinationEditor = editors[0];
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

    NavigationComponent<LayoutNavigationItem> navigationComponent = ourNavigationBarCache.get(sourceEditor);
    if (navigationComponent == null) {
      navigationComponent = createNavigationComponent(source, destinationEditor);
    }
    NavigationComponent<LayoutNavigationItem> previousComponent = ourNavigationBarCache.put(destinationEditor, navigationComponent);
    if (previousComponent != null) {
      manager.removeTopComponent(destinationEditor, previousComponent);
    }

    navigationComponent.push(new LayoutNavigationItem(destination));
    manager.addTopComponent(destinationEditor, navigationComponent);
    return true;
  }

  /**
   * Go back to the previous editor from editorToPop and unstack editorToPop from navigation component
   *
   * @param editorToPop            The editor to pop from navigation component
   * @param navigationComponent    The navigation component owning the editor to pop
   * @param previousNavigationItem The navigation item to go back to
   */
  private void popFile(@NotNull FileEditor editorToPop,
                       @NotNull NavigationComponent<LayoutNavigationItem> navigationComponent,
                       @NotNull LayoutNavigationItem previousNavigationItem) {
    FileEditorManager manager = FileEditorManager.getInstance(myProject);
    OpenFileDescriptor previousOpenFileDescriptor = new OpenFileDescriptor(myProject, previousNavigationItem.myFile);
    manager.openEditor(previousOpenFileDescriptor, true);
    navigationComponent.goTo(previousNavigationItem);
    navigationComponent.pop();
    manager.removeTopComponent(editorToPop, navigationComponent);
    ourNavigationBarCache.remove(editorToPop);
  }

  /**
   * Create a new {@link NavigationComponent<LayoutNavigationItem>} with a new {@link LayoutNavigationItem}
   * to go back to rootFile from childEditor.
   *
   * @param rootFile    The file showing at the root of the navigation component
   * @param childEditor The editor that will display this {@link NavigationComponent}.
   *                    It is normally the one opened from the editor of rootFile
   * @return The newly created {@link NavigationComponent<LayoutNavigationItem>}
   */
  @NotNull
  private NavigationComponent<LayoutNavigationItem> createNavigationComponent(@NotNull VirtualFile rootFile,
                                                                              @NotNull FileEditor childEditor) {
    NavigationComponent<LayoutNavigationItem> navigationComponent = new NavigationComponent<>();
    navigationComponent.push(new LayoutNavigationItem(rootFile));
    navigationComponent.addItemListener((item) -> popFile(childEditor, navigationComponent, item));
    return navigationComponent;
  }

  @Override
  public void dispose() {
    ourNavigationBarCache.clear();
  }
}
