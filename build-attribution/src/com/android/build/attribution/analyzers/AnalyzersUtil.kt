/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.build.attribution.analyzers

import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.TaskData

fun isAndroidGradlePlugin(plugin: PluginData): Boolean {
  return plugin.displayName == "com.android.application"
}

fun isJavaPlugin(plugin: PluginData): Boolean {
  return plugin.displayName == "application" ||
         plugin.displayName == "java" ||
         plugin.displayName == "java-base" ||
         plugin.displayName == "java-gradle-plugin" ||
         plugin.displayName == "java-library" ||
         plugin.displayName == "java-platform"
}

fun isAndroidPlugin(plugin: PluginData): Boolean {
  return plugin.displayName == "com.android.application" ||
         plugin.displayName == "com.android.library" ||
         plugin.displayName == "com.android.instantapp" ||
         plugin.displayName == "com.android.feature" ||
         plugin.displayName == "com.android.dynamic-feature" ||
         plugin.displayName == "com.android.test"
}

fun isKotlinPlugin(plugin: PluginData): Boolean {
  return plugin.displayName == "kotlin" ||
         plugin.displayName == "kotlin-android" ||
         plugin.displayName == "kotlin-android-extensions" ||
         plugin.displayName == "kotlin-multiplatform" ||
         plugin.displayName == "kotlin-kapt" ||
         plugin.displayName.startsWith("org.jetbrains.kotlin")
}

fun isGradlePlugin(plugin: PluginData) = plugin.displayName.startsWith("org.gradle.")

fun isKaptTask(task: TaskData): Boolean {
  return task.taskType == "org.jetbrains.kotlin.gradle.internal.KaptTask" ||
         task.taskType == "org.jetbrains.kotlin.gradle.internal.KaptWithKotlincTask" ||
         task.taskType == "org.jetbrains.kotlin.gradle.internal.KaptWithoutKotlincTask" ||
         task.taskType == "org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask"
}
