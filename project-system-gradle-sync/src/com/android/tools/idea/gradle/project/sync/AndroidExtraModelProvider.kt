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
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.gradle.GradleBuild
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider

@UsedInBuildAction
class AndroidExtraModelProvider(private val syncOptions: SyncActionOptions) : ProjectImportModelProvider {
  private var buildModels: Set<GradleBuild>? = null
  private val seenBuildModels: MutableSet<GradleBuild> = mutableSetOf()

  override fun populateBuildModels(
    controller: BuildController,
    buildModel: GradleBuild,
    consumer: ProjectImportModelProvider.BuildModelConsumer
  ) {
    // Flatten the platform's handling of included builds. We need all models together to resolve cross `includeBuild` dependencies
    // correctly. This, unfortunately, makes assumptions about the order in which these methods are invoked. If broken it will be caught
    // by any test attempting to sync a composite build.
    if (buildModels == null) {
      buildModels =
        flattenDag(buildModel, getId = { it.buildIdentifier.rootDir }) {
          runCatching { it.includedBuilds.all }.getOrDefault(/* old Gradle? */ emptyList())
        }
          .toSet()
    }
    if (!seenBuildModels.add(buildModel)) {
      error("Included build ${buildModel.buildIdentifier.rootDir} appears for the second time")
    }
    if (!buildModels.orEmpty().contains(buildModel)) {
      error("Unknown included build ${buildModel.buildIdentifier.rootDir} encountered")
    }
    if (buildModels.orEmpty().size == seenBuildModels.size) {
      AndroidExtraModelProviderWorker(
        controller,
        syncOptions,
        buildModels!!.toList(),
        // Consumers for different build models are all equal except they aggregate statistics to different targets. We cannot request all
        // models we need until we have enough information to do it. In the case of a composite builds all model fetching time will be
        // reported against the last included build.
        consumer
      ).populateBuildModels()
    }
  }

  override fun populateProjectModels(
    controller: BuildController,
    projectModel: Model,
    modelConsumer: ProjectImportModelProvider.ProjectModelConsumer
  ) {
    controller.findModel(projectModel, GradlePluginModel::class.java)
      ?.also { pluginModel -> modelConsumer.consume(pluginModel, GradlePluginModel::class.java) }
  }
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

