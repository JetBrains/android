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
package com.android.tools.idea.uibuilder.actions;

import com.android.tools.idea.uibuilder.editor.*;
import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class LightToolWindowAction<T> extends AnAction {
  private final Class<T> myClass;

  public LightToolWindowAction(@NotNull Class<T> tClass) {
    myClass = tClass;
  }

  @NotNull
  protected abstract NlAbstractWindowManager getWindowManager(@NotNull Project project);

  protected abstract void actionPerformed(@NotNull T toolContent);

  @Override
  public void update(AnActionEvent event) {
    Project project = event.getProject();
    DesignerEditorPanelFacade facade = getDesignerEditorFacade(event);
    Presentation presentation = event.getPresentation();
    presentation.setEnabled(getToolContent(project, facade) != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    DesignerEditorPanelFacade facade = getDesignerEditorFacade(event);
    T toolContent = getToolContent(project, facade);
    if (toolContent != null) {
      if (facade instanceof NlEditorPanel) {
        FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
        fileEditorManager.setSelectedEditor(((NlEditorPanel)facade).getFile().getVirtualFile(), NlEditorProvider.DESIGNER_ID);
      }
      NlAbstractWindowManager windowManager = getWindowManager(project);
      windowManager.activateToolWindow(facade, () -> actionPerformed(toolContent));
    }
  }

  @Nullable
  private DesignerEditorPanelFacade getDesignerEditorFacade(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    if (project == null) {
      return null;
    }
    NlPreviewManager previewManager = NlPreviewManager.getInstance(project);

    VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);

    if (file == null && previewManager.toolWindowHasFocus()) {
      // If the tool window from the preview has focus get the file from the preview
      PsiFile psiFile = previewManager.getPreviewForm().getFile();
      if (psiFile != null) {
        file = psiFile.getVirtualFile();
      }
    }

    if (file == null) {
      return null;
    }

    if (previewManager.isWindowVisible() && getWindowManager(project) instanceof NlPaletteManager) {
      // If interested in palette or component tree use the palette in the preview if currently visible:
      return previewManager.getPreviewForm();
    }

    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    for (FileEditor fileEditor : fileEditorManager.getEditors(file)) {
      if (fileEditor instanceof NlEditor) {
        return ((NlEditor)fileEditor).getComponent();
      }
    }
    return null;
  }

  @Nullable
  private T getToolContent(@Nullable Project project, @Nullable DesignerEditorPanelFacade facade) {
    if (project == null || facade == null) {
      return null;
    }
    NlAbstractWindowManager windowManager = getWindowManager(project);
    Object content = windowManager.getToolWindowContent(facade);
    if (myClass.isInstance(content)) {
      //noinspection unchecked
      return (T)content;
    }
    return null;
  }
}
