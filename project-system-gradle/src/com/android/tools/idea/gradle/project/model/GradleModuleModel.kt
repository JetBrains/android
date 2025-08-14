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
package com.android.tools.idea.gradle.project.model

import com.android.SdkConstants
import com.android.tools.idea.gradle.model.impl.FileImpl
import com.android.tools.idea.gradle.model.impl.toImpl
import com.android.tools.idea.gradle.project.entities.gradleModuleModel
import com.android.tools.idea.projectsystem.gradle.getHolderModule
import com.android.tools.idea.projectsystem.gradle.isLinkedAndroidModule
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.workspaceModel.ide.legacyBridge.findSnapshotModuleEntity
import java.io.File
import org.gradle.tooling.model.GradleProject

val Module.gradleModuleModel: GradleModuleModel? get() =
  if (this.isLinkedAndroidModule()) {
    this.getHolderModule() // We only set this model on the holder so we can redirect
  } else {
    this // For non-android modules, the model is only set on the holder, but we have no mechanism of finding it
  }.findSnapshotModuleEntity()
    ?.gradleModuleModel
    ?.gradleModuleModel

data class GradleModuleModel(
  private val moduleNameField: String,
  val taskNames: List<String>,
  val gradlePath: String,
  val rootFolderPath: FileImpl,
  val buildFilePath: FileImpl?,
  val gradleVersion: String?,
  val agpVersion: String?,
  val safeArgsJava: Boolean,
  val safeArgsKotlin: Boolean,
): ModuleModel {
  constructor(
    moduleName: String,
    gradleProject: GradleProject,
    buildFilePath: File?,
    gradleVersion: String?,
    agpVersion: String?,
    safeArgsJava: Boolean,
    safeArgsKotlin: Boolean
  ): this(moduleName, gradleProject.getTaskNames(), gradleProject.getPath(),
          gradleProject.projectIdentifier.buildIdentifier.rootDir.toImpl(),
          buildFilePath?.toImpl(), gradleVersion, agpVersion, safeArgsJava,
          safeArgsKotlin)

  fun buildFileAsVirtualFile() = buildFilePath?.let { VfsUtil.findFileByIoFile(it, true) }
  override fun getModuleName() = moduleNameField
}

private fun GradleProject.getTaskNames() =
  tasks.filter {
    it.name.isNotEmpty()
  }.map {
    it.project.path + SdkConstants.GRADLE_PATH_SEPARATOR + it.name
  }

