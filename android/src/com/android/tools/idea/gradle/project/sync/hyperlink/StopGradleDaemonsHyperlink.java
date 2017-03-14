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
package com.android.tools.idea.gradle.project.sync.hyperlink;

import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.util.GradleUtil.stopAllGradleDaemonsAndRestart;

public class StopGradleDaemonsHyperlink extends NotificationHyperlink {
  @NotNull
  public static NotificationHyperlink createStopGradleDaemonsHyperlink() {
    if (ApplicationManager.getApplication().isRestartCapable()) {
      return new StopGradleDaemonsHyperlink();
    }
    return new OpenUrlHyperlink("http://www.gradle.org/docs/current/userguide/gradle_daemon.html",
                                "Open Gradle Daemon documentation");
  }

  private StopGradleDaemonsHyperlink() {
    super("stopGradleDaemons", "Stop Gradle build processes (requires restart)");
  }

  @Override
  protected void execute(@NotNull Project project) {
    String title = "Stop Gradle Daemons";
    String message = "Stopping all Gradle daemons will terminate any running Gradle builds (e.g. from the command line).\n" +
                     "This action will also restart the IDE.\n\n" +
                     "Do you want to continue?";
    int answer = Messages.showYesNoDialog(project, message, title, Messages.getQuestionIcon());
    if (answer == Messages.YES) {
      stopAllGradleDaemonsAndRestart();
    }
  }
}
