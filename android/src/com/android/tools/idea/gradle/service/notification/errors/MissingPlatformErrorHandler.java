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
package com.android.tools.idea.gradle.service.notification.errors;

import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.tools.idea.gradle.service.notification.hyperlink.InstallPlatformHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.OpenAndroidSdkManagerHyperlink;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.google.common.collect.Lists;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MissingPlatformErrorHandler extends AbstractSyncErrorHandler {
  private static final Pattern MISSING_PLATFORM_PATTERN_0 = Pattern.compile("(Cause: )?failed to find target with hash string '(.*)' in: (.*)");
  private static final Pattern MISSING_PLATFORM_PATTERN_1 = Pattern.compile("(Cause: )?failed to find target (.*) : (.*)");
  // This second format is used in older versions of the Android Gradle plug-in (0.9.+)
  private static final Pattern MISSING_PLATFORM_PATTERN_2 = Pattern.compile("(Cause: )?failed to find target (.*)");

  @Override
  public boolean handleError(@NotNull List<String> message,
                             @NotNull ExternalSystemException error,
                             @NotNull NotificationData notification,
                             @NotNull Project project) {
    String firstLine = message.get(0);

    Matcher matcher = MISSING_PLATFORM_PATTERN_0.matcher(firstLine);
    boolean missingPlatform = matcher.matches();
    if (!missingPlatform) {
      matcher = MISSING_PLATFORM_PATTERN_1.matcher(firstLine);
      missingPlatform = matcher.matches();
    }
    if (!missingPlatform) {
      matcher = MISSING_PLATFORM_PATTERN_2.matcher(firstLine);
      missingPlatform = matcher.matches();
    }
    if (missingPlatform) {
      String loadError = null;

      List<NotificationHyperlink> hyperlinks = Lists.newArrayList();
      String platform = matcher.group(2);

      AndroidSdkHandler sdkHandler = null;
      AndroidSdkData androidSdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
      if (androidSdkData != null) {
        sdkHandler = androidSdkData.getSdkHandler();
      }
      if (sdkHandler != null) {
        AndroidVersion version = AndroidTargetHash.getPlatformVersion(platform);
        if (version != null) {
          // Is the platform installed?
          ProgressIndicator logger = new StudioLoggerProgressIndicator(getClass());
          loadError = sdkHandler.getAndroidTargetManager(logger).getErrorForPackage(DetailsTypes.getPlatformPath(version));
          hyperlinks.add(new InstallPlatformHyperlink(version));
        }
      }
      if (hyperlinks.isEmpty()) {
        // We are unable to install platform automatically.
        List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
        if (!facets.isEmpty()) {
          // We can only open SDK manager if the project has an Android facet. Android facet has a reference to the Android SDK manager.
          hyperlinks.add(new OpenAndroidSdkManagerHyperlink());
        }
      }

      String newMsg = error.getMessage();
      if (StringUtil.isNotEmpty(loadError)) {
        newMsg = newMsg + "\nPossible cause: " + loadError;
      }

      updateNotification(notification, project, newMsg, hyperlinks);
      return true;
    }
    return false;
  }
}
