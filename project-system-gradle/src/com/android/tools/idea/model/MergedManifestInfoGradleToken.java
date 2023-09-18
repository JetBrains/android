/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.model;

import com.android.manifmerger.ManifestMerger2;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.projectsystem.GradleToken;
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem;
import com.intellij.openapi.project.Project;

public class MergedManifestInfoGradleToken implements MergedManifestInfoToken<GradleProjectSystem>, GradleToken {

  @Override
  public ManifestMerger2.Invoker withProjectSystemFeatures(GradleProjectSystem projectSystem, ManifestMerger2.Invoker invoker) {
    if (!isVersionAtLeast7_4_0(projectSystem.getProject())) {
      invoker.withFeatures(ManifestMerger2.Invoker.Feature.DISABLE_STRIP_LIBRARY_TARGET_SDK);
    }
    return invoker;
  }

  private static boolean isVersionAtLeast7_4_0(Project project) {
    AndroidPluginInfo androidPluginInfo = AndroidPluginInfo.findFromModel(project);
    return androidPluginInfo != null &&
           androidPluginInfo.getPluginVersion() != null &&
           androidPluginInfo.getPluginVersion().isAtLeast(7, 4, 0);
  }
}