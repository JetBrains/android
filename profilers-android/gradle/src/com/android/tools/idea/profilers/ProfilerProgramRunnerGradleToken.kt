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
package com.android.tools.idea.profilers

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem

class ProfilerProgramRunnerGradleToken : ProfilerProgramRunnerToken<GradleProjectSystem>, GradleToken {
  override fun isProfileableBuildSupported(projectSystem: GradleProjectSystem): Boolean {
    val project = projectSystem.project
    val agpVersion = GradleProjectSystemUtil.getLastKnownAndroidGradlePluginVersion(
      project)?.let { AgpVersion.tryParse(it) }
    return agpVersion != null && agpVersion.isAtLeastIncludingPreviews(7, 3, 0)
  }
}
