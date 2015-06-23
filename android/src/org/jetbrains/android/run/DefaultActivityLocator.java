/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.jetbrains.android.run;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.model.ManifestInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DefaultActivityLocator extends ActivityLocator {
  @NotNull
  private final AndroidFacet myFacet;

  public DefaultActivityLocator(@NotNull AndroidFacet facet) {
    myFacet = facet;
  }

  @NotNull
  @Override
  protected String getQualifiedActivityName() {
    String activityName = computeDefaultActivity(myFacet);
    assert activityName != null; // validated by validate below
    return activityName;
  }

  @Override
  public void validate(@NotNull AndroidFacet facet) throws ActivityLocatorException {
    String activity = computeDefaultActivity(myFacet);
    if (activity == null) {
      throw new ActivityLocatorException(AndroidBundle.message("default.activity.not.found.error"));
    }
  }

  @Nullable
  @VisibleForTesting
  static String computeDefaultActivity(@NotNull final AndroidFacet facet) {
    assert !facet.getProperties().USE_CUSTOM_COMPILER_MANIFEST;
    final ManifestInfo manifestInfo = ManifestInfo.get(facet.getModule(), ActivityLocatorUtils.shouldUseMergedManifest(facet));

    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return AndroidUtils.getDefaultLauncherActivityName(manifestInfo.getActivities(), manifestInfo.getActivityAliases());
      }
    });
  }
}
