/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.gradle

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.projectsystem.CapabilityStatus
import com.android.tools.idea.projectsystem.CapabilitySupported
import com.android.tools.idea.projectsystem.CapabilityUpgradeRequired
import com.intellij.openapi.module.Module

val MIN_PNG_GENERATION_VERSION = AgpVersion(1, 4, 0)

/**
 * Returns the gradle plugin version for the given module or null if the model is unknown
 */
fun Module.getGradlePluginVersion(): AgpVersion? {
  return GradleAndroidModel.get(this)?.let { AgpVersion.parse(it.androidProject.agpVersion) }
}

fun Module.isGradlePluginVersionAtLeast(version: AgpVersion, default: Boolean, ignoreQualifier: Boolean = true): Boolean {
  val gradleVersion = getGradlePluginVersion()
  return when {
    gradleVersion == null -> default
    ignoreQualifier -> gradleVersion.compareIgnoringQualifiers(version) >= 0
    else -> gradleVersion >= version
  }
}

/**
 * @return whether the gradle plugin used by this module supports PNG generation
 */
fun supportsPngGeneration(module: Module): CapabilityStatus {
  return if (module.isGradlePluginVersionAtLeast(MIN_PNG_GENERATION_VERSION, true))
    CapabilitySupported()
  else
    CapabilityUpgradeRequired(
        "<html><p>To support vector assets when your minimal SDK version is less than 21,<br>" +
            "the Android plugin for Gradle version must be 1.4 or above.<br>" +
            "This will allow Android Studio to convert vector assets into PNG images at build time.</p>" +
            "<p>See <a href=\"https://developer.android.com/tools/building/plugin-for-gradle.html" +
            "#projectBuildFile\">here</a> for how to update the version of Android plugin for Gradle." +
            "</p></html>",
        "Newer Android Plugin for Gradle Required")
}
