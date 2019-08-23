/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.whatsnew.assistant.actions;

import com.android.tools.idea.assistant.AssistActionHandler;
import com.android.tools.idea.assistant.datamodel.ActionData;
import com.android.tools.idea.whatsnew.assistant.WhatsNewAssistantMetricsTracker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

final class WhatsNewDismissAction implements AssistActionHandler {
  static final String ACTION_KEY = "wna.dismiss";

  @NotNull
  @Override
  public String getId() {
    return ACTION_KEY;
  }

  @Override
  public void handleAction(@NotNull ActionData actionData, @NotNull Project project) {
    ToolWindowManager.getInstance(project).getToolWindow("Assistant").hide(() -> {});
    WhatsNewAssistantMetricsTracker.getInstance().dismissed(project);
  }
}
