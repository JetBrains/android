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
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.hash.Hashing;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class UsageTrackerAnalyticsImpl extends UsageTracker {
  private static final ExtensionPointName<UsageUploader> EP_NAME = ExtensionPointName.create("com.android.tools.idea.stats.tracker");

  private static final String GLOGS_CATEGORY_LIBCOUNT = "gradlelibs";
  private static final String GLOGS_CATEGORY_MODUDLE_COUNT = "gradlemodules";
  private static final String GLOGS_CATEGORY_ANDROID_MODUDLE = "gradleAndroidModule";
  private static final String GLOGS_CATEGORY_VERSIONS = "gradleVersions";
  private static final String GLOGS_CATEGORY_LEGACY_IDEA_ANDROID_PROJECT = "legacyIdeaAndroidProject";
  private static final String GLOGS_CATEGORY_INSTANT_RUN = "irstats2";
  private static final String GLOGS_CATEGORY_INSTANT_RUN_TIMINGS = "irtimings";
  private static final String GLOGS_CATEGORY_SYSTEM_INFO = "systeminfo";

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
  public void trackModuleCount(@NotNull String applicationId, int total, int appModuleCount, int libModuleCount) {
    if (!trackingEnabled()) {
      return;
    }

    // @formatter:off
    myUploader.trackEvent(GLOGS_CATEGORY_MODUDLE_COUNT,
                          ImmutableMap.of(
                            "appId", anonymize(applicationId),
                            "total", String.valueOf(total),
                            "apps", String.valueOf(appModuleCount),
                            "libs", String.valueOf(libModuleCount)));
    // @formatter:on
  }

  @Override
  public void trackAndroidModule(@NotNull String applicationId,
                                 @NotNull String moduleName,
                                 boolean isLibrary,
                                 int signingConfigCount,
                                 int buildTypeCount,
                                 int flavorCount,
                                 int flavorDimension) {
    if (!trackingEnabled()) {
      return;
    }
    Builder<String, String> builder = ImmutableMap.builder();

    builder.put("appId", anonymize(applicationId));
    builder.put("moduleName", anonymize(moduleName));
    builder.put("isLibrary", String.valueOf(isLibrary));
    builder.put("buildTypeCount", String.valueOf(buildTypeCount));
    builder.put("flavorCount", String.valueOf(flavorCount));
    builder.put("flavorDimension", String.valueOf(flavorDimension));

    myUploader.trackEvent(GLOGS_CATEGORY_ANDROID_MODUDLE, builder.build());
  }

  @Override
  public void trackGradleArtifactVersions(@NotNull String applicationId,
                                          @NotNull String androidPluginVersion,
                                          @NotNull String gradleVersion,
                                          @NotNull Map<String, String> instantRunSettings) {
    if (!trackingEnabled()) {
      return;
    }

    // @formatter:off
    ImmutableMap<String, String> params = ImmutableMap.<String,String>builder()
      .put("appId", anonymize(applicationId))
      .put("pluginVer", androidPluginVersion)
      .put("gradleVer", gradleVersion)
      .putAll(instantRunSettings)
      .build();
    myUploader.trackEvent(GLOGS_CATEGORY_VERSIONS, params);
    // @formatter:on
  }

  @Override
  public void trackLegacyIdeaAndroidProject(@NotNull String applicationId) {
    if (!trackingEnabled()) {
      return;
    }
    // @formatter:off
    myUploader.trackEvent(GLOGS_CATEGORY_LEGACY_IDEA_ANDROID_PROJECT,
                          ImmutableMap.of(
                            "appId", anonymize(applicationId)));
    // @formatter:on
  }

  @Override
  public void trackInstantRunStats(@NotNull Map<String, String> kv) {
    if (!trackingEnabled()) {
      return;
    }

    myUploader.trackEvent(GLOGS_CATEGORY_INSTANT_RUN, kv);
  }

  @Override
  public void trackInstantRunTimings(@NotNull Map<String, String> kv) {
    if (!trackingEnabled()) {
      return;
    }

    myUploader.trackEvent(GLOGS_CATEGORY_INSTANT_RUN_TIMINGS, kv);
  }


  @Override
  public void trackSystemInfo(@Nullable String hyperVState, @Nullable String cpuInfoFlags) {
    if (!trackingEnabled()) {
      return;
    }

    HashMap<String, String> kv = new HashMap<String, String>();
    if (hyperVState != null) {
      kv.put("hvstate", hyperVState);
    }
    if (cpuInfoFlags != null) {
      kv.put("cpuflags", cpuInfoFlags);
    }
    kv.put("os", SystemInfo.OS_NAME);
    kv.put("bits", (SystemInfo.is64Bit || SystemInfo.OS_ARCH.contains("64")) ? "64" : "32");

    myUploader.trackEvent(GLOGS_CATEGORY_SYSTEM_INFO, kv);
  }

  @NotNull
  private static String anonymize(@NotNull String applicationId) {
    return Hashing.md5().hashString(applicationId, Charsets.UTF_8).toString();
  }
}
