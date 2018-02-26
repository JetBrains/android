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
import com.intellij.ide.util.PropertiesComponent;
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
import com.intellij.ui.content.ContentManager;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class AndroidProfilerToolWindowFactory implements DumbAware, ToolWindowFactory, Condition<Project> {
  public static final String ID = "Android Profiler";
  private static final String PROFILER_TOOL_WINDOW_TITLE = "Profiler";
  private static final String ANDROID_PROFILER_ACTIVE = "android.profiler.active";
  private static final Map<Content, AndroidProfilerToolWindow> PROJECT_PROFILER_MAP = new HashMap<>();

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    ToolWindowManagerEx.getInstanceEx(project).addToolWindowManagerListener(new ToolWindowManagerListener() {
      @Override
      public void toolWindowRegistered(@NotNull String id) {
      }

      @Override
      public void stateChanged() {
        // We need to query the tool window again, because it might have been unregistered when closing the project.
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(ID);
        if (window != null && window.isVisible() && window.getContentManager().getContentCount() == 0) {
          createContent(project, window);
        }
      }
    });
  }

  @Override
  public void init(ToolWindow toolWindow) {
    toolWindow.setToHideOnEmptyContent(true);
    toolWindow.hide(null);
    toolWindow.setShowStripeButton(false);
  }

  private static void createContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    AndroidProfilerToolWindow view = new AndroidProfilerToolWindow(project);
    ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    Content content = contentFactory.createContent(view.getComponent(), "", false);
    Disposer.register(content, view);
    toolWindow.getContentManager().addContent(content);
    toolWindow.setIcon(ExecutionUtil.getLiveIndicator(StudioIcons.Shell.ToolWindows.ANDROID_PROFILER));
    toolWindow.setStripeTitle(PROFILER_TOOL_WINDOW_TITLE);

    PROJECT_PROFILER_MAP.put(content, view);
    Disposer.register(content, () -> PROJECT_PROFILER_MAP.remove(content));

    PropertiesComponent properties = PropertiesComponent.getInstance(project);
    properties.setValue(ANDROID_PROFILER_ACTIVE, true);

    // Forcibly synchronize the Tool Window to a visible state. Otherwise, the Tool Window may not auto-hide correctly.
    toolWindow.show(null);
  }

  @Nullable
  static AndroidProfilerToolWindow getProfilerTooWindow(@NotNull Project project) {
    ToolWindow window = ToolWindowManagerEx.getInstanceEx(project).getToolWindow(ID);
    if (window == null) {
      return null;
    }

    ContentManager contentManager = window.getContentManager();
    if (contentManager == null || contentManager.getContentCount() == 0) {
      return null;
    }

    return PROJECT_PROFILER_MAP.get(contentManager.getContent(0));
  }

  public static void removeContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    toolWindow.getContentManager().removeAllContents(true);
    PropertiesComponent properties = PropertiesComponent.getInstance(project);
    toolWindow.setIcon(StudioIcons.Shell.ToolWindows.ANDROID_PROFILER);
    properties.setValue(ANDROID_PROFILER_ACTIVE, false);
  }

  @Override
  public boolean value(Project project) {
    return true;
  }
}

