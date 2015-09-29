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
package com.android.tools.idea.profiling.view;

import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.designer.LightToolWindowManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class CaptureEditorLightToolWindowManager extends LightToolWindowManager {
  protected CaptureEditorLightToolWindowManager(@NotNull Project project, @NotNull FileEditorManager fileEditorManager) {
    super(project, fileEditorManager);
  }

  @Nullable
  @Override
  protected DesignerEditorPanelFacade getDesigner(FileEditor editor) {
    if (editor instanceof CaptureEditor) {
      return ((CaptureEditor)editor).getFacade();
    }
    return null;
  }

  @Override
  protected void initToolWindow() {
    myToolWindow = ToolWindowManager.getInstance(myProject).registerToolWindow(getManagerName(), false, getAnchor(), myProject, true);
    myToolWindow.setIcon(getIcon());

    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      myToolWindow.getComponent().putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true");
    }

    ((ToolWindowEx)myToolWindow).setTitleActions(createActions());
    initGearActions();

    ContentManager contentManager = myToolWindow.getContentManager();
    Content content = contentManager.getFactory().createContent(getContent(), getToolWindowTitleBarText(), false);
    content.setCloseable(false);
    content.setPreferredFocusableComponent(getFocusedComponent());
    contentManager.addContent(content);
    contentManager.setSelectedContent(content, true);
    myToolWindow.setAvailable(false, null);
  }

  @NotNull
  protected abstract Icon getIcon();

  @NotNull
  protected abstract String getManagerName();

  @NotNull
  protected abstract String getToolWindowTitleBarText();

  @NotNull
  protected abstract AnAction[] createActions();

  @NotNull
  protected abstract JComponent getContent();

  @Nullable
  protected abstract JComponent getFocusedComponent();
}
