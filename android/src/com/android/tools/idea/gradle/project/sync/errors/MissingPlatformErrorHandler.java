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
package com.android.tools.idea.gradle.project.sync.errors;

import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.project.sync.hyperlink.InstallPlatformHyperlink;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenAndroidSdkManagerHyperlink;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.MISSING_ANDROID_PLATFORM;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class MissingPlatformErrorHandler extends SyncErrorHandler {
  private static final Pattern[] MISSING_PLATFORM_PATTERNS = {
    Pattern.compile("(Cause: )?(F|f)ailed to find target with hash string '(.*)' in: (.*)"),
    Pattern.compile("(Cause: )?(F|f)ailed to find target (.*) : (.*)"),
    Pattern.compile("(Cause: )?(F|f)ailed to find target (.*)")
  };

  @Override
  public boolean handleError(@NotNull ExternalSystemException error, @NotNull NotificationData notification, @NotNull Project project) {
    String text = findErrorMessage(getRootCause(error));
    if (text != null) {
      // Handle update notification inside of findAndAddQuickFixes,
      // because notification message might be changed there.
      findAndAddQuickFixes(notification, project, text);
      return true;
    }
    return false;
  }

  @Nullable
  private String findErrorMessage(@NotNull Throwable rootCause) {
    String text = rootCause.getMessage();
    if ((rootCause instanceof IllegalStateException || rootCause instanceof ExternalSystemException) &&
        isNotEmpty(text) &&
        getMissingPlatform(text) != null) {
      updateUsageTracker(MISSING_ANDROID_PLATFORM);
      return text;
    }
    return null;
  }

  @VisibleForTesting
  @Nullable
  String getMissingPlatform(@NotNull String text) {
    String firstLine = getFirstLineMessage(text);
    for (Pattern pattern : MISSING_PLATFORM_PATTERNS) {
      Matcher matcher = pattern.matcher(firstLine);
      if (matcher.matches()) {
        return matcher.group(3);
      }
    }
    return null;
  }

  private void findAndAddQuickFixes(@NotNull NotificationData notification, @NotNull Project project, @NotNull String text) {
    String missingPlatform = getMissingPlatform(text);
    if (missingPlatform == null) {
      return;
    }
    String loadError = null;
    List<NotificationHyperlink> hyperlinks = new ArrayList<>();

    AndroidSdkHandler sdkHandler = null;
    AndroidSdkData androidSdkData = AndroidSdks.getInstance().tryToChooseAndroidSdk();
    if (androidSdkData != null) {
      sdkHandler = androidSdkData.getSdkHandler();
    }
    if (sdkHandler != null) {
      AndroidVersion version = AndroidTargetHash.getPlatformVersion(missingPlatform);
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

    if (isNotEmpty(loadError)) {
      text += "\nPossible cause: " + loadError;
    }
    GradleSyncMessages.getInstance(project).updateNotification(notification, text, hyperlinks);
  }
}