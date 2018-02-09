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
package com.android.tools.idea.assistant;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;

public final class AssistToolWindowFactory implements ToolWindowFactory {
  private AssistSidePanel myAssistSidePanel;
  private String myActionId;

  public AssistToolWindowFactory(@NotNull String actionId) {
    myActionId = actionId;
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    if (myAssistSidePanel == null) {
      myAssistSidePanel = new AssistSidePanel(myActionId, project);
    }

    ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    Content content = contentFactory.createContent(myAssistSidePanel, null, false);
    ContentManager contentManager = toolWindow.getContentManager();
    contentManager.removeAllContents(true);
    contentManager.addContent(content);
    contentManager.setSelectedContent(content);
    toolWindow.show(null);
  }
}
