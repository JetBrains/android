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

import com.intellij.designer.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowAnchor;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Dummy LightToolWindowManager because the framework requires two managers at the same time.
 */
public class EmptyManager extends CaptureEditorLightToolWindowManager {
  @NotNull private JPanel myEmptyPanel = new JPanel();

  @NotNull
  public static EmptyManager getInstance(@NotNull Project project) {
    return project.getComponent(EmptyManager.class);
  }

  protected EmptyManager(@NotNull Project project, @NotNull FileEditorManager fileEditorManager) {
    super(project, fileEditorManager);
  }

  @Nullable
  @Override
  protected DesignerEditorPanelFacade getDesigner(@NotNull FileEditor editor) {
    return null;
  }

  @Override
  protected void updateToolWindow(@Nullable DesignerEditorPanelFacade designer) {

  }

  @NotNull
  @Override
  protected Icon getIcon() {
    return AndroidIcons.ToolWindows.Warning;
  }

  @NotNull
  @Override
  protected String getManagerName() {
    return "Capture Tool";
  }

  @NotNull
  @Override
  protected String getToolWindowTitleBarText() {
    return "Unused";
  }

  @NotNull
  @Override
  protected AnAction[] createActions() {
    return new AnAction[0];
  }

  @NotNull
  @Override
  protected JComponent getContent() {
    return myEmptyPanel;
  }

  @Nullable
  @Override
  protected JComponent getFocusedComponent() {
    return myEmptyPanel;
  }

  @Override
  protected ToolWindowAnchor getAnchor() {
    return ToolWindowAnchor.LEFT;
  }

  @Override
  protected ToggleEditorModeAction createToggleAction(ToolWindowAnchor anchor) {
    return new ToggleEditorModeAction(this, myProject, anchor) {
      @Override
      protected LightToolWindowManager getOppositeManager() {
        return AnalysisResultsManager.getInstance(myProject);
      }
    };
  }

  @Override
  protected LightToolWindow createContent(@NotNull DesignerEditorPanelFacade designer) {
    return createContent(designer, new LightToolWindowContent() {
      @Override
      public void dispose() {

      }
    }, getToolWindowTitleBarText(), getIcon(), myEmptyPanel, myEmptyPanel, 0, createActions());
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "CaptureTool";
  }
}
