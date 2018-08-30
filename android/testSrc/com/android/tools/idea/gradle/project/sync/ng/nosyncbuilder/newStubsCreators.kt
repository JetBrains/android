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

import com.android.SdkConstants.FN_BUILD_GRADLE
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.androidproject.AaptOptions
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.androidproject.AndroidProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.javaproject.JavaProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.library.AndroidLibrary
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.library.JavaLibrary
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.library.ModuleDependency
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.library.NativeLibrary
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.InstantRun
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.Variant
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.PathConverter
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.androidproject.NewAaptOptions
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.androidproject.NewAndroidProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.androidproject.NewJavaCompileOptions
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.javaproject.MAIN_SOURCE_SET
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.javaproject.NewJavaProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.javaproject.NewJavaSourceSet
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.javaproject.TEST_SOURCE_SET
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.gradleproject.NewGradleProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.gradleproject.NewGradleScript
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.gradleproject.NewGradleTask
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.gradleproject.NewProjectIdentifier
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.library.NewAndroidLibrary
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.library.NewGlobalLibraryMap
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.library.NewJavaLibrary
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.library.NewModuleDependency
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.variant.*
import java.io.File

val modulePath = File("/path/to/module")
val sdkPath = File("/path/to/sdk")
val buildPath = File(modulePath, "build")
val outPath = File("/path/to/out")
val bundlePath = File("/path/to/bundle")

val testConverter = PathConverter(modulePath, sdkPath, outPath, bundlePath)

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

fun createNewJavaProject(): JavaProject = NewJavaProject(
  name = "javaProject",
  mainSourceSet = createNewJavaSourceSet(MAIN_SOURCE_SET),
  testSourceSet = createNewJavaSourceSet(TEST_SOURCE_SET),
  extraSourceSets = listOf(),
  javaLanguageLevel = "1.7"
)

fun createNewVariant(): Variant {
  val instantRun = NewInstantRun(
    File(buildPath, "info/file"),
    true,
    InstantRun.Status.SUPPORTED
  )

  val dependencies = NewDependencies(listOf(), listOf(), listOf(), listOf())

  val mergedSourceProvider = NewArtifactSourceProvider(
    null,
    createNewAndroidSourceSet("buildType"),
    null,
    listOf(),
    createNewAndroidSourceSet("default"),
    File(buildPath, "classes/folder"),
    listOf(),
    File(buildPath, "java/resources/folder"),
    listOf(),
    listOf()
  )

  // TODO add boolean flags to create Variant with [Android/unit] test artifacts
  val mainArtifact = NewAndroidArtifact(
    "sourceGenTaskName",
    null,
    false,
    "com.google.application.id",
    listOf(),
    instantRun,
    listOf(),
    null,
    "app",
    "compileTaskName",
    "assembleTaskName",
    dependencies,
    mergedSourceProvider,
    listOf("ideSetupTaskName"),
    "instrumentedTestTaskName",
    "bundleTaskName",
    null
  )

  val apiVersion = NewApiVersion(
    27,
    "Oreo",
    "Oreo"
  )

  val variantConfig = NewVariantConfig(
    "app",
    mapOf(),
    listOf(),
    listOf(),
    mapOf(),
    true,
    "com.google.application.id",
    1,
    "version 1",
    apiVersion,
    apiVersion,
    listOf()
  )

  val testedTargetVariant = NewTestedTargetVariant("/path/to/target/project", "app")

  return NewVariant(
    "app",
    "app",
    mainArtifact,
    null,
    null,
    variantConfig,
    listOf(testedTargetVariant)
  )
}

fun createNewAndroidSourceSet(name: String) = NewAndroidSourceSet(
  name,
  File(modulePath, "${name}Manifest.xml"),
  listOf(File(buildPath, "${name}JavaDirectory")),
  listOf(File(buildPath,"${name}JavaResourcesDirectory")),
  listOf(File(buildPath,"${name}aidlDirectory")),
  listOf(File(buildPath,"${name}renderscriptDirectory")),
  listOf(File(buildPath,"${name}cDirectory")),
  listOf(File(buildPath,"${name}cppDirectory")),
  listOf(File(buildPath,"${name}androidResourcesDirectory")),
  listOf(File(buildPath,"${name}assetsDirectory")),
  listOf(File(buildPath,"${name}jniLibsDirectory")),
  listOf(File(buildPath,"${name}shadersDirectory"))
)

fun createNewJavaSourceSet(name: String) = NewJavaSourceSet(
  name,
  listOf(File(buildPath, "${name}SourceDirectory")),
  listOf(File(buildPath, "${name}ResourceDirectory")),
  File(buildPath, "${name}ClassesOutputDirectory"),
  File(buildPath, "${name}ResourcesOutputDirectory"),
  createNewJavaLibraries(3).values.toList(),
  createNewModuleLibraries(2).values.toList()
)

fun createNewGlobalLibraryMap() = NewGlobalLibraryMap(
  createNewAndroidLibraries(1),
  createNewJavaLibraries(2),
  mapOf(), // TODO use createNativeLibraries when it will be implemented
  createNewModuleLibraries(3)
)

fun createNewAndroidLibraries(count: Int): Map<String, AndroidLibrary> {
  val androidLibraries = mutableMapOf<String, AndroidLibrary>()
  for (i in 1..count) {
    val artifactAddress = "pkg:android_library:$i@aar"
    androidLibraries[artifactAddress] = NewAndroidLibrary(
      File(outPath, "pkg/android_library/$i"),
      listOf(File(bundlePath,  "pkg/android_library/$i/jars/libs/local_jar_$i")),
      File(bundlePath, "pkg/android_library/$i"),
      artifactAddress
    )
  }
  return androidLibraries
}

fun createNewJavaLibraries(count: Int): Map<String, JavaLibrary> {
  val javaLibraries = mutableMapOf<String, JavaLibrary>()
  for (i in 1..count) {
    val artifactAddress = "pkg:java_library:$i@jar"
    javaLibraries[artifactAddress] = NewJavaLibrary(
      File(outPath, "pkg/java_library/$i"),
      artifactAddress
    )
  }
  return javaLibraries
}

fun createNewModuleLibraries(count: Int): Map<String, ModuleDependency> {
  val moduleDependencies = mutableMapOf<String, ModuleDependency>()
  for (i in 1..count) {
    val artifactAddress = "pkg:module_dependency:$i@mod"
    moduleDependencies[artifactAddress] = NewModuleDependency(
      "build_id_$i",
      "project_path_$i",
      "variant_$i",
      artifactAddress
    )
  }
  return moduleDependencies
}

fun createNativeLibraries(): Map<String, NativeLibrary> = TODO("not implemented yet")

const val GRADLE_PROJECT_NAME = "gradleProjectName"

fun createNewGradleProject(): NewGradleProject = NewGradleProject(
  buildScript = NewGradleScript(File(modulePath, FN_BUILD_GRADLE)),
  buildDirectory = File(modulePath, "build"),
  projectDirectory = modulePath,
  tasks = createNewGradleTasks(2),
  name = GRADLE_PROJECT_NAME,
  projectIdentifier = NewProjectIdentifier(":$GRADLE_PROJECT_NAME", modulePath),
  description = null
).apply {
  tasks.forEach { (it as NewGradleTask).project = this }
}

const val GRADLE_TASK_NAME = "gradleTaskName"

fun createNewGradleTasks(count: Int): List<NewGradleTask> = (1..count).map {
  NewGradleTask(
    name = "$GRADLE_TASK_NAME$it",
    displayName = "task '$GRADLE_TASK_NAME$it'",
    path = ":$GRADLE_PROJECT_NAME:module$it",
    isPublic = true,
    group = null,
    description = null
  )
}
