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
package com.android.tools.idea.npw.module.recipes.androidProject

import com.android.tools.idea.wizard.template.GradlePluginVersion
import com.android.tools.idea.wizard.template.renderIf


fun androidProjectBuildGradle(
  generateKotlin: Boolean,
  kotlinVersion: String,
  gradlePluginVersion: GradlePluginVersion): String {
  return """
    // Top-level build file where you can add configuration options common to all sub-projects/modules.
    plugins {
        id 'com.android.application' version '$gradlePluginVersion' apply false
        id 'com.android.library' version '$gradlePluginVersion' apply false
        ${renderIf(generateKotlin) { "id 'org.jetbrains.kotlin.android' version '$kotlinVersion' apply false" }}
    }

    task clean (type: Delete) {
        delete rootProject.buildDir
    }
    """
}

fun androidProjectBuildGradleKts(
  generateKotlin: Boolean,
  kotlinVersion: String,
  gradlePluginVersion: GradlePluginVersion): String {
  return """
    // Top-level build file where you can add configuration options common to all sub-projects/modules.
    plugins {
        id("com.android.application") version "$gradlePluginVersion" apply false
        id("com.android.library") version "$gradlePluginVersion" apply false
        ${renderIf(generateKotlin) { "id(\"org.jetbrains.kotlin.android\") version \"$kotlinVersion\" apply false" }}
    }

    tasks.register("clean", Delete::class) {
        delete(rootProject.buildDir)
    }
    """
}