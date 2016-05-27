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
package com.android.tools.idea.gradle.invoker.console.view;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import org.jetbrains.annotations.NotNull;

public abstract class GradleConsoleView implements Disposable {
  private static final GradleConsoleView NO_OP = new GradleConsoleView() {
    @Override
    public void clear() {
    }

    @Override
    public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
    }

    @Override
    public void createToolWindowContent(@NotNull ToolWindow toolWindow) {
    }

    @Override
    public void dispose() {
    }
  };

  @NotNull
  public static GradleConsoleView getInstance(@NotNull Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return NO_OP;
    }
    return ServiceManager.getService(project, DefaultGradleConsoleView.class);
  }

  public abstract void clear();

  public abstract void print(@NotNull String text, @NotNull ConsoleViewContentType contentType);

  public abstract void createToolWindowContent(@NotNull ToolWindow toolWindow);
}
