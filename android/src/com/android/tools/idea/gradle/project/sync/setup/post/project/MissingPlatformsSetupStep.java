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
package com.android.tools.idea.gradle.project.sync.setup.post.project;

import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenAndroidSdkManagerHyperlink;
import com.android.tools.idea.gradle.project.sync.setup.post.ProjectSetupStep;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.pom.NonNavigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.project.sync.messages.GroupNames.SDK_SETUP_ISSUES;
import static com.android.tools.idea.project.messages.MessageType.INFO;

public class MissingPlatformsSetupStep extends ProjectSetupStep {
  @Override
  public void setUpProject(@NotNull Project project, @Nullable ProgressIndicator indicator) {
    GradleSyncMessages syncMessages = GradleSyncMessages.getInstance(project);
    int sdkErrorCount = syncMessages.getMessageCount(SDK_SETUP_ISSUES);
    if (sdkErrorCount > 0) {
      // If we have errors due to platforms not being installed, we add an extra message that prompts user to open Android SDK manager and
      // install any missing platforms.
      String text = "Open Android SDK Manager and install all missing platforms.";
      SyncMessage hint = new SyncMessage(SDK_SETUP_ISSUES, INFO, NonNavigatable.INSTANCE, text);
      hint.add(new OpenAndroidSdkManagerHyperlink());
      syncMessages.report(hint);
    }
  }

  @Override
  public boolean invokeOnFailedSync() {
    return true;
  }
}
