/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.notification.hyperlink;

import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class StopGradleDaemonsAndSyncHyperlink extends NotificationHyperlink {
  public StopGradleDaemonsAndSyncHyperlink() {
    super("stopGradleDaemons", "Stop Gradle daemons and sync project");
  }

  @Override
  protected void execute(@NotNull Project project) {
    String title = "Stop Gradle Daemons";
    String message = "Stopping all Gradle daemons will terminate any running Gradle builds (e.g. from the command line.)\n\n" +
                     "Do you want to continue?";
    int answer = Messages.showYesNoDialog(project, message, title, Messages.getQuestionIcon());
    if (answer == Messages.YES) {
      GradleUtil.stopAllGradleDaemons();
      GradleProjectImporter.getInstance().requestProjectSync(project, null);
    }
  }
}
