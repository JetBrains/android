/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.gradle.plugin.AgpVersions
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionContext
import org.jetbrains.plugins.gradle.service.project.GradleExecutionHelperExtension
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings

/**
 * This class allows projects to query for the "corresponding" AGP version to the running Android
 * Studio version, somewhat allowing projects to automatically use the latest possible AGP version
 * by wiring in this version.  I don't think this is desperately well-founded, but it also doesn't
 * particularly hurt to provide this information.
 */
class AgpVersionExecutionHelperExtension : GradleExecutionHelperExtension {
  override fun configureSettings(settings: GradleExecutionSettings, context: GradleExecutionContext) {
    settings.withArgument("-Dandroid.studio.latest.known.compatible.agp.version=${AgpVersions.latestKnown}")
  }
}