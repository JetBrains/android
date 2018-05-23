/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.androidproject.AaptOptions
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.androidproject.AndroidProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.PathConverter
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.androidproject.NewAaptOptions
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.androidproject.NewAndroidProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.androidproject.NewJavaCompileOptions
import java.io.File

val modulePath = File("/path/to/module")
val sdkPath = File("/path/to/sdk")
val buildPath = File(modulePath, "build")

val testConverter = PathConverter(modulePath, sdkPath)

fun createNewAndroidProject(): AndroidProject {
  val aaptOptions = NewAaptOptions(AaptOptions.Namespacing.REQUIRED)
  val javaCompileOptions = NewJavaCompileOptions("utf-8", "1.8", "1.8")

  return NewAndroidProject(
    modelVersion = "27.0.3",
    name = "legacyTestProject",
    projectType = AndroidProject.ProjectType.APP,
    variantNames = listOf("debug", "release"),
    compileTarget = "android-28",
    bootClasspath = listOf(File(sdkPath, "path/to/Android.jar")),
    aaptOptions = aaptOptions,
    syncIssues = listOf(),
    javaCompileOptions = javaCompileOptions,
    buildFolder = buildPath,
    isBaseSplit = false,
    dynamicFeatures = listOf(), // TODO add flag for dynamic features
    rootFolder = modulePath,
    signingConfigs = listOf() // TODO add flag for signing configs
  )
}
