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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.androidproject

import com.android.builder.model.SyncIssue
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.androidproject.AaptOptions
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.androidproject.AndroidProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.androidproject.JavaCompileOptions
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.androidproject.SigningConfig
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.OldAndroidProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.PathConverter
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.AndroidProjectProto
import java.io.File

data class NewAndroidProject(
  override val modelVersion: String,
  override val name: String,
  override val projectType: AndroidProject.ProjectType,
  override val variantNames: Collection<String>,
  override val compileTarget: String,
  override val bootClasspath: Collection<File>,
  override val aaptOptions: AaptOptions,
  override val syncIssues: Collection<SyncIssue>,
  override val javaCompileOptions: JavaCompileOptions,
  override val buildFolder: File,
  override val isBaseSplit: Boolean,
  override val dynamicFeatures: Collection<String>,
  override val rootFolder: File,
  override val signingConfigs: Collection<SigningConfig>
) : AndroidProject {
  constructor(oldAndroidProject: OldAndroidProject, rootFolder: File) : this(
    oldAndroidProject.modelVersion,
    oldAndroidProject.name,
    AndroidProject.ProjectType.fromValue(oldAndroidProject.projectType),
    oldAndroidProject.variantNames,
    oldAndroidProject.compileTarget,
    oldAndroidProject.bootClasspath.map { File(it) },
    NewAaptOptions(oldAndroidProject.aaptOptions),
    oldAndroidProject.syncIssues,
    NewJavaCompileOptions(oldAndroidProject.javaCompileOptions),
    oldAndroidProject.buildFolder,
    oldAndroidProject.isBaseSplit,
    oldAndroidProject.dynamicFeatures,
    rootFolder,
    oldAndroidProject.signingConfigs.map { NewSigningConfig(it) }
  )

  constructor(proto: AndroidProjectProto.AndroidProject, rootFolder: File, converter: PathConverter) : this(
    proto.modelVersion,
    proto.name,
    AndroidProject.ProjectType.valueOf(proto.projectType.name),
    proto.variantNamesList,
    proto.compileTarget,
    proto.bootClasspathList.map { converter.fileFromProto(it) },
    NewAaptOptions(proto.aaptOptions),
    listOf<SyncIssue>(),
    NewJavaCompileOptions(proto.javaCompileOptions),
    converter.fileFromProto(proto.buildFolder),
    proto.baseSplit,
    proto.dynamicFeaturesList,
    rootFolder,
    listOf() // empty because it's not cached
  )
}
