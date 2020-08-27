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
package com.android.tools.idea.gradle.project.sync.idea.svs

import com.android.builder.model.AndroidProject
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.ProjectSyncIssues
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.ide.common.gradle.model.impl.ModelCache.Companion.safeGet
import com.android.ide.common.repository.GradleVersion
import com.android.ide.gradle.model.artifacts.AdditionalClassifierArtifactsModel
import com.android.tools.idea.gradle.project.sync.Modules.createUniqueModuleId
import com.android.tools.idea.gradle.project.sync.idea.UsedInBuildAction
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider

/**
 * The container class for Android module, containing its Android model, Variant models, and dependency modules.
 */
@UsedInBuildAction
class AndroidModule(
  private val gradleProject: BasicGradleProject,
  val androidProject: AndroidProject,
  /** Old V1 model. It's only set if [nativeModule] is not set. */
  val nativeAndroidProject: NativeAndroidProject?,
  /** New V2 model. It's only set if [nativeAndroidProject] is not set. */
  val nativeModule: NativeModule?
) {
  val findModelRoot: Model get() = gradleProject
  val modelVersion: GradleVersion? = runCatching { GradleVersion.tryParse(androidProject.modelVersion) }.getOrNull()
  val projectType: Int get() = androidProject.projectType

  /** All configured variant names if supported by the AGP version. */
  val allVariantNames: Collection<String>? = safeGet(androidProject::getVariantNames, null)?.toSet()

  /** Names of all currently fetch variants (currently pre single-variant-sync only). */
  val fetchedVariantNames: Collection<String> = safeGet({ androidProject.variants.map { it.name }.toSet() }, emptySet())

  val defaultVariantName: String?
    get() = safeGet(androidProject::getDefaultVariant, null)
            ?: allVariantNames?.getDefaultOrFirstItem("debug")

  val id = createUniqueModuleId(gradleProject)
  val variantGroup: VariantGroup = VariantGroup()
  val hasNative: Boolean = nativeAndroidProject != null || nativeModule != null

  var projectSyncIssues: ProjectSyncIssues? = null
  var additionalClassifierArtifacts: AdditionalClassifierArtifactsModel? = null

  private inner class ModelConsumer(val buildModelConsumer: ProjectImportModelProvider.BuildModelConsumer) {
    inline fun <reified T : Any> T.deliver() {
      println("Consuming ${T::class.simpleName} for ${gradleProject.path}")
      buildModelConsumer.consumeProjectModel(gradleProject, this, T::class.java)
    }
  }

  fun deliverModels(consumer: ProjectImportModelProvider.BuildModelConsumer) {
    with(ModelConsumer(consumer)) {
      androidProject.deliver()
      nativeModule?.deliver()
      nativeAndroidProject?.deliver()
      variantGroup.takeUnless { it.variants.isEmpty() }?.deliver()
      projectSyncIssues?.deliver()
      additionalClassifierArtifacts?.deliver()
    }
  }
}

data class ModuleConfiguration(val id: String, val variant: String, val abi: String?)

@UsedInBuildAction
fun Collection<String>.getDefaultOrFirstItem(defaultValue: String): String? =
  if (contains(defaultValue)) defaultValue else minBy { it }
