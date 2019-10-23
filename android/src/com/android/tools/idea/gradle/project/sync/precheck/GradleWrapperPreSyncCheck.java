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
package com.android.tools.idea.gradle.project.sync.precheck;

import static com.android.tools.idea.gradle.project.sync.precheck.PreSyncCheckResult.SUCCESS;
import static com.android.tools.idea.gradle.project.sync.precheck.PreSyncCheckResult.failure;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.DISTRIBUTIONSHA256SUM_FOUND_IN_WRAPPER;
import static org.gradle.wrapper.WrapperExecutor.DISTRIBUTION_SHA_256_SUM;

import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.RemoveSHA256FromGradleWrapperHyperlink;
import com.android.tools.idea.gradle.project.sync.issues.SyncIssueUsageReporter;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.project.messages.MessageType;
import com.android.tools.idea.project.messages.SyncMessage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;

/**
 * Check if Gradle Wrapper has property distributionSha256Sum. Android Studio doesn't support this property because Gradle Tooling API will
 * freeze if the checksum is invalid. Fail Sync early to prevent that from happening.
 */
public class GradleWrapperPreSyncCheck extends AndroidStudioSyncCheck {
  @NotNull
  @Override
  PreSyncCheckResult doCheckCanSyncAndTryToFix(@NotNull Project project) {
    GradleWrapper gradleWrapper = GradleWrapper.find(project);
    if (gradleWrapper != null) {
      File propertiesFilePath = gradleWrapper.getPropertiesFilePath();
      try {
        if (gradleWrapper.getProperties().getProperty(DISTRIBUTION_SHA_256_SUM) != null) {
          String fileName = propertiesFilePath.getName();
          String errorMessage = "It is not supported to define " + DISTRIBUTION_SHA_256_SUM +
                                " in " + fileName + ".\n" +
                                "Please manually remove this property from all of included projects if applicable.\n" +
                                "For more details, see https://github.com/gradle/gradle/issues/9361.\n";
          SyncMessage syncMessage = new SyncMessage(SyncMessage.DEFAULT_GROUP, MessageType.ERROR, errorMessage);
          syncMessage.add(new RemoveSHA256FromGradleWrapperHyperlink());
          syncMessage.add(new OpenFileHyperlink(propertiesFilePath.getAbsolutePath(), "Open Gradle wrapper properties", -1, -1));
          GradleSyncMessages.getInstance(project).report(syncMessage);
          // Update usage tracker.
          SyncIssueUsageReporter.Companion.getInstance(project).collect(DISTRIBUTIONSHA256SUM_FOUND_IN_WRAPPER);
          return failure("Error in " + fileName);
        }
      }
      catch (IOException e) {
        Logger.getInstance(this.getClass())
          .warn("Unsupported property " + DISTRIBUTION_SHA_256_SUM + " in " + propertiesFilePath.getPath());
      }
    }
    return SUCCESS;
  }
}
