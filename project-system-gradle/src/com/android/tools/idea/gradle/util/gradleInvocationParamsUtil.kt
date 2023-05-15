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
@file:JvmName("GradleInvocationParams")

package com.android.tools.idea.gradle.util

import com.android.builder.model.AndroidProject
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId

/**
 * Adds Android Studio version to [allArgs] for Gradle invocation. This is not used by AGP directly, but other tools use it for reporting.
 * See http://b/280831521 for more details.
 */
fun addAndroidStudioPluginVersion(allArgs: MutableList<String?>) {
  val studioVersion = PluginManagerCore.getPlugin(PluginId.getId("org.jetbrains.android"))?.version ?: return
  allArgs.add(AndroidGradleSettings.createProjectProperty(AndroidProject.PROPERTY_ANDROID_STUDIO_VERSION, studioVersion))
}