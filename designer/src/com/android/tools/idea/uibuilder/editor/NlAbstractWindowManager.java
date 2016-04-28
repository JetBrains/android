/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.designer.LightToolWindowManager;
import com.intellij.designer.ToggleEditorModeAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class NlAbstractWindowManager extends LightToolWindowManager {

  public NlAbstractWindowManager(@NotNull Project project, @NotNull FileEditorManager fileEditorManager) {
    super(project, fileEditorManager);
  }

  protected void initToolWindow(@NotNull String id, @NotNull Icon icon) {
    myToolWindow = ToolWindowManager.getInstance(myProject).registerToolWindow(id, false, getAnchor(), myProject, true);
    myToolWindow.setIcon(icon);
    myToolWindow.setAvailable(false, null);
    myToolWindow.setAutoHide(false);
    initGearActions();
  }

  @Nullable
  @Override
  protected DesignerEditorPanelFacade getDesigner(@Nullable FileEditor editor) {
    if (editor instanceof NlEditor) {
      NlEditor designerEditor = (NlEditor)editor;
      return designerEditor.getComponent();
    }
    if (editor instanceof TextEditor) {
      NlPreviewManager previewManager = NlPreviewManager.getInstance(myProject);
      if (previewManager.isApplicableEditor((TextEditor)editor)) {
        return previewManager.getPreviewForm();
      }
    }
    return null;
  }

  @NotNull
  protected static DesignSurface getDesignSurface(@NotNull DesignerEditorPanelFacade designer) {
    if (designer instanceof NlEditorPanel) {
      NlEditorPanel editor = (NlEditorPanel)designer;
      return editor.getSurface();
    } else if (designer instanceof NlPreviewForm) {
      NlPreviewForm form = (NlPreviewForm)designer;
      return form.getSurface();
    }

    // Unexpected facade
    throw new RuntimeException(designer.getClass().getName());
  }

  protected void createWindowContent(@NotNull JComponent contentPane, @NotNull JComponent focusedComponent, @Nullable AnAction[] actions) {
    ContentManager contentManager = myToolWindow.getContentManager();
    Content content = contentManager.getFactory().createContent(contentPane, null, false);
    content.setCloseable(false);
    content.setPreferredFocusableComponent(focusedComponent);
    if (actions != null) {
      ToolWindowEx toolWindow = (ToolWindowEx)myToolWindow;
      toolWindow.setTitleActions(actions);
    }
    contentManager.addContent(content);
    contentManager.setSelectedContent(content, true);
  }

  @Override
  protected ToggleEditorModeAction createToggleAction(@NotNull ToolWindowAnchor anchor) {
    return new ToggleEditorModeAction(this, myProject, anchor) {
      @Override
      protected LightToolWindowManager getOppositeManager() {
        LightToolWindowManager propertiesManager = NlPropertiesWindowManager.get(myProject);
        LightToolWindowManager paletteManager = NlPaletteManager.get(myProject);
        return myManager == propertiesManager ? paletteManager : propertiesManager;
      }
    };
  }
}
