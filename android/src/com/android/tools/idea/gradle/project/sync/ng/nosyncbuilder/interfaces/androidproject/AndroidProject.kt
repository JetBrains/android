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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.androidproject

import com.android.builder.model.SyncIssue
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.OldAndroidProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.PathConverter
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.AndroidProjectProto
import java.io.File

/**
 * Entry point for the model of the Android Projects. This models a single module, whether the
 * module is an app project, a library project, a Instant App feature project, an instantApp bundle
 * project, or a dynamic feature split project.
 */
interface AndroidProject {
  // TODO: add Properties from model.AndroidProject

  /**
   * Represents a possible type of the project.
   *
   * Contains [oldValue] integer which is used for protobuf serialization.
   */
  enum class ProjectType(val oldValue: Int) {
    APP(OldAndroidProject.PROJECT_TYPE_APP),
    LIBRARY(OldAndroidProject.PROJECT_TYPE_LIBRARY),
    TEST(OldAndroidProject.PROJECT_TYPE_TEST),
    INSTANTAPP(OldAndroidProject.PROJECT_TYPE_INSTANTAPP),
    FEATURE(OldAndroidProject.PROJECT_TYPE_FEATURE),
    DYNAMIC_FEATURE(OldAndroidProject.PROJECT_TYPE_DYNAMIC_FEATURE);

    companion object {
      fun fromValue(value: Int): ProjectType = when (value) {
        OldAndroidProject.PROJECT_TYPE_APP -> APP
        OldAndroidProject.PROJECT_TYPE_LIBRARY -> LIBRARY
        OldAndroidProject.PROJECT_TYPE_TEST -> TEST
        OldAndroidProject.PROJECT_TYPE_INSTANTAPP -> INSTANTAPP
        OldAndroidProject.PROJECT_TYPE_FEATURE -> FEATURE
        OldAndroidProject.PROJECT_TYPE_DYNAMIC_FEATURE -> DYNAMIC_FEATURE
        else -> throw IllegalStateException("Nonexistent project type")
      }
    }
  }

  /** The model version. A string in the format X.Y.Z */
  val modelVersion: String
  /** The name of the module. e.g "app" */
  val name: String
  /** The type of project. */
  val projectType: ProjectType
  /**
   * The list of all the variant names.
   *
   * Does not include test variant. Test variants are additional artifacts in their respective variant info.
   *
   * @since 3.2
   */
  val variantNames: Collection<String>
  /**
   * The compilation target as a full extended target hash string.
   *
   * @see com.android.sdklib.IAndroidTarget.hashString()
   */
  val compileTarget: String
  /** The boot classpath matching the compile target. Typically `android.jar` plus other optional libraries. */
  val bootClasspath: Collection<File>
  /** The list of [SigningConfig]s */
  val signingConfigs: Collection<SigningConfig>
  /** Indicates whether project is namespace aware or not. */
  val aaptOptions: AaptOptions
  /**
   * Issues found during sync.
   *
   * Populated only if the system property [PROPERTY_BUILD_MODEL_ONLY] has been set to `true`. FIXME(comment is wrong?)
   */
  val syncIssues: Collection<SyncIssue>
  /** The compile options for Java code. */
  val javaCompileOptions: JavaCompileOptions
  /** The path of build directory. */
  val buildFolder: File
  /** The path of module directory. */
  val rootFolder: File
  /** True if this is the base feature split. */
  val isBaseSplit: Boolean
  /**
   * The list of dynamic features.
   *
   * Each value is a Gradle path. Only valid for base splits.
   */
  val dynamicFeatures: Collection<String>

  fun toProto(converter: PathConverter): AndroidProjectProto.AndroidProject = AndroidProjectProto.AndroidProject.newBuilder()
    .setProjectType(AndroidProjectProto.AndroidProject.ProjectType.valueOf(projectType.name))
    .addAllVariantNames(variantNames)
    .setAaptOptions(aaptOptions.toProto())
    .setJavaCompileOptions(javaCompileOptions.toProto())
    .setBuildFolder(converter.fileToProto(buildFolder))
    .setBaseSplit(isBaseSplit)
    .addAllDynamicFeatures(dynamicFeatures)
    .setModelVersion(modelVersion)
    .setName(name)
    .setCompileTarget(compileTarget)
    .addAllBootClasspath(bootClasspath.map { converter.fileToProto(it, PathConverter.DirType.SDK) })
    .build()!!
}
