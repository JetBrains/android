/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.toolwindow;

import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;

/**
 * Factory for the Blaze tool window.
 */
public class TasksToolWindowFactory implements DumbAware, ToolWindowFactory {

  /** Tool window ID that matches the one in blaze-base.xml. */
  public static final String ID = "Blaze";

  @Override
  public void createToolWindowContent(Project project, ToolWindow toolWindow) {
    String title = Blaze.getBuildSystemName(project).getName();
    toolWindow.setTitle(title);
    toolWindow.setStripeTitle(title);
  }
}
