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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.loaders

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.androidproject.AndroidProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.Variant
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade.LegacyAndroidProjectStub
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.*
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.androidproject.NewAndroidProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.gradleproject.NewGradleProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.variant.NewVariant
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.AndroidProjectProto
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.GradleProjectProto
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.VariantProto
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.repackage.com.google.protobuf.util.JsonFormat
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

fun bindExtraInfoToNewProjectJsonLoader(extraInfo: NewProjectExtraInfo): LoaderConstructor = {
  path: Path, converter: PathConverter -> NewProjectJsonLoader(path, converter, extraInfo)
}

class NewProjectJsonLoader(
  path: Path,
  converter: PathConverter,
  private val extraInfo: NewProjectExtraInfo
): SimpleJsonLoader(path, converter) {
  override fun loadAndroidProject(variant: String): OldAndroidProject {
    fun loadNewAndroidProjectFromJSON(fullPath: Path, extraInfo: NewProjectExtraInfo): AndroidProject {
      val androidProjectJSON = String(Files.readAllBytes(fullPath))

      val proto = AndroidProjectProto.AndroidProject.newBuilder().apply {
        JsonFormat.parser().ignoringUnknownFields().merge(androidProjectJSON, this)
        // TODO(qumeric): support wear etc.
        name = extraInfo.mobileProjectName
      }.build()

      val rootDir = File(extraInfo.projectLocation)
      return NewAndroidProject(proto, rootDir, converter)
    }

    fun loadNewVariantFromJSON(fullPath: Path, extraInfo: NewProjectExtraInfo): Variant {
      val variantJSON = String(Files.readAllBytes(fullPath))

      val proto = VariantProto.Variant.newBuilder().apply {
        JsonFormat.parser().ignoringUnknownFields().merge(variantJSON, this)
        variantConfigBuilder.applicationId = extraInfo.packageName
        mainArtifactBuilder.applicationId = extraInfo.packageName
        if (hasAndroidTestArtifact()) {
          androidTestArtifactBuilder.applicationId = extraInfo.packageName
        }
      }.build()

      return NewVariant(proto, converter)
    }

    val fullAndroidProjectPath = path.resolve(ANDROID_PROJECT_CACHE_PATH)
    val newAndroidProject = loadNewAndroidProjectFromJSON(fullAndroidProjectPath, extraInfo)

    val fullVariantPath = path.resolve(VARIANTS_CACHE_DIR_PATH).resolve("$variant.json")
    val newVariant = loadNewVariantFromJSON(fullVariantPath, extraInfo)

    return LegacyAndroidProjectStub(newAndroidProject, newVariant)
  }

  override fun loadGradleProject(): NewGradleProject {
    val fullPath = path.resolve(GRADLE_PROJECT_CACHE_PATH)

    val gradleProjectJSON = String(Files.readAllBytes(fullPath))
    val proto = GradleProjectProto.GradleProject.newBuilder().apply {
      JsonFormat.parser().ignoringUnknownFields().merge(gradleProjectJSON, this)
    }.build()

    // TODO(qumeric): support wear etc.
    val newProjectPath = if (proto.projectPath == ":")
      ":"
    else
      ":" + extraInfo.mobileProjectName
    val newName = if (proto.projectPath == ":") extraInfo.projectName else extraInfo.mobileProjectName

    // NewGradleProject implements the old interface because it's not going to be changed
    return NewGradleProject(proto, converter, proto.projectPath, newProjectPath, newName)
  }
}
