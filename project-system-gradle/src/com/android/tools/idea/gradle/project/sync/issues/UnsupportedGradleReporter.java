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
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileSyncMessageHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenGradleSettingsHyperlink;
import com.android.tools.idea.gradle.util.GradleProjectSettingsFinder;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.project.messages.MessageType;
import com.android.tools.idea.project.messages.SyncMessage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.NonNavigatable;
import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

import static com.android.tools.idea.gradle.model.IdeSyncIssue.TYPE_GRADLE_TOO_OLD;
import static com.android.tools.idea.gradle.project.sync.hyperlink.FixGradleVersionInWrapperHyperlink.createIfProjectUsesGradleWrapper;
import static com.android.tools.idea.project.messages.SyncMessage.DEFAULT_GROUP;

class UnsupportedGradleReporter extends SimpleDeduplicatingSyncIssueReporter {
  @Override
  int getSupportedIssueType() {
    return TYPE_GRADLE_TOO_OLD;
  }

  @Override
  protected @NotNull SyncMessage setupSyncMessage(@NotNull Project project,
                                                  @NotNull List<IdeSyncIssue> syncIssues,
                                                  @NotNull List<Module> affectedModules,
                                                  @NotNull Map<Module, VirtualFile> buildFileMap,
                                                  @NotNull MessageType type) {
    if (syncIssues.size() != 1) throw new IllegalStateException("Must not have been de-duplicated because of getDeduplicationKey()");
    final var syncIssue = syncIssues.get(0);
    String text = syncIssue.getMessage();
    SyncMessage message = new SyncMessage(DEFAULT_GROUP, type, NonNavigatable.INSTANCE, text);

    String gradleVersion = syncIssue.getData();
    List<SyncIssueNotificationHyperlink> quickFixes = getQuickFixHyperlinksWithGradleVersion(project, gradleVersion);
    message.add(quickFixes);
    return message;
  }

  @Override
  protected @NotNull Object getDeduplicationKey(@NotNull IdeSyncIssue issue) {
    return new Object(); // Do not deduplicate.
  }

  @Override
  protected boolean shouldIncludeModuleLinks() {
    return false;
  }

  @NotNull
  private static List<SyncIssueNotificationHyperlink> getQuickFixHyperlinksWithGradleVersion(@NotNull Project project,
                                                                                             @Nullable String gradleVersion) {
    List<SyncIssueNotificationHyperlink> hyperlinks = new ArrayList<>();
    GradleWrapper gradleWrapper = GradleWrapper.find(project);
    if (gradleWrapper != null) {
      // It is very likely that we need to fix the model version as well. Do everything in one shot.
      SyncIssueNotificationHyperlink hyperlink = createIfProjectUsesGradleWrapper(project, gradleVersion);
      if (hyperlink != null) {
        hyperlinks.add(hyperlink);
      }
      File propertiesFile = gradleWrapper.getPropertiesFilePath();
      if (propertiesFile.exists()) {
        hyperlinks
          .add(
            new OpenFileSyncMessageHyperlink(gradleWrapper.getPropertiesFilePath().getAbsolutePath(), "Open Gradle wrapper properties", -1,
                                             -1));
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
