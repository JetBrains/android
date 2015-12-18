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
package com.android.tools.idea.stats;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UsageTrackerAnalyticsImpl extends UsageTracker {
  private static final ExtensionPointName<UsageUploader> EP_NAME = ExtensionPointName.create("com.android.tools.idea.stats.tracker");

  private static final String GLOGS_CATEGORY_LIBCOUNT = "gradlelibs";
  private static final String GLOGS_CATEGORY_VERSIONS = "gradleVersions";

  private final UsageUploader myUploader;

  public UsageTrackerAnalyticsImpl() {
    UsageUploader[] uploaders = EP_NAME.getExtensions();
    myUploader = uploaders.length > 0 ? uploaders[0] : null;
  }

  private boolean trackingEnabled() {
    return myUploader != null && canTrack();
  }

  @Override
  public void trackEvent(@NotNull String eventCategory,
                         @NotNull String eventAction,
                         @Nullable String eventLabel,
                         @Nullable Integer eventValue) {
    if (!trackingEnabled()) {
      return;
    }

    myUploader.trackEvent(eventCategory, eventAction, eventLabel, eventValue);
  }

  @Override
  public void trackLibraryCount(@NotNull String applicationId, int jarDependencyCount, int aarDependencyCount) {
    if (!trackingEnabled()) {
      return;
    }

    // @formatter:off
    myUploader.trackEvent(GLOGS_CATEGORY_LIBCOUNT,
                          ImmutableMap.of(
                            "appId", anonymize(applicationId),
                            "jars", Integer.toString(jarDependencyCount),
                            "aars", Integer.toString(aarDependencyCount)));
    // @formatter:on
  }

  @Override
  public void trackGradleArtifactVersions(@NotNull String applicationId,
                                          @NotNull String androidPluginVersion,
                                          @NotNull String gradleVersion) {
    if (!trackingEnabled()) {
      return;
    }
    myUploader.trackEvent(GLOGS_CATEGORY_VERSIONS,
                          ImmutableMap.of(
                            "appId", anonymize(applicationId),
                            "pluginVer", androidPluginVersion,
                            "gradleVer", gradleVersion));
  }

  @NotNull
  private static String anonymize(@NotNull String applicationId) {
    return Hashing.md5().hashString(applicationId, Charsets.UTF_8).toString();
  }
}
