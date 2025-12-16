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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl
import com.android.utils.appendCapitalized
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradleModel
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService

class PhasedSyncAdditionalModelProvider(val cachedModels: ModelProviderCachedData) : ProjectImportModelProvider {
  override fun getPhase() = GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE

  override fun populateModels(controller: BuildController,
                              buildModels: Collection<GradleBuild>,
                              modelConsumer: ProjectImportModelProvider.GradleModelConsumer) {
    if (cachedModels.shouldRunLegacyModelProviders) {
      return
    }
    val actions = buildModels.flatMap { buildModel ->
      buildModel.projects
        .mapNotNull { gradleProject ->
          BuildAction { controller ->
            gradleProject to controller.fetchModel<KotlinGradleModel>(gradleProject, cachedModels.data[gradleProject]?.selectedVariantName)
          }
        }
    }

    controller.run(actions).filterNotNull().forEach { (gradleProject, kotlinModel) ->
      kotlinModel?.let {
        modelConsumer.consumeProjectModel(gradleProject, it, KotlinGradleModel::class.java)
      }
      createIdeAndroidModels(cachedModels, gradleProject)?.let {
        modelConsumer.consumeProjectModel(gradleProject, it, IdeAndroidModels::class.java)
      }
    }
    cachedModels.clear()
  }
}

private fun createIdeAndroidModels(
  cachedModels: ModelProviderCachedData,
  gradleProject: BasicGradleProject
) = cachedModels.data[gradleProject]?.let { data ->
  IdeAndroidModels(
    data.ideAndroidProject as IdeAndroidProjectImpl,
    listOf(cachedModels.selectedVariant[gradleProject]!!),
    data.selectedVariantName,
    selectedAbiName = null,
    v2NativeModule = null,
    kaptGradleModel = null
  )
}