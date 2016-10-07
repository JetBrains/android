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
import com.intellij.designer.LightToolWindow;
import com.intellij.designer.LightToolWindowManager;
import com.intellij.designer.ToggleEditorModeAction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowAnchor;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AnalysisResultsManager extends CaptureEditorLightToolWindowManager {
  @NotNull private AnalysisResultsContent myContent;

  @NotNull
  public static AnalysisResultsManager getInstance(@NotNull Project project) {
    return project.getComponent(AnalysisResultsManager.class);
  }

  protected AnalysisResultsManager(@NotNull Project project, @NotNull FileEditorManager fileEditorManager) {
    super(project, fileEditorManager);
    myContent = new AnalysisResultsContent();
  }

  @Override
  protected void updateToolWindow(@Nullable DesignerEditorPanelFacade designer) {
    myContent.update(designer);

    if (designer == null) {
      myToolWindow.setAvailable(false, null);
    }
    else {
      DesignerEditorPanelFacade activeDesigner = getActiveDesigner();
      if (activeDesigner != null &&
          activeDesigner instanceof CapturePanel &&
          activeDesigner.getClientProperty(getComponentName()) == null) {
        activeDesigner.putClientProperty(getComponentName(), myContent);
      }
      myToolWindow.setIcon(getIcon());
      myToolWindow.setAvailable(true, null);
      myToolWindow.show(null);
    }
  }

  @NotNull
  @Override
  protected Icon getIcon() {
    return myContent.getIcon() == null ? AllIcons.Toolwindows.ToolWindowFind : myContent.getIcon();
  }

  @NotNull
  @Override
  protected String getManagerName() {
    return AndroidBundle.message("android.captures.analysis.results.manager.name");
  }

  @NotNull
  @Override
  protected String getToolWindowTitleBarText() {
    return AndroidBundle.message("android.captures.analysis.results.manager.titlebar.text");
  }

  @NotNull
  @Override
  protected AnAction[] createActions() {
    return new AnAction[]{new ToggleAction(AndroidBundle.message("android.captures.analysis.results.manager.run.name"),
                                           AndroidBundle.message("android.captures.analysis.results.manager.run.description"),
                                           AllIcons.Toolwindows.ToolWindowRun) {
      @Override
      public boolean isSelected(AnActionEvent e) {
        AnalysisResultsContent analysisResultsContent = getContentFromDesigner();
        return analysisResultsContent != null && !analysisResultsContent.canRunAnalysis();
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        if (state) {
          AnalysisResultsContent analysisResultsContent = getContentFromDesigner();
          if (analysisResultsContent != null && analysisResultsContent.canRunAnalysis()) {
            analysisResultsContent.performAnalysis();
          }
        }
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        super.update(e);
        Presentation presentation = e.getPresentation();
        if (isSelected(e)) {
          presentation.setText(AndroidBundle.message("android.captures.analysis.results.manager.run.disabled.name"));
          presentation.setDescription(AndroidBundle.message("android.captures.analysis.results.manager.run.disabled.description"));
          presentation.setIcon(AllIcons.Process.DisabledRun);
        }
        else {
          presentation.setText(AndroidBundle.message("android.captures.analysis.results.manager.run.enabled.name"));
          presentation.setDescription(AndroidBundle.message("android.captures.analysis.results.manager.run.enabled.description"));
          presentation.setIcon(AllIcons.Toolwindows.ToolWindowRun);
        }
      }
    }};
  }

  @NotNull
  @Override
  protected JComponent getContent() {
    return myContent.getMainPanel();
  }

  @Nullable
  @Override
  protected JComponent getFocusedComponent() {
    return myContent.getFocusComponent();
  }

  @Override
  protected ToolWindowAnchor getAnchor() {
    return ToolWindowAnchor.RIGHT;
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "CaptureAnalysis";
  }

  @Override
  public void disposeComponent() {
    myContent.dispose();
  }

  @Override
  protected LightToolWindow createContent(@NotNull DesignerEditorPanelFacade designer) {
    AnalysisResultsContent content = new AnalysisResultsContent();
    content.update(designer);

    Icon icon = content.getIcon();
    if (icon == null) {
      icon = getIcon();
    }

    // TODO figure out how to properly dispose this if it needs to be
    JComponent focus = content.getFocusComponent();
    LightToolWindow lightToolWindow = createContent(designer, content, getToolWindowTitleBarText(), icon, content.getMainPanel(),
                                                    focus == null ? content.getMainPanel() : focus, 320, createActions());
    try {
      // When LightToolWindow#minimize() is added to the base platform and upstreamed,
      // replace this:
      LightToolWindow.class.getDeclaredMethod("minimize").invoke(lightToolWindow);
      // with lightToolWindow.minimize();
    } catch (Exception ignore) {
    }
    return lightToolWindow;
  }

  @Override
  protected ToggleEditorModeAction createToggleAction(@NotNull ToolWindowAnchor anchor) {
    return new ToggleEditorModeAction(this, myProject, anchor) {
      @Override
      protected LightToolWindowManager getOppositeManager() {
        return EmptyManager.getInstance(myProject);
      }
    };
  }

  @Nullable
  private AnalysisResultsContent getContentFromDesigner() {
    DesignerEditorPanelFacade activeDesigner = getActiveDesigner();
    if (activeDesigner != null && activeDesigner instanceof CapturePanel) {
      Object property = activeDesigner.getClientProperty(getComponentName());
      if (property instanceof LightToolWindow) {
        LightToolWindow lightToolWindow = (LightToolWindow)property;
        Object content = lightToolWindow.getContent();
        if (content instanceof AnalysisResultsContent) {
          return (AnalysisResultsContent)content;
        }
      }
      else if (property instanceof AnalysisResultsContent) {
        return (AnalysisResultsContent)property;
      }
    }

    return null;
  }
}
