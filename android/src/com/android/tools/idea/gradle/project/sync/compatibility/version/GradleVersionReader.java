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
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.project.sync.hyperlink.FixAndroidGradlePluginVersionHyperlink;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenUrlHyperlink;
import com.android.tools.idea.gradle.util.GradleVersions;
import com.android.tools.idea.gradle.util.PositionInFile;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;

/**
 * Obtains the version of Gradle that a project is using.
 */
class GradleVersionReader implements ComponentVersionReader {
  @Override
  public boolean appliesTo(@NotNull Module module) {
    return GradleFacet.getInstance(module) != null;
  }

  @Override
  @Nullable
  public String getComponentVersion(@NotNull Module module) {
    GradleVersion gradleVersion = GradleVersions.getInstance().getGradleVersion(module.getProject());
    return gradleVersion != null ? gradleVersion.toString() : null;
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
    List<NotificationHyperlink> quickFixes = new ArrayList<>();
    AndroidPluginInfo pluginInfo = AndroidPluginInfo.find(module.getProject());
    if (pluginInfo != null) {
      GradleVersion pluginVersion = GradleVersion.parse(pluginInfo.getPluginGeneration().getLatestKnownVersion());
      GradleVersion gradleVersion = GradleVersion.parse(GRADLE_LATEST_VERSION);
      String text = "Fix Gradle version (as part of the update, the Android plugin will be updated to version " + pluginVersion + ")";
      quickFixes.add(new FixAndroidGradlePluginVersionHyperlink(text, pluginVersion, gradleVersion));
    }
    quickFixes.add(new OpenUrlHyperlink("https://developer.android.com/studio/releases/index.html#Revisions", "Open Documentation"));
    return quickFixes;
  }

  @Override
  public boolean isProjectLevel() {
    return true;
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "Gradle";
  }
}
