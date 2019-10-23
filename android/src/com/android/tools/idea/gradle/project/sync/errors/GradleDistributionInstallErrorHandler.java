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
package com.android.tools.idea.gradle.project.sync.errors;

import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.GRADLE_DISTRIBUTION_INSTALL_ERROR;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_QF_GRADLE_DISTRIBUTION_DELETED;
import static org.gradle.StartParameter.DEFAULT_GRADLE_USER_HOME;

import com.android.tools.idea.gradle.project.sync.hyperlink.DeleteFileAndSyncHyperlink;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.wrapper.PathAssembler;
import org.gradle.wrapper.WrapperConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleUtil;

public class GradleDistributionInstallErrorHandler extends SyncErrorHandler {
  public static final Pattern COULD_NOT_INSTALL_GRADLE_DISTRIBUTION_PATTERN = Pattern.compile("Could not install Gradle distribution from '(.*?)'.");

  @Override
  public boolean handleError(@NotNull ExternalSystemException error, @NotNull NotificationData notification, @NotNull Project project) {
    String msg = error.getMessage();
    if (msg == null) {
      return false;
    }
    Matcher matcher = COULD_NOT_INSTALL_GRADLE_DISTRIBUTION_PATTERN.matcher(msg);
    if (!matcher.matches()) {
      return false;
    }
    StringBuilder text = new StringBuilder(msg);
    List<NotificationHyperlink> hyperlinks = new ArrayList<>();
    WrapperConfiguration wrapperConfiguration = GradleUtil.getWrapperConfiguration(project.getBasePath());
    if (wrapperConfiguration != null) {
      PathAssembler.LocalDistribution
        localDistribution = new PathAssembler(DEFAULT_GRADLE_USER_HOME).getDistribution(wrapperConfiguration);
      File zip = localDistribution.getZipFile();
      if (zip.exists()) {
        try {
          zip = zip.getCanonicalFile();
        }
        catch (IOException e) {
          // Do nothing, use file as it is.
        }
        text.append("\nThe cached zip file ").append(zip).append(" may be corrupted.");
        hyperlinks.add(new DeleteFileAndSyncHyperlink(zip, TRIGGER_QF_GRADLE_DISTRIBUTION_DELETED));
      }
    }
    GradleSyncMessages.getInstance(project).updateNotification(notification, text.toString(), hyperlinks);
    updateUsageTracker(project, GRADLE_DISTRIBUTION_INSTALL_ERROR);
    return true;
  }
}
