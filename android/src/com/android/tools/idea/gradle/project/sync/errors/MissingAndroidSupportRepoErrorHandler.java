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

import com.android.annotations.Nullable;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenAndroidSdkManagerHyperlink;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.MISSING_ANDROID_SUPPORT_REPO;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class MissingAndroidSupportRepoErrorHandler extends BaseSyncErrorHandler {
  private static final String INSTALL_ANDROID_SUPPORT_REPO = "Please install the Android Support Repository from the Android SDK Manager.";

  @Override
  @Nullable
  protected String findErrorMessage(@NotNull Throwable rootCause, @NotNull Project project) {
    String text = rootCause.getMessage();

    // With this condition we cover 2 similar messages about the same problem.
    if (rootCause instanceof RuntimeException &&
        isNotEmpty(text) &&
        text.contains("Could not find") &&
        text.contains("com.android.support:")) {
      updateUsageTracker(MISSING_ANDROID_SUPPORT_REPO);
      // We keep the original error message and we append a hint about how to fix the missing dependency.
      text += EMPTY_LINE + INSTALL_ANDROID_SUPPORT_REPO;
      return text;
    }
    return null;
  }

  @Override
  @NotNull
  protected List<NotificationHyperlink> getQuickFixHyperlinks(@NotNull Project project, @NotNull String text) {
    List<NotificationHyperlink> hyperlinks = new ArrayList<>();
    List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
    if (!facets.isEmpty()) {
      // We can only open SDK manager if the project has an Android facet. Android facet has a reference to the Android SDK manager.
      hyperlinks.add(new OpenAndroidSdkManagerHyperlink());
    }
    return hyperlinks;
  }
}