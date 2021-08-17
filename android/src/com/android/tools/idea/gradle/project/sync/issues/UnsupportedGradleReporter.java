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
package com.android.tools.idea.gradle.project.sync.issues;

import com.android.tools.idea.gradle.model.IdeSyncIssue;
import com.android.tools.idea.gradle.project.sync.hyperlink.CreateGradleWrapperHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenGradleSettingsHyperlink;
import com.android.tools.idea.gradle.util.GradleProjectSettingsFinder;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.project.messages.MessageType;
import com.android.tools.idea.project.messages.SyncMessage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.NonNavigatable;
import java.io.File;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

import static com.android.tools.idea.gradle.model.IdeSyncIssue.TYPE_GRADLE_TOO_OLD;
import static com.android.tools.idea.gradle.project.sync.hyperlink.FixGradleVersionInWrapperHyperlink.createIfProjectUsesGradleWrapper;
import static com.android.tools.idea.project.messages.SyncMessage.DEFAULT_GROUP;

class UnsupportedGradleReporter extends BaseSyncIssuesReporter {
  @Override
  int getSupportedIssueType() {
    return TYPE_GRADLE_TOO_OLD;
  }

  @Override
  void report(@NotNull IdeSyncIssue syncIssue, @NotNull Module module, @Nullable VirtualFile buildFile,
              @NotNull SyncIssueUsageReporter usageReporter) {
    String text = syncIssue.getMessage();
    MessageType type = getMessageType(syncIssue);
    SyncMessage message = new SyncMessage(DEFAULT_GROUP, type, NonNavigatable.INSTANCE, text);

    String gradleVersion = syncIssue.getData();
    List<NotificationHyperlink> quickFixes = getQuickFixHyperlinksWithGradleVersion(module.getProject(), gradleVersion);
    message.add(quickFixes);

    getSyncMessages(module).report(message);
    SyncIssueUsageReporterUtils.collect(usageReporter, syncIssue.getType(), quickFixes);
  }

  @NotNull
  private static List<NotificationHyperlink> getQuickFixHyperlinksWithGradleVersion(@NotNull Project project,
                                                                                   @Nullable String gradleVersion) {
    List<NotificationHyperlink> hyperlinks = new ArrayList<>();
    GradleWrapper gradleWrapper = GradleWrapper.find(project);
    if (gradleWrapper != null) {
      // It is very likely that we need to fix the model version as well. Do everything in one shot.
      NotificationHyperlink hyperlink = createIfProjectUsesGradleWrapper(project, gradleVersion);
      if (hyperlink != null) {
        hyperlinks.add(hyperlink);
      }
      File propertiesFile = gradleWrapper.getPropertiesFilePath();
      if (propertiesFile.exists()) {
        hyperlinks
          .add(new OpenFileHyperlink(gradleWrapper.getPropertiesFilePath().getAbsolutePath(), "Open Gradle wrapper properties", -1, -1));
      }
    }
    else {
      GradleProjectSettings gradleProjectSettings = GradleProjectSettingsFinder.getInstance().findGradleProjectSettings(project);
      if (gradleProjectSettings != null && gradleProjectSettings.getDistributionType() == DistributionType.LOCAL) {
        hyperlinks.add(new CreateGradleWrapperHyperlink());
      }
    }
    hyperlinks.add(new OpenGradleSettingsHyperlink());
    return hyperlinks;
  }
}
