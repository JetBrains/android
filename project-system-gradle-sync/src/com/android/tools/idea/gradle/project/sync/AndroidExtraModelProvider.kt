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

import com.android.ide.gradle.model.GradlePluginModel
import com.android.ide.gradle.model.composites.BuildMap
import com.android.tools.idea.gradle.model.IdeCompositeBuildMap
import com.android.tools.idea.gradle.model.IdeDebugInfo
import com.android.tools.idea.gradle.model.impl.IdeBuildImpl
import com.android.tools.idea.gradle.model.impl.IdeCompositeBuildMapImpl
import com.android.tools.idea.gradle.model.impl.IdeDebugInfoImpl
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.GradleModelConsumer
import java.io.File
import java.net.URLClassLoader
import java.time.Instant

/**
 * An entry point for Android Gradle sync.
 *
 * This is a serializable class instantiated by `AndroidGradleProjectResolver` in the IDE process, which is later deserialized in the Gradle
 * process and its [populateBuildModels] and [populateProjectModels] are invoked by the framework in order to fetch any required models.
 *
 * To avoid interference with Java serialization this class redirects calls to its methods to a non-serializable
 * [AndroidExtraModelProviderImpl], which is instantiated on demand and the reference is stored in a Java-serialization-transitive property.
 */
class AndroidExtraModelProvider(private val syncOptions: SyncActionOptions) : ProjectImportModelProvider {

  @Transient
  private var _impl: AndroidExtraModelProviderImpl? = null

  private val impl: AndroidExtraModelProviderImpl get() = _impl ?: AndroidExtraModelProviderImpl(syncOptions).also { _impl = it }

  override fun populateBuildModels(
    controller: BuildController,
    buildModel: GradleBuild,
    modelConsumer: GradleModelConsumer
  ) = impl.populateBuildModels(controller, buildModel, modelConsumer)

  override fun populateProjectModels(
    controller: BuildController,
    projectModel: BasicGradleProject,
    modelConsumer: GradleModelConsumer
  ) = impl.populateProjectModels(controller, projectModel, modelConsumer)
}

private class BuildModelsAndMap(val models: Set<GradleBuild>, val map: IdeCompositeBuildMapImpl)

/**
 * The actual implementation of [AndroidExtraModelProvider], which does not require its properties to be [Transient] and allows
 * initializers (without having these values serialized).
 */
private class AndroidExtraModelProviderImpl(private val syncOptions: SyncActionOptions) {

  private var buildModelsAndMap: BuildModelsAndMap? = null
  private val seenBuildModels: MutableSet<GradleBuild> = mutableSetOf()

  private val syncCounters = SyncCounters()

  fun populateBuildModels(
    controller: BuildController,
    buildModel: GradleBuild,
    consumer: GradleModelConsumer
  ) {
    recordCheckpointData(MeasurementCheckpoint.ANDROID_STARTED)
    // Flatten the platform's handling of included builds. We need all models together to resolve cross `includeBuild` dependencies
    // correctly. This, unfortunately, makes assumptions about the order in which these methods are invoked. If broken it will be caught
    // by any test attempting to sync a composite build.
    val buildModelsAndMap = this.buildModelsAndMap ?: syncCounters.buildInfoPhase {
      val buildModelsAndMap = buildModelsAndMap(buildModel, controller)
      consumer.consumeBuildModel(buildModel, buildModelsAndMap.map, IdeCompositeBuildMap::class.java)
      this.buildModelsAndMap = buildModelsAndMap
      buildModelsAndMap
    }

    if (!seenBuildModels.add(buildModel)) {
      error("Included build ${buildModel.buildIdentifier.rootDir} appears for the second time")
    }
    if (!buildModelsAndMap.models.contains(buildModel)) {
      error("Unknown included build ${buildModel.buildIdentifier.rootDir} encountered")
    }
    if (buildModelsAndMap.models.size == seenBuildModels.size) {
      AndroidExtraModelProviderWorker(
        controller,
        syncCounters,
        syncOptions,
        BuildInfo(
          buildModelsAndMap.models.toList(),
          // Consumers for different build models are all equal except they aggregate statistics to different targets. We cannot request all
          // models we need until we have enough information to do it. In the case of a composite builds all model fetching time will be
          // reported against the last included build.
          buildModelsAndMap.map,
          ModelConverter.populateModuleBuildDirs(controller),
        ),
        consumer
      ).populateBuildModels()
      recordCheckpointData(MeasurementCheckpoint.ANDROID_FINISHED)
      if (syncOptions.flags.studioDebugMode) {
        populateDebugInfo(buildModel, consumer)
      }
   }
  }

  private fun recordCheckpointData(checkpoint: MeasurementCheckpoint) {
    if (syncOptions.flags.studioFlagSyncStatsOutputDirectory.isNotEmpty()) {
      writeCheckpointTimestamp(syncOptions.flags.studioFlagSyncStatsOutputDirectory, checkpoint)
      if (checkpoint == MeasurementCheckpoint.ANDROID_FINISHED) {
        writeStatsToFile(syncOptions.flags.studioFlagSyncStatsOutputDirectory, syncCounters)
      }
    }
    if (syncOptions.flags.studioHprofOutputDirectory.isNotEmpty()) {
      captureSnapshot(syncOptions.flags.studioHprofOutputDirectory, checkpoint)
    }
    if (syncOptions.flags.studioHeapAnalysisOutputDirectory.isNotEmpty()) {
      if (syncOptions.flags.studioHeapAnalysisLightweightMode) {
        captureHeapHistogramOfCurrentProcess(syncOptions.flags.studioHeapAnalysisOutputDirectory, checkpoint)
      }
      else {
        analyzeCurrentProcessHeap(syncOptions.flags.studioHeapAnalysisOutputDirectory, checkpoint)
      }
    }
  }

  fun populateProjectModels(
    controller: BuildController,
    projectModel: BasicGradleProject,
    modelConsumer: GradleModelConsumer
  ) {
    val pluginModel = controller.findModel(projectModel, GradlePluginModel::class.java) ?: return
    modelConsumer.consumeProjectModel(projectModel, pluginModel, GradlePluginModel::class.java)
  }

  private fun populateDebugInfo(buildModel: GradleBuild, consumer: GradleModelConsumer) {
    val classLoader = javaClass.classLoader
    if (classLoader is URLClassLoader) {
      val classpath = classLoader.urLs.joinToString { url -> url.toURI()?.let { File(it).absolutePath }.orEmpty() }
      val debugInfo = IdeDebugInfoImpl(mapOf(AndroidExtraModelProvider::class.java.simpleName to classpath))
      consumer.consumeBuildModel(buildModel, debugInfo, IdeDebugInfo::class.java)
    }
  }
}

private fun buildModelsAndMap(
  buildModel: GradleBuild,
  controller: BuildController
): BuildModelsAndMap {
  val gradleSupportsBuildSrcAsCompositeMember = checkGradleVersionIsAtLeast(controller, buildModel, GradleVersion.version("8.0"))
  val buildModels =
    flattenDag(
      root = buildModel,
      getId = { it.buildIdentifier.rootDir },
      getChildren = {
        runCatching {
          if (gradleSupportsBuildSrcAsCompositeMember) {
            val builds = it.editableBuilds.all
            if (builds.isEmpty()) {
              it.includedBuilds.all
            }
            else {
              builds
            }
          } else {
            it.includedBuilds.all
          }
        }.getOrDefault(/* old Gradle? */ emptyList())
      }
    )
      .toSet()
  val buildMap = buildCompositeBuildMap(controller, buildModel, buildModels)
  return BuildModelsAndMap(buildModels, buildMap)
}

private fun buildCompositeBuildMap(
  controller: BuildController,
  buildModel: GradleBuild,
  buildModels: Set<GradleBuild>
): IdeCompositeBuildMapImpl {
  val gradleSupportsDirectTaskInvocationInComposites = checkGradleVersionIsAtLeast(controller, buildModel, GradleVersion.version("6.8"))
  return IdeCompositeBuildMapImpl(
    builds = listOf(IdeBuildImpl(":", buildModel.buildIdentifier.rootDir)) +
      buildModels
        .mapNotNull { build -> controller.findModel(build.rootProject, BuildMap::class.java) }
        .flatMap { buildNames -> buildNames.buildIdMap.entries.map { IdeBuildImpl(it.key, it.value) } }
        .distinct(),
    gradleSupportsDirectTaskInvocation = gradleSupportsDirectTaskInvocationInComposites
  )
}

private fun checkGradleVersionIsAtLeast(controller: BuildController,
                                        buildModel: GradleBuild,
                                        version: GradleVersion): Boolean {
  val buildEnvironment = controller.findModel(buildModel, BuildEnvironment::class.java)
                         ?: error("Cannot get BuildEnvironment model")
  val parsedGradleVersion = GradleVersion.version(buildEnvironment.gradle.gradleVersion)
  return parsedGradleVersion >= version
}

private fun <T : Any> flattenDag(root: T, getId: (T) -> Any = { it }, getChildren: (T) -> List<T>): List<T> = sequence {
  val seen = HashSet<Any>()
  val queue = ArrayDeque(listOf(root))

  while (queue.isNotEmpty()) {
    val item = queue.removeFirst()
    if (seen.add(getId(item))) {
      queue.addAll(getChildren(item))
      yield(item)
    }
  }
}
  .toList()

private fun writeStatsToFile(directory: String, syncCounters: SyncCounters) {
  File(directory).resolve("${Instant.now().toEpochMilli()}_sync_stats").writeText(syncCounters.toString())
  println(syncCounters)
}

private fun writeCheckpointTimestamp(directory: String, checkpoint: MeasurementCheckpoint) {
  val now = Instant.now()
  File(directory).resolve("${now.toEpochMilli()}_${checkpoint.name}").writeText(now.toString())
}