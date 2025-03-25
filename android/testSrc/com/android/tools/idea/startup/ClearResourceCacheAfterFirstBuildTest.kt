/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.startup

import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.projectsystem.TestProjectSystem
import com.android.tools.idea.res.ResourceClassRegistry
import com.android.tools.idea.res.StudioResourceIdManager
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.Facets
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.replaceService
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.resourceManagers.LocalResourceManager
import org.jetbrains.android.resourceManagers.ModuleResourceManagers
import org.junit.*
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@RunWith(JUnit4::class)
class ClearResourceCacheAfterFirstBuildTest {
  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.onDisk().initAndroid(false)

  private lateinit var project: Project
  private lateinit var projectSystem: TestProjectSystem
  private lateinit var clearResourceCacheAfterFirstBuild: ClearResourceCacheAfterFirstBuild
  private lateinit var onCacheClean: TestRunnable
  private lateinit var onSourceGenerationError: TestRunnable
  private lateinit var mockDisposable: Disposable

  private class TestRunnable(private val toRun: () -> Unit) : Runnable {
    var hasRun = false
      private set

    override fun run() {
      toRun()
      hasRun = true
    }
  }

  @Before
  fun setUp() {
    project = projectRule.project

    projectSystem = TestProjectSystem(project, lastSyncResult = SyncResult.UNKNOWN)
    projectSystem.useInTests()

    clearResourceCacheAfterFirstBuild = ClearResourceCacheAfterFirstBuild.getInstance(project)

    onCacheClean = TestRunnable {
      assertWithMessage("onCacheClean callback was called before resource cache was cleared")
        .that(clearResourceCacheAfterFirstBuild.isCacheClean()).isTrue()
    }

    onSourceGenerationError = TestRunnable {
      assertWithMessage("onSourceGenerationError callback was called after resource cache was cleared")
        .that(clearResourceCacheAfterFirstBuild.isCacheClean()).isFalse()
    }

    mockDisposable = Disposer.newDisposable()
  }

  @After
  fun after() {
    Disposer.dispose(mockDisposable)
  }

  @Test
  fun isCacheClean_falseBeforeCacheClean() {
    assertThat(clearResourceCacheAfterFirstBuild.isCacheClean()).isFalse()
  }

  @Test
  fun isCacheClean_trueAfterCacheClean() {
    clearResourceCacheAfterFirstBuild.syncSucceeded()
    assertThat(clearResourceCacheAfterFirstBuild.isCacheClean()).isTrue()
  }

  @Test
  fun runWhenResourceCacheClean_cacheAlreadyCleared() {
    projectSystem.emulateSync(SyncResult.SUCCESS)

    clearResourceCacheAfterFirstBuild.runWhenResourceCacheClean(onCacheClean, onSourceGenerationError, mockDisposable)

    assertThat(onCacheClean.hasRun).isTrue()
    assertThat(onSourceGenerationError.hasRun).isFalse()
  }

  @Test
  fun runWhenResourceCacheClean_errorAlreadyOccurredAndCacheAlreadyCleared() {
    projectSystem.emulateSync(SyncResult.FAILURE)
    projectSystem.emulateSync(SyncResult.SUCCESS)

    clearResourceCacheAfterFirstBuild.runWhenResourceCacheClean(onCacheClean, onSourceGenerationError, mockDisposable)

    // Ignore errors as long as the resource cache has been cleared once.
    assertThat(onCacheClean.hasRun).isTrue()
    assertThat(onSourceGenerationError.hasRun).isFalse()
  }

  @Test
  fun runWhenResourceCacheClean_errorAlreadyOccurredButCacheNotCleared() {
    projectSystem.emulateSync(SyncResult.FAILURE)

    clearResourceCacheAfterFirstBuild.runWhenResourceCacheClean(onCacheClean, onSourceGenerationError, mockDisposable)
    assertThat(onCacheClean.hasRun).isFalse()
    assertThat(onSourceGenerationError.hasRun).isTrue()
  }

  @Test
  fun runWhenResourceCacheClean_waits() {
    clearResourceCacheAfterFirstBuild.runWhenResourceCacheClean(onCacheClean, onSourceGenerationError, mockDisposable)
    assertThat(onCacheClean.hasRun).isFalse()
    assertThat(onSourceGenerationError.hasRun).isFalse()
  }

  @Test
  fun runWhenResourceCacheClean_errorAlreadyOccurredAndThenCacheClean() {
    projectSystem.emulateSync(SyncResult.FAILURE)
    clearResourceCacheAfterFirstBuild.runWhenResourceCacheClean(onCacheClean, onSourceGenerationError, mockDisposable)
    projectSystem.emulateSync(SyncResult.SUCCESS)

    assertThat(onCacheClean.hasRun).isTrue()
  }

  @Test
  fun runWhenResourceCacheClean_cacheClearedLater() {
    clearResourceCacheAfterFirstBuild.runWhenResourceCacheClean(onCacheClean, onSourceGenerationError, mockDisposable)

    projectSystem.emulateSync(SyncResult.SUCCESS)
    assertThat(onCacheClean.hasRun).isTrue()
    assertThat(onSourceGenerationError.hasRun).isFalse()
  }

  @Test
  fun runWhenResourceCacheClean_errorLater() {
    clearResourceCacheAfterFirstBuild.runWhenResourceCacheClean(onCacheClean, onSourceGenerationError, mockDisposable)

    projectSystem.emulateSync(SyncResult.FAILURE)
    assertThat(onCacheClean.hasRun).isFalse()
    assertThat(onSourceGenerationError.hasRun).isTrue()
  }

  @Test
  fun runWhenResourceCacheClean_errorLaterAndThenCacheClean() {
    clearResourceCacheAfterFirstBuild.runWhenResourceCacheClean(onCacheClean, onSourceGenerationError, mockDisposable)
    projectSystem.emulateSync(SyncResult.FAILURE)
    projectSystem.emulateSync(SyncResult.SUCCESS)

    assertThat(onCacheClean.hasRun).isTrue()
  }

  @Test
  fun cacheActuallyCleared() {
    val module = projectRule.module
    Facets.createAndAddAndroidFacet(module)

    val disposable = projectRule.testRootDisposable

    // Project service
    val resourceClassRegistry: ResourceClassRegistry = mock()
    project.replaceService(ResourceClassRegistry::class.java, resourceClassRegistry, disposable)

    // Module service
    val localResourceManager: LocalResourceManager = mock()
    val moduleResourceManagers: ModuleResourceManagers = mock {
      on { getLocalResourceManager() } doReturn localResourceManager
    }
    module.replaceService(ModuleResourceManagers::class.java, moduleResourceManagers, disposable)

    clearResourceCacheAfterFirstBuild.setIncompleteRuntimeDependencies()
    clearResourceCacheAfterFirstBuild.syncSucceeded()

    verify(resourceClassRegistry, times(2)).clearCache()
    verify(localResourceManager).invalidateAttributeDefinitions()
  }
}
