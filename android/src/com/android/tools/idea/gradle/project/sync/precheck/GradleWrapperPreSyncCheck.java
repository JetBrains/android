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

import com.android.tools.idea.gradle.project.sync.hyperlink.ConfirmSHA256FromGradleWrapperHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.RemoveSHA256FromGradleWrapperHyperlink;
import com.android.tools.idea.gradle.project.sync.issues.SyncIssueUsageReporter;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.gradle.util.PersistentSHA256Checksums;
import com.android.tools.idea.project.messages.MessageType;
import com.android.tools.idea.project.messages.SyncMessage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import java.net.URL;
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
        if (gradleWrapper.getDistributionSha256Sum() != null) {
          if (continueWithSync(gradleWrapper)) {
            return SUCCESS;
          }
          else {
            String fileName = propertiesFilePath.getName();
            String MORE_INFO_URL = "https://github.com/gradle/gradle/issues/9361";
            String errorMessage = "It is not fully supported to define " + DISTRIBUTION_SHA_256_SUM +
                                  " in " + fileName + ".\n" +
                                  "Using an incorrect value may freeze or crash Android Studio.\n" +
                                  "Please manually verify or remove this property from all of included projects if applicable.\n" +
                                  "For more details, see " + MORE_INFO_URL + ".\n";
            SyncMessage syncMessage = new SyncMessage(SyncMessage.DEFAULT_GROUP, MessageType.WARNING, errorMessage);
            ConfirmSHA256FromGradleWrapperHyperlink confirmHyperlink = ConfirmSHA256FromGradleWrapperHyperlink.create(gradleWrapper);
            if ((confirmHyperlink != null)) {
              syncMessage.add(confirmHyperlink);
            }
            syncMessage.add(new RemoveSHA256FromGradleWrapperHyperlink());
            syncMessage.add(new OpenFileHyperlink(propertiesFilePath.getAbsolutePath(), "Open Gradle wrapper properties", -1, -1));
            GradleSyncMessages.getInstance(project).report(syncMessage);
            // Update usage tracker.
            SyncIssueUsageReporter.Companion.getInstance(project).collect(DISTRIBUTIONSHA256SUM_FOUND_IN_WRAPPER);
            return failure("Sync cancelled due to " + DISTRIBUTION_SHA_256_SUM + " in " + fileName);
          }
        }
      }
      catch (IOException e) {
        Logger.getInstance(this.getClass())
          .warn("Unsupported property " + DISTRIBUTION_SHA_256_SUM + " in " + propertiesFilePath.getPath());
      }
    }
    return SUCCESS;
  }

  private static boolean continueWithSync(@NotNull GradleWrapper wrapper) {
    PersistentSHA256Checksums checksums = PersistentSHA256Checksums.getInstance();
    try {
      String URLString = wrapper.getDistributionUrl();
      if (URLString == null) {
        // Do not continue, SHA256 is defined but not the URL
        return false;
      }
      URL url = new URL(URLString);
      if (url.getProtocol().equalsIgnoreCase("file")) {
        // Checksum is not used for file url's, we can ignore it
        return true;
      }
      return checksums.isChecksumStored(wrapper.getDistributionUrl(), wrapper.getDistributionSha256Sum());
    }
    catch (IOException ignored) {
      return false;
    }
  }
}
