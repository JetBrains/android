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
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
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
    DesignerEditorPanelFacade facade = getDesignerEditorFacade(project);
    Presentation presentation = event.getPresentation();
    presentation.setEnabled(getToolContent(project, facade) != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    DesignerEditorPanelFacade facade = getDesignerEditorFacade(project);
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
  private DesignerEditorPanelFacade getDesignerEditorFacade(@Nullable Project project) {
    if (project == null) {
      return null;
    }

    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    FileEditor[] editors = fileEditorManager.getSelectedEditors();
    for (FileEditor fileEditor : editors) {
      if (fileEditor instanceof NlEditor) {
        return ((NlEditor)fileEditor).getComponent();
      }
    }

    Editor editor = fileEditorManager.getSelectedTextEditor();
    if (editor == null) {
      return null;
    }

    NlPreviewManager previewManager = NlPreviewManager.getInstance(project);
    if (getWindowManager(project) instanceof NlPaletteManager && previewManager.isWindowVisible()) {
      return previewManager.getPreviewForm();
    }

    final PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
    if (file == null) {
      return null;
    }

    for (FileEditor fileEditor : fileEditorManager.getEditors(file.getVirtualFile())) {
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
