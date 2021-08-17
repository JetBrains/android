/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.common

import com.android.builder.model.AndroidProject
import com.android.tools.idea.gradle.util.AndroidGradleSettings
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId

fun addAndroidSupportVersionArg(allArgs: MutableList<String?>) {
  // Obtains the version of the Android Support plugin.
  val androidSupport = PluginManagerCore.getPlugin(
    PluginId.getId("org.jetbrains.android"))
  if (androidSupport != null && !isDevBuild(androidSupport.version)) {
    // Example of version to pass: 2.4.0.6
    allArgs.add(AndroidGradleSettings.createProjectProperty(AndroidProject.PROPERTY_ANDROID_SUPPORT_VERSION, androidSupport.version))
  }
}

private fun isDevBuild(version: String): Boolean {
  return version == "dev build" // set in org.jetbrains.android plugin.xml
}
