/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.profilers.AndroidProfilerToolWindowFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

public class AndroidProfilerToolWindowFixture {
  @NotNull
  public static AndroidProfilerToolWindowFixture find(@NotNull IdeFrameFixture ideFrameFixture) {
    final Ref<ToolWindow> toolWindowRef = new Ref<>();
    Wait.seconds(10).expecting("tool window with ID '" + AndroidProfilerToolWindowFactory.ID + "' to be found")
        .until(() -> {
          ToolWindow toolWindow =
            ToolWindowManager.getInstance(ideFrameFixture.getProject()).getToolWindow(AndroidProfilerToolWindowFactory.ID);
          toolWindowRef.set(toolWindow);
          return toolWindow != null;
        });
    return new AndroidProfilerToolWindowFixture(ideFrameFixture, toolWindowRef.get());
  }

  @NotNull private final IdeFrameFixture myIdeFrameFixture;
  @NotNull private final ToolWindow myToolWindow;

  private AndroidProfilerToolWindowFixture(@NotNull IdeFrameFixture ideFrameFixture, @NotNull ToolWindow toolWindow) {
    myIdeFrameFixture = ideFrameFixture;
    myToolWindow = toolWindow;
  }
}
