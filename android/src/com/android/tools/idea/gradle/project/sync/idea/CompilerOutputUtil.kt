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
package com.android.tools.idea.gradle.project.sync.idea

import com.android.builder.model.AndroidProject
import com.android.builder.model.Variant
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil

/**
 * Sets the compiler output paths on the module [DataNode].
 */
@JvmOverloads
fun DataNode<ModuleData>.setupCompilerOutputPaths(variant: Variant? = null) {
  val androidModel = ExternalSystemApiUtil.find(this, AndroidProjectKeys.ANDROID_MODEL)?.data ?: return
  val selectedVariant = variant ?: androidModel.selectedVariant

  data.isInheritProjectCompileOutputPath = false

  val sourceCompilerOutput = selectedVariant.mainArtifact.classesFolder.absolutePath
  val testCompilerOutput = selectedVariant.extraJavaArtifacts.firstOrNull { artifact ->
    artifact.name == AndroidProject.ARTIFACT_UNIT_TEST
  }?.classesFolder?.absolutePath

  data.setExternalCompilerOutputPath(ExternalSystemSourceType.SOURCE, sourceCompilerOutput)
  data.setExternalCompilerOutputPath(ExternalSystemSourceType.TEST, testCompilerOutput)
}