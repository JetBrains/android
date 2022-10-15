/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.ide.gradle.model.artifacts.AdditionalClassifierArtifactsModel
import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.IdeUnresolvedLibraryTableImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantCoreImpl
import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeAndroidProject
import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeVariantAbi
import com.android.tools.idea.gradle.model.ndk.v2.IdeNativeModule
import org.gradle.tooling.model.BuildModel
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.model.kapt.KaptGradleModel
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import java.io.Serializable

sealed interface GradleModelCollection {
  fun deliverModels(consumer: ProjectImportModelProvider.BuildModelConsumer)
}

class GradleProject(
  private val buildModel: BuildModel,
  private val ideLibraryTable: IdeUnresolvedLibraryTableImpl
) : GradleModelCollection {

  override fun deliverModels(consumer: ProjectImportModelProvider.BuildModelConsumer) {
    with(ModelConsumer(consumer)) {
      ideLibraryTable.deliver()
    }
  }

  private inner class ModelConsumer(val buildModelConsumer: ProjectImportModelProvider.BuildModelConsumer) {
    inline fun <reified T : Any> T.deliver() {
      buildModelConsumer.consume(buildModel, this, T::class.java)
    }
  }
}

/**
 * The final collection of models representing [gradleProject] prepared for consumption by the IDE.
 */
sealed class DeliverableGradleModule(
  val gradleProject: BasicGradleProject,
  val projectSyncIssues: List<IdeSyncIssue>
) : GradleModelCollection {

  final override fun deliverModels(consumer: ProjectImportModelProvider.BuildModelConsumer) {
    with(ModelConsumer(consumer)) {
      deliverModels()
    }
  }

  protected abstract fun ModelConsumer.deliverModels()

  protected inner class ModelConsumer(val buildModelConsumer: ProjectImportModelProvider.BuildModelConsumer) {
    inline fun <reified T : Any> T.deliver() {
      buildModelConsumer.consumeProjectModel(gradleProject, this, T::class.java)
    }
  }
}

class DeliverableAndroidModule(
  gradleProject: BasicGradleProject,
  projectSyncIssues: List<IdeSyncIssue>,
  val selectedVariantName: String,
  val selectedAbiName: String?,
  val androidProject: IdeAndroidProjectImpl,
  val fetchedVariants: List<IdeVariantCoreImpl>,
  val nativeModule: IdeNativeModule?,
  val nativeAndroidProject: IdeNativeAndroidProject?,
  val syncedNativeVariant: IdeNativeVariantAbi?,
  val kotlinGradleModel: KotlinGradleModel?,
  val kaptGradleModel: KaptGradleModel?,
  val additionalClassifierArtifacts: AdditionalClassifierArtifactsModel?
) : DeliverableGradleModule(gradleProject, projectSyncIssues) {
  override fun ModelConsumer.deliverModels() {

    val ideAndroidModels = IdeAndroidModels(
      androidProject,
      fetchedVariants,
      selectedVariantName,
      selectedAbiName,
      projectSyncIssues,
      nativeModule,
      nativeAndroidProject,
      syncedNativeVariant,
      kaptGradleModel
    )
    ideAndroidModels.deliver()
    kotlinGradleModel?.deliver()
    additionalClassifierArtifacts?.deliver()
  }
}

class DeliverableJavaModule(
  gradleProject: BasicGradleProject,
  projectSyncIssues: List<IdeSyncIssue>,
  private val kotlinGradleModel: KotlinGradleModel?,
  private val kaptGradleModel: KaptGradleModel?
) : DeliverableGradleModule(gradleProject, projectSyncIssues) {
  override fun ModelConsumer.deliverModels() {
    kotlinGradleModel?.deliver()
    kaptGradleModel?.deliver()
  }
}

class DeliverableNativeVariantsAndroidModule(
  gradleProject: BasicGradleProject,
  projectSyncIssues: List<IdeSyncIssue>,
  private val nativeVariants: List<IdeNativeVariantAbi>? // Null means V2.
) : DeliverableGradleModule(gradleProject, projectSyncIssues) {
  override fun ModelConsumer.deliverModels() {
    IdeAndroidNativeVariantsModels(nativeVariants, projectSyncIssues).deliver()
  }
}

class StandaloneDeliverableModel<T : Serializable>(
  private val clazz: Class<*>,
  private val model: T,
  private val modelFor: BuildModel
  ) : GradleModelCollection {

  override fun deliverModels(consumer: ProjectImportModelProvider.BuildModelConsumer) {
    consumer.consume(modelFor, model, clazz)
  }

  companion object {
    inline fun <reified T: Serializable> createModel(model: T, modelFor: BuildModel): StandaloneDeliverableModel<T> {
      return StandaloneDeliverableModel(T::class.java, model, modelFor)
    }
  }
}
