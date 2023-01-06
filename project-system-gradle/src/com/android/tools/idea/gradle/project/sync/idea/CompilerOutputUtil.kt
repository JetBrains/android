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

import com.android.tools.idea.gradle.model.IdeModuleWellKnownSourceSet
import com.android.tools.idea.gradle.model.IdeVariant
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData

/**
 * Sets the compiler output paths on the module [DataNode].
 */
// TODO(b/213887150) : once this bug is fixed and we have code coverage exclusively with Jacoco, then this can be deleted.
@JvmOverloads
fun DataNode<ModuleData>.setupCompilerOutputPaths(variant: IdeVariant? = null, isDelegatedBuildUsed: Boolean) {
  val androidModel = ExternalSystemApiUtil.find(this, AndroidProjectKeys.ANDROID_MODEL)?.data ?: return
  val selectedVariant = variant ?: androidModel.selectedVariantCore

  data.useExternalCompilerOutput(isDelegatedBuildUsed)
  data.isInheritProjectCompileOutputPath = false
  // MPSS: Set compilation data for Gradle sourceSets too.
  for (sourceSet in ExternalSystemApiUtil.findAll(this, GradleSourceSetData.KEY)) {
    val sourceSetData = sourceSet.data
    val knownSourceSet = IdeModuleWellKnownSourceSet.fromName(sourceSetData.moduleName)
    if (knownSourceSet == null) {
      // Ignore any non-Android source sets e.g in a KMP project
      continue
    }
    val artifact = when(knownSourceSet) {
      IdeModuleWellKnownSourceSet.MAIN -> selectedVariant.mainArtifact
      IdeModuleWellKnownSourceSet.TEST_FIXTURES -> selectedVariant.testFixturesArtifact
      IdeModuleWellKnownSourceSet.UNIT_TEST -> selectedVariant.unitTestArtifact
      IdeModuleWellKnownSourceSet.ANDROID_TEST -> selectedVariant.androidTestArtifact
    }
    val isTestScope = when(knownSourceSet) {
      IdeModuleWellKnownSourceSet.MAIN -> false
      IdeModuleWellKnownSourceSet.TEST_FIXTURES -> true
      IdeModuleWellKnownSourceSet.UNIT_TEST -> true
      IdeModuleWellKnownSourceSet.ANDROID_TEST -> true
    }

    // TODO(b/232780259): Look for the compilation output folder. We can have both java and kotlin compilation outputs in classesFolder(IDEA-235250).

    val sourceCompilerOutput = if (!isTestScope) artifact?.classesFolder?.firstOrNull()?.absolutePath else null
    val testCompilerOutput = if (isTestScope) artifact?.classesFolder?.firstOrNull()?.absolutePath else null

    // The compiler output paths are not inherited here as every moduleData can have its own path.
    // In order for CompilerModuleExtension to use the compiler paths of each module, we need to make sure that
    // isInheritProjectCompileOutputPath is set to false.
    sourceSetData.isInheritProjectCompileOutputPath = false
    sourceSetData.useExternalCompilerOutput(isDelegatedBuildUsed)
    sourceSetData.setCompileOutputPath(ExternalSystemSourceType.SOURCE, null)
    sourceSetData.setCompileOutputPath(ExternalSystemSourceType.TEST, null)
    sourceSetData.setExternalCompilerOutputPath(ExternalSystemSourceType.SOURCE, sourceCompilerOutput)
    sourceSetData.setExternalCompilerOutputPath(ExternalSystemSourceType.TEST, testCompilerOutput)
  }
}


