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
package com.android.tools.idea.gradle.project.build.output;

import com.android.tools.idea.gradle.project.build.events.AndroidSyncIssueOutputEvent;
import com.intellij.build.BuildConsoleUtils;
import com.intellij.build.BuildTextConsoleView;
import com.intellij.build.events.BuildEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Console view used to display the raw output from sync. It is used in order to make sure the AndroidSyncIssueOutputEvents are
 * rendered with the links and quickfixes they need.
 */
public class AndroidGradleSyncTextConsoleView extends BuildTextConsoleView {
  public AndroidGradleSyncTextConsoleView(@NotNull Project project) {
    super(project);
  }

  @Override
  public void onEvent(@NotNull Object buildId, @NotNull BuildEvent event) {
    if (event instanceof AndroidSyncIssueOutputEvent) {
      BuildConsoleUtils.printDetails(this, ((AndroidSyncIssueOutputEvent)event).getFailure(), event.getMessage());
    }
    else {
      super.onEvent(buildId, event);
    }
  }
}
