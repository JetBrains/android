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
package com.android.tools.idea.profilers;

import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.facet.ui.FacetDependentToolWindow;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;

public class AndroidProfilerToolWindowFactory implements DumbAware, ToolWindowFactory, Condition<Project> {
  public static final String ID = "Android Profiler";
  public static final String ANDROID_PROFILER_ACTIVE = "android.profiler.active";

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    toolWindow.setToHideOnEmptyContent(true);
    ToolWindowManagerEx.getInstanceEx(project).addToolWindowManagerListener(new ToolWindowManagerListener() {
      @Override
      public void toolWindowRegistered(@NotNull String id) {
      }

      @Override
      public void stateChanged() {
        // We need to query the tool window again, because it might have been unregistered when closing the project.
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(ID);
        if (window != null) {
          if (window.isVisible() && window.getContentManager().getContentCount() == 0) {
            createContent(project, window);
          }
        }
      }
    });
  }

  private static void createContent(Project project, ToolWindow toolWindow) {
    AndroidProfilerToolWindow view = new AndroidProfilerToolWindow(project);
    ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    Content content = contentFactory.createContent(view.getComponent(), "", false);
    Disposer.register(content, view);
    toolWindow.getContentManager().addContent(content);
    PropertiesComponent properties = PropertiesComponent.getInstance(project);
    toolWindow.setIcon(ExecutionUtil.getLiveIndicator(StudioIcons.Shell.ToolWindows.ANDROID_PROFILER));
    properties.setValue(ANDROID_PROFILER_ACTIVE, true);
  }

  public static void removeContent(Project project, ToolWindow toolWindow) {
    toolWindow.getContentManager().removeAllContents(true);
    PropertiesComponent properties = PropertiesComponent.getInstance(project);
    toolWindow.setIcon(StudioIcons.Shell.ToolWindows.ANDROID_PROFILER);
    properties.setValue(ANDROID_PROFILER_ACTIVE, false);
  }

  @Override
  public boolean value(Project project) {
    return false;
  }

  @NotNull
  public static ToolWindow ensureToolWindowInitialized(@NotNull ToolWindowManagerEx windowManager) {
    ToolWindow window = windowManager.getToolWindow(ID);
    if (window == null) {
      for (FacetDependentToolWindow extension : Extensions.getExtensions(FacetDependentToolWindow.EXTENSION_POINT_NAME)) {
        if (extension.id.equals(ID)) {
          windowManager.initToolWindow(extension);
          window = windowManager.getToolWindow(ID);
        }
      }
      if (window == null) {
        throw new RuntimeException("Could not find Android Profiler facet/extension.");
      }
    }
    return window;
  }
}

