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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade.library

import com.android.builder.model.level2.DependencyGraphs
import com.android.builder.model.level2.GraphItem
import com.android.ide.common.gradle.model.UnusedModelMethodException
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.Dependencies

// Graph dependencies are used here to build level 2 dependencies which are actually used in the IDE.
open class LegacyDependencyGraphs(private val dependencies: Dependencies) : DependencyGraphs {
  override fun getCompileDependencies(): List<GraphItem> {
    val moduleLibraries = dependencies.moduleDependencies.mapNotNull { it.artifactAddress }
    val allLibraries = dependencies.androidLibraries + dependencies.javaLibraries + dependencies.nativeLibraries + moduleLibraries

    return allLibraries.map { FlatGraphItem(it) }
  }

  override fun getPackageDependencies(): List<GraphItem> = throw UnusedModelMethodException("getPackageDependencies")
  override fun getProvidedLibraries(): List<String> = throw UnusedModelMethodException("getPackageDependencies")
  override fun getSkippedLibraries(): List<String> = throw UnusedModelMethodException("getSkippedLibraries")
}

class LegacyDependencyGraphsStub(dependencies: Dependencies) : LegacyDependencyGraphs(dependencies) {
  override fun getPackageDependencies(): List<GraphItem> = listOf()
  override fun getProvidedLibraries(): List<String> = listOf()
  override fun getSkippedLibraries(): List<String> = listOf()
}

// All the dependencies are resolved at this point so the graph is flat.
class FlatGraphItem(private val artifactAddress: String) : GraphItem {
  override fun getArtifactAddress(): String = artifactAddress
  override fun getRequestedCoordinates(): String? = null
  override fun getDependencies(): List<GraphItem> = listOf()
}
