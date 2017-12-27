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
package com.android.tools.idea.gradle.project.sync.compatibility.version;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.plugin.AndroidPluginGeneration;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.project.sync.hyperlink.FixAndroidGradlePluginVersionHyperlink;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenUrlHyperlink;
import com.android.tools.idea.gradle.util.PositionInFile;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static java.util.Collections.emptyList;

/**
 * Obtains the version of the Android Gradle plugin that a project is using.
 */
class AndroidGradlePluginVersionReader implements ComponentVersionReader {
  @NotNull private final AndroidPluginGeneration myPluginGeneration;

  AndroidGradlePluginVersionReader(@NotNull AndroidPluginGeneration pluginGeneration) {
    myPluginGeneration = pluginGeneration;
  }

  @Override
  public boolean appliesTo(@NotNull Module module) {
    if (AndroidFacet.getInstance(module) == null) {
      return false;
    }
    AndroidPluginInfo pluginInfo = AndroidPluginInfo.find(module.getProject());
    return isSupportedGeneration(pluginInfo);
  }

  @Override
  @Nullable
  public String getComponentVersion(@NotNull Module module) {
    AndroidPluginInfo pluginInfo = AndroidPluginInfo.find(module.getProject());
    if (isSupportedGeneration(pluginInfo)) {
      GradleVersion pluginVersion = pluginInfo.getPluginVersion();
      return pluginVersion != null ? pluginVersion.toString() : null;
    }
    return null;
  }

  @Override
  @Nullable
  public PositionInFile getVersionSource(@NotNull Module module) {
    return null;
  }

  @Override
  @NotNull
  public List<NotificationHyperlink> getQuickFixes(@NotNull Module module,
                                                   @Nullable VersionRange expectedVersion,
                                                   @Nullable PositionInFile location) {
    AndroidPluginInfo pluginInfo = AndroidPluginInfo.find(module.getProject());
    if (isSupportedGeneration(pluginInfo)) {
      String version = pluginInfo.getPluginGeneration().getLatestKnownVersion();
      List<NotificationHyperlink> quickFixes = new ArrayList<>();
      quickFixes.add(new FixAndroidGradlePluginVersionHyperlink(GradleVersion.parse(version), GradleVersion.parse(GRADLE_LATEST_VERSION)));
      quickFixes.add(new OpenUrlHyperlink("https://developer.android.com/studio/releases/gradle-plugin.html#updating-gradle", "Open Documentation"));
      return quickFixes;
    }
    return emptyList();
  }

  private boolean isSupportedGeneration(@Nullable AndroidPluginInfo pluginInfo) {
    return pluginInfo != null && myPluginGeneration == pluginInfo.getPluginGeneration();
  }

  @Override
  public boolean isProjectLevel() {
    return true;
  }

  @Override
  @NotNull
  public String getComponentName() {
    return myPluginGeneration.getDescription();
  }
}
