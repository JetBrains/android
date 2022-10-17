/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.NativeVariantAbi
import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.BasicAndroidProject
import com.android.builder.model.v2.models.ProjectSyncIssues
import com.android.builder.model.v2.models.VariantDependencies
import com.android.builder.model.v2.models.Versions
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.ide.gradle.model.GradlePluginModel
import com.android.ide.gradle.model.LegacyApplicationIdModel
import com.android.ide.gradle.model.LegacyV1AgpVersionModel
import com.android.ide.gradle.model.artifacts.AdditionalClassifierArtifactsModel
import com.android.tools.idea.projectsystem.gradle.sync.Counter
import org.gradle.api.Action
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.gradle.GradleBuild
import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.model.kapt.KaptGradleModel
import com.android.builder.model.AndroidProject as AndroidProjectV1
import com.android.builder.model.ProjectSyncIssues as ProjectSyncIssuesV1
import com.android.builder.model.Variant as VariantV1

internal interface GradleInjectedSyncActionRunner {
  fun <T> runActions(actionsToRun: List<ActionToRun<T>>): List<T>
  fun <T> runAction(action: (BuildController) -> T): T
}

/**
 * A [BuildAction]-like structure holding an action to run together with metadata describing what kind of models it may fetch.
 */
data class ActionToRun<T>(
  private val action: (BuildController) -> T,
  val fetchesV1Models: Boolean = false,
  val fetchesV2Models: Boolean = false,
  val fetchesPlatformModels: Boolean = false,
  val fetchesKotlinModels: Boolean = false
) {

  /**
   * Transforms the result of this action by applying [transform].
   */
  fun <V> map(transform: (T) -> V): ActionToRun<V> {
    return ActionToRun(
      action = { controller -> transform(this.action(controller)) },
      fetchesV1Models = fetchesV1Models,
      fetchesV2Models = fetchesV2Models,
      fetchesPlatformModels = fetchesPlatformModels,
      fetchesKotlinModels = fetchesKotlinModels
    )
  }

  /**
   * Runs the action using the given [controller] and ensures that only models declared in the metadata are requested by the action.
   */
  internal fun run(controller: BuildController): T {
    return action(controller.toSafeController())
  }

  /**
   * Make sure that the model requested by the action is known and declared by the action.
   *
   * Each model that can be requested within [AndroidExtraModelProviderWorker] has to be listed here and if it is known to have any
   * parallel sync compatibility issues it needs to be mapped to one of the issue classes (a new class may need to be added).
   */
  private fun validateModelType(modelType: Class<*>) {
    val isDeclared = when (modelType) {
      Versions::class.java -> fetchesV2Models
      BasicAndroidProject::class.java -> fetchesV2Models
      AndroidProject::class.java -> fetchesV2Models
      VariantDependencies::class.java -> fetchesV2Models
      AndroidDsl::class.java -> fetchesV2Models
      ProjectSyncIssues::class.java -> fetchesV2Models
      AndroidProjectV1::class.java -> fetchesV1Models
      VariantV1::class.java -> fetchesV1Models
      ProjectSyncIssuesV1::class.java -> fetchesV1Models
      KotlinGradleModel::class.java -> fetchesKotlinModels
      KaptGradleModel::class.java -> fetchesKotlinModels
      KotlinMPPGradleModel::class.java -> fetchesKotlinModels
      LegacyApplicationIdModel::class.java -> fetchesV1Models || fetchesV2Models
      NativeModule::class.java -> fetchesV1Models || fetchesV2Models  // We trust actions request it with Gradle models.
      NativeAndroidProject::class.java -> fetchesV1Models
      NativeVariantAbi::class.java -> fetchesV1Models
      AdditionalClassifierArtifactsModel::class.java -> true  // No known incompatibilities.
      LegacyV1AgpVersionModel::class.java -> true
      GradlePluginModel::class.java -> true
      else -> error("Unexpected model type: $modelType. ActionToRun.validateModelType needs to be updated.")
    }
    if (!isDeclared) {
      error("Undeclared model $modelType requested by ActionToRun")
    }
  }

  /**
   * Wraps this [BuildController] and throws an error if a requested model is not declared by the action. This is to make sure that
   * model types declared in code are valid.
   */
  private fun BuildController.toSafeController(): BuildController {
    val delegate = this
    return object : BuildController {
      override fun <T> getModel(modelType: Class<T>): T {
        validateModelType(modelType)
        return delegate.getModel(modelType)
      }

      override fun <T> getModel(target: Model?, modelType: Class<T>): T {
        validateModelType(modelType)
        return delegate.getModel(target, modelType)
      }

      override fun <T, P> getModel(modelType: Class<T>, parameterType: Class<P>, parameterInitializer: Action<in P>): T {
        validateModelType(modelType)
        return delegate.getModel( modelType, parameterType, parameterInitializer)
      }

      override fun <T, P> getModel(target: Model?, modelType: Class<T>, parameterType: Class<P>, parameterInitializer: Action<in P>): T {
        validateModelType(modelType)
        return delegate.getModel(target, modelType, parameterType, parameterInitializer)
      }

      override fun <T> findModel(modelType: Class<T>): T? {
        validateModelType(modelType)
        return delegate.findModel(modelType)
      }

      override fun <T> findModel(target: Model?, modelType: Class<T>): T? {
        validateModelType(modelType)
        return delegate.findModel(target, modelType)
      }

      override fun <T, P> findModel(modelType: Class<T>, parameterType: Class<P>, parameterInitializer: Action<in P>): T? {
        validateModelType(modelType)
        return delegate.findModel( modelType, parameterType, parameterInitializer)
      }

      override fun <T, P> findModel(target: Model?, modelType: Class<T>, parameterType: Class<P>, parameterInitializer: Action<in P>): T? {
        validateModelType(modelType)
        return delegate.findModel(target, modelType, parameterType, parameterInitializer)
      }

      override fun getBuildModel(): GradleBuild = error("Not intended to be used")

      @Suppress("UnstableApiUsage")
      override fun <T : Any?> run(p0: Collection<out BuildAction<out T>>?): List<T> = error("Not intended to be used")

      @Suppress("UnstableApiUsage")
      override fun getCanQueryProjectModelInParallel(p0: Class<*>?): Boolean = error("Not intended to be used")
    }
  }
}

/**
 * The class that controls parallel fetching of build models in Android Gradle sync.
 *
 * Each [ActionToRun] used in Gradle sync to get build models declares which kinds of models it processes.  [SyncActionRunner] knows the
 * capabilities of the Gradle version and of the Android Gradle plugin in use.
 *
 * [runActions] method separates models that can be fetched in parallel and those that cannot and runs them in the appropriate mode.
 *
 * Also [SyncActionRunner] measures time it took to fetch models using [syncCounters].
 */
class SyncActionRunner private constructor(
  private val controller: BuildController,
  private val syncCounters: SyncCounters,
  private val parallelActionsSupported: Boolean,
  private val canFetchV2ModelsInParallel: Boolean = false,
  private val canFetchPlatformModelsInParallel: Boolean = false,
  private val canFetchKotlinModelsInParallel: Boolean = false,
) : GradleInjectedSyncActionRunner {

  companion object {
    fun create(
      controller: BuildController,
      syncCounters: SyncCounters,
      parallelActionsSupported: Boolean
    ) = SyncActionRunner(controller, syncCounters, parallelActionsSupported)
  }

  fun enableParallelFetchForV2Models(enable: Boolean = true): SyncActionRunner =
    SyncActionRunner(
      controller = controller,
      syncCounters = syncCounters,
      parallelActionsSupported = parallelActionsSupported,
      canFetchV2ModelsInParallel = enable,
      canFetchPlatformModelsInParallel = canFetchPlatformModelsInParallel,
      canFetchKotlinModelsInParallel = canFetchKotlinModelsInParallel
    )

  val parallelActionsForV2ModelsSupported: Boolean get() = parallelActionsSupported && canFetchV2ModelsInParallel

  private val ActionToRun<*>.canRunInParallel
    get() =
      parallelActionsSupported &&
      (!fetchesV2Models || canFetchV2ModelsInParallel) &&
      (!fetchesPlatformModels || canFetchPlatformModelsInParallel) &&
      (!fetchesKotlinModels || canFetchKotlinModelsInParallel)

  override fun <T> runActions(actionsToRun: List<ActionToRun<T>>): List<T> {
    return when (actionsToRun.size) {
      0 -> emptyList()
      1 -> listOf(runAction { actionsToRun[0].run(it.toMeasuringController(syncCounters)) })
      else ->
        if (parallelActionsSupported) {
          val indexedActions = actionsToRun.mapIndexed { index, action -> index to action }.toMap()
          val parallelActions = indexedActions.filter { it.value.canRunInParallel }
          val sequentialAction = indexedActions.filter { !it.value.canRunInParallel }
          val executionResults =
            parallelActions.keys.zip(
              @Suppress("UnstableApiUsage")
              controller.run(parallelActions.map { indexedActionToRun ->
                BuildAction {
                  indexedActionToRun.value.run(it.toMeasuringController(syncCounters))
                }
              }) as List<T>
            ).toMap() +
              sequentialAction.map {
                it.key to runAction { controller -> it.value.run(controller.toMeasuringController(syncCounters)) }
              }.toMap()

          executionResults.toSortedMap().values.toList()
        } else {
          actionsToRun.map { runAction { controller -> it.run(controller.toMeasuringController(syncCounters)) } }
        }
    }
  }

  override fun <T> runAction(action: (BuildController) -> T): T {
    return action(controller)
  }
}

/**
 * Wraps this [BuildController] and records time it takes to build each model.
 */
private fun BuildController.toMeasuringController(syncCounters: SyncCounters): BuildController {
  val delegate = this
  return object : BuildController {
    override fun <T> getModel(modelType: Class<T>): T {
      return syncCounters.measure(modelType) { delegate.getModel(modelType) }
    }

    override fun <T> getModel(target: Model?, modelType: Class<T>): T {
      return syncCounters.measure(modelType) { delegate.getModel(target, modelType) }
    }

    override fun <T, P> getModel(modelType: Class<T>, parameterType: Class<P>, parameterInitializer: Action<in P>): T {
      return syncCounters.measure(modelType) { delegate.getModel(modelType, parameterType, parameterInitializer) }
    }

    override fun <T, P> getModel(target: Model?, modelType: Class<T>, parameterType: Class<P>, parameterInitializer: Action<in P>): T {
      return syncCounters.measure(modelType) { delegate.getModel(target, modelType, parameterType, parameterInitializer) }
    }

    override fun <T> findModel(modelType: Class<T>): T? {
      return syncCounters.measure(modelType) { delegate.findModel(modelType) }
    }

    override fun <T> findModel(target: Model?, modelType: Class<T>): T? {
      return syncCounters.measure(modelType) { delegate.findModel(target, modelType) }
    }

    override fun <T, P> findModel(modelType: Class<T>, parameterType: Class<P>, parameterInitializer: Action<in P>): T? {
      return syncCounters.measure(modelType) { delegate.findModel(modelType, parameterType, parameterInitializer) }
    }

    override fun <T, P> findModel(target: Model?, modelType: Class<T>, parameterType: Class<P>, parameterInitializer: Action<in P>): T? {
      return syncCounters.measure(modelType) { delegate.findModel(target, modelType, parameterType, parameterInitializer) }
    }

    override fun getBuildModel(): GradleBuild = error("Not intended to be used")

    @Suppress("UnstableApiUsage")
    override fun <T : Any?> run(p0: Collection<out BuildAction<out T>>?): List<T> = error("Not intended to be used")

    @Suppress("UnstableApiUsage")
    override fun getCanQueryProjectModelInParallel(p0: Class<*>?): Boolean = error("Not intended to be used")
  }
}

/**
 * Maps a [modelType] to a [Counter] to be used to measure the time it takes to build models of this type.
 */
private fun <T> SyncCounters.measure(modelType: Class<*>, block: () -> T): T {
  val counter = when (modelType) {
    Versions::class.java -> otherModel
    BasicAndroidProject::class.java -> projectModel
    AndroidProject::class.java -> projectModel
    VariantDependencies::class.java -> variantDependenciesModel
    AndroidDsl::class.java -> projectModel
    ProjectSyncIssues::class.java -> otherModel
    AndroidProjectV1::class.java -> projectModel
    VariantV1::class.java -> variantDependenciesModel
    ProjectSyncIssuesV1::class.java -> otherModel
    KotlinGradleModel::class.java -> kotlinModel
    KaptGradleModel::class.java -> kaptModel
    KotlinMPPGradleModel::class.java -> mppModel
    LegacyApplicationIdModel::class.java -> otherModel
    NativeModule::class.java -> nativeModel
    NativeAndroidProject::class.java -> nativeModel
    NativeVariantAbi::class.java -> nativeModel
    AdditionalClassifierArtifactsModel::class.java -> additionalArtifactsModel
    LegacyV1AgpVersionModel::class.java -> otherModel
    GradlePluginModel::class.java -> otherModel
    else -> error("Unexpected model type: $modelType. ActionToRun.SyncCounters.measure needs to be updated.")
  }
  return counter(block)
}
