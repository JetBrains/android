/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run

import com.android.tools.idea.projectsystem.ApplicationProjectContextProvider.RunningApplicationIdentity
import com.android.tools.ndk.run.SymbolDir
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import com.google.idea.blaze.android.projectsystem.BazelProjectSystem
import com.google.idea.blaze.android.resources.BlazeLightResourceClassService
import com.google.idea.blaze.base.BlazeTestCase
import com.google.idea.blaze.base.run.DeployedApplicationTargetStore
import com.google.idea.blaze.base.run.RuntimeArtifactCache
import com.google.idea.blaze.base.run.RuntimeArtifactKind
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.artifact.OutputArtifact
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.file.Path
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Unit tests for [BazelApplicationProjectContextProvider]. */
@RunWith(JUnit4::class)
class BazelApplicationProjectContextProviderTest : BlazeTestCase() {

  private val mockArtifactCache = MockRuntimeArtifactCache()
  private val mockTargetStore = MockDeployedApplicationTargetStore()

  override fun initTest(applicationServices: Container, projectServices: Container) {
    super.initTest(applicationServices, projectServices)
    projectServices.register(RuntimeArtifactCache::class.java, mockArtifactCache)
    projectServices.register(DeployedApplicationTargetStore::class.java, mockTargetStore)
    val constructor =
      BlazeLightResourceClassService::class.java.getDeclaredConstructor(Project::class.java).apply {
        isAccessible = true
      }
    projectServices.register(BlazeLightResourceClassService::class.java, constructor.newInstance(project))
  }

  @Test
  fun testComputeApplicationProjectContext_withCachedSymbols() {
    val target = Label.of("//java/com/example:app")
    mockTargetStore.trackTargetForApplication("com.example.app", target)
    val symbolFile1 = Path.of("/path/to/symbols1/libnative1.so")
    val symbolFile2 = Path.of("/path/to/symbols2/libnative2.so")
    mockArtifactCache.cachedArtifactsMap[target] = ImmutableList.of(symbolFile1, symbolFile2)

    val provider = BazelApplicationProjectContextProvider()
    val projectSystem = BazelProjectSystem(project)
    val identity = RunningApplicationIdentity(processName = "com.example.app", applicationId = "com.example.app")

    val context = provider.computeApplicationProjectContext(projectSystem, identity)
    assertThat(context).isNotNull()
    assertThat(context).isInstanceOf(BazelApplicationProjectContext::class.java)

    val bazelContext = context as BazelApplicationProjectContext
    assertThat(bazelContext.applicationId).isEqualTo("com.example.app")
    assertThat(bazelContext.project).isSameInstanceAs(project)
    assertThat(bazelContext.liveEditDataExtractor).isNull()
    assertThat(bazelContext.symbolDirs)
      .containsExactly(
        SymbolDir.WithoutSubdirectories(File("/path/to/symbols1")),
        SymbolDir.WithoutSubdirectories(File("/path/to/symbols2")),
      )
  }

  @Test
  fun testComputeApplicationProjectContext_withoutCachedSymbols_returnsEmptySymbolDirs() {
    val provider = BazelApplicationProjectContextProvider()
    val projectSystem = BazelProjectSystem(project)
    val identity = RunningApplicationIdentity(processName = "com.example.uncached", applicationId = "com.example.uncached")

    val context = provider.computeApplicationProjectContext(projectSystem, identity)
    assertThat(context).isNotNull()
    assertThat(context).isInstanceOf(BazelApplicationProjectContext::class.java)

    val bazelContext = context as BazelApplicationProjectContext
    assertThat(bazelContext.applicationId).isEqualTo("com.example.uncached")
    assertThat(bazelContext.symbolDirs).isEmpty()
  }

  @Test
  fun testComputeApplicationProjectContext_evaluatesSymbolDirsLazily() {
    val target = Label.of("//java/com/example:app")
    mockTargetStore.trackTargetForApplication("com.example.app", target)
    mockArtifactCache.cachedArtifactsMap[target] = ImmutableList.of(Path.of("/path/to/symbols/libnative.so"))
    mockTargetStore.resetQueryCounts()
    mockArtifactCache.resetQueryCounts()

    val provider = BazelApplicationProjectContextProvider()
    val projectSystem = BazelProjectSystem(project)
    val identity = RunningApplicationIdentity(processName = "com.example.app", applicationId = "com.example.app")

    val context = provider.computeApplicationProjectContext(projectSystem, identity)
    assertThat(context).isNotNull()
    // Verify target store and cache have NOT been queried during computeApplicationProjectContext
    assertThat(mockTargetStore.queryCount).isEqualTo(0)
    assertThat(mockArtifactCache.queryCount).isEqualTo(0)

    val bazelContext = context as BazelApplicationProjectContext
    // Now access symbolDirs
    val symbolDirs = bazelContext.symbolDirs
    assertThat(symbolDirs).containsExactly(SymbolDir.WithoutSubdirectories(File("/path/to/symbols")))
    // Queried on first access
    assertThat(mockTargetStore.queryCount).isEqualTo(1)
    assertThat(mockArtifactCache.queryCount).isEqualTo(1)

    // Accessing again should use cached lazy value without re-querying
    val symbolDirsAgain = bazelContext.symbolDirs
    assertThat(symbolDirsAgain).containsExactly(SymbolDir.WithoutSubdirectories(File("/path/to/symbols")))
    assertThat(mockTargetStore.queryCount).isEqualTo(1)
    assertThat(mockArtifactCache.queryCount).isEqualTo(1)
  }

  @Test
  fun testComputeApplicationProjectContext_withNullApplicationId_returnsNull() {
    val provider = BazelApplicationProjectContextProvider()
    val projectSystem = BazelProjectSystem(project)
    val identity = RunningApplicationIdentity(processName = null, applicationId = null)

    val context = provider.computeApplicationProjectContext(projectSystem, identity)
    assertThat(context).isNull()
  }

  private class MockDeployedApplicationTargetStore : DeployedApplicationTargetStore {
    val trackedTargets = mutableMapOf<String, Label>()
    var queryCount = 0

    fun resetQueryCounts() {
      queryCount = 0
    }

    override fun trackTargetForApplication(applicationId: String, target: Label) {
      trackedTargets[applicationId] = target
    }

    override fun getTargetForApplication(applicationId: String): Label? {
      queryCount++
      return trackedTargets[applicationId]
    }

    override fun getAllApplicationIds(): Set<String> = trackedTargets.keys
  }

  private class MockRuntimeArtifactCache : RuntimeArtifactCache {
    val cachedArtifactsMap = mutableMapOf<Label, ImmutableList<Path>>()
    var queryCount = 0

    fun resetQueryCounts() {
      queryCount = 0
    }

    override fun fetchArtifacts(
      target: Label,
      artifacts: List<OutputArtifact>,
      context: BlazeContext,
      artifactKind: RuntimeArtifactKind,
    ): ImmutableList<Path> {
      return ImmutableList.of()
    }

    override fun getCachedArtifacts(target: Label, artifactKind: RuntimeArtifactKind): ImmutableList<Path> {
      queryCount++
      return cachedArtifactsMap[target] ?: ImmutableList.of()
    }
  }
}
