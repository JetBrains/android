/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.imports

import com.android.testutils.waitForCondition
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import java.nio.charset.StandardCharsets.UTF_8
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@RunWith(JUnit4::class)
class MavenClassRegistryManagerTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val testScheduler = TestCoroutineScheduler()
  private val testDispatcher = StandardTestDispatcher(testScheduler)
  private val testScope = TestScope(testDispatcher)

  private val gMavenIndexRepositoryListeners: MutableList<GMavenIndexRepositoryListener> =
    mutableListOf()

  private val mockGMavenIndexRepository: GMavenIndexRepository = mock {
    on { loadIndexFromDisk() } doAnswer { """{ "Index": [] }""".byteInputStream(UTF_8) }
    on { addListener(any(), any()) } doAnswer
      { inv ->
        gMavenIndexRepositoryListeners.add(inv.arguments[0] as GMavenIndexRepositoryListener)
        null
      }
  }

  private val mavenClassRegistryManager by lazy {
    MavenClassRegistryManager(testScope, testDispatcher, testDispatcher).also {
      Disposer.register(projectRule.testRootDisposable, it)
    }
  }

  @Before
  fun setUp() {
    application.replaceService(
      GMavenIndexRepository::class.java,
      mockGMavenIndexRepository,
      projectRule.testRootDisposable,
    )
  }

  @Test
  fun blockingRead() {
    verify(mockGMavenIndexRepository, never()).loadIndexFromDisk()

    val registry1 = mavenClassRegistryManager.getMavenClassRegistryBlockingForTest()
    verify(mockGMavenIndexRepository, times(1)).loadIndexFromDisk()

    // Second call should not trigger a new load.
    val registry2 = mavenClassRegistryManager.getMavenClassRegistryBlockingForTest()
    verify(mockGMavenIndexRepository, times(1)).loadIndexFromDisk()

    assertThat(registry2).isSameAs(registry1)
  }

  @Test
  fun tryRead() {
    // Multiple calls should kick off the read process only once, and it won't run until the
    // coroutine dispatcher runs.
    verify(mockGMavenIndexRepository, never()).loadIndexFromDisk()
    assertThat(mavenClassRegistryManager.tryGetMavenClassRegistry()).isNull()

    verify(mockGMavenIndexRepository, never()).loadIndexFromDisk()
    assertThat(mavenClassRegistryManager.tryGetMavenClassRegistry()).isNull()

    verify(mockGMavenIndexRepository, never()).loadIndexFromDisk()
    assertThat(mavenClassRegistryManager.tryGetMavenClassRegistry()).isNull()

    // Allowing the coroutine to complete means a result is now returned.
    testScheduler.runCurrent()
    verify(mockGMavenIndexRepository, times(1)).loadIndexFromDisk()
    val registry1 = mavenClassRegistryManager.tryGetMavenClassRegistry()
    assertThat(registry1).isNotNull()

    // Second call should not trigger a new load.
    testScheduler.runCurrent()
    assertThat(mavenClassRegistryManager.tryGetMavenClassRegistry()).isNotNull()
    val registry2 = mavenClassRegistryManager.tryGetMavenClassRegistry()
    assertThat(registry2).isNotNull()
    assertThat(registry2).isSameAs(registry1)

    testScheduler.runCurrent()
    verify(mockGMavenIndexRepository, times(1)).loadIndexFromDisk()
  }

  @Test
  fun suspendingRead() =
    testScope.runTest {
      verify(mockGMavenIndexRepository, never()).loadIndexFromDisk()

      val registry1 = mavenClassRegistryManager.getMavenClassRegistry()
      verify(mockGMavenIndexRepository, times(1)).loadIndexFromDisk()

      // Second call should not trigger a new load.
      val registry2 = mavenClassRegistryManager.getMavenClassRegistry()
      verify(mockGMavenIndexRepository, times(1)).loadIndexFromDisk()

      assertThat(registry2).isSameAs(registry1)
    }

  @Test
  fun indexUpdated() {
    assertThat(gMavenIndexRepositoryListeners).isEmpty()

    // Listener should be registered when the registry is initialized.
    val registry1 = mavenClassRegistryManager.getMavenClassRegistryBlockingForTest()
    assertThat(gMavenIndexRepositoryListeners).hasSize(1)

    // Subsequent calls to the various getters should all return the same registry.
    assertThat(mavenClassRegistryManager.getMavenClassRegistryBlockingForTest()).isSameAs(registry1)
    assertThat(mavenClassRegistryManager.tryGetMavenClassRegistry()).isSameAs(registry1)
    assertThat(runBlocking { mavenClassRegistryManager.getMavenClassRegistry() })
      .isSameAs(registry1)
    verify(mockGMavenIndexRepository, times(1)).loadIndexFromDisk()

    // Update the index
    gMavenIndexRepositoryListeners.single().onIndexUpdated()

    // Ensure that the update didn't happen synchronously; it should happen in the background, so
    // shouldn't do anything until we run the scheduler.
    assertThat(mavenClassRegistryManager.tryGetMavenClassRegistry()).isSameAs(registry1)
    verify(mockGMavenIndexRepository, times(1)).loadIndexFromDisk()

    // Let the manager update.
    testScheduler.runCurrent()

    // Now there should be a new registry.
    val registry2 = mavenClassRegistryManager.tryGetMavenClassRegistry()
    assertThat(registry2).isNotSameAs(registry1)
    assertThat(mavenClassRegistryManager.getMavenClassRegistryBlockingForTest()).isSameAs(registry2)
    assertThat(runBlocking { mavenClassRegistryManager.getMavenClassRegistryBlockingForTest() })
      .isSameAs(registry2)
  }

  /**
   * Returns result of [MavenClassRegistryManager.getMavenClassRegistryBlocking].
   *
   * To call the blocking read method, we have to use a background thread so that control returns to
   * the test and we can advance the coroutine scheduler.
   */
  private fun MavenClassRegistryManager.getMavenClassRegistryBlockingForTest(): MavenClassRegistry {
    var result: MavenClassRegistry? = null
    application.executeOnPooledThread { result = getMavenClassRegistryBlocking() }

    waitForCondition(10.seconds) {
      testScheduler.runCurrent()
      result != null
    }

    return requireNotNull(result)
  }
}
