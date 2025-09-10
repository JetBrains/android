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
package com.android.screenshottest.util

import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.onEdt
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.RunsInEdt
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [ScreenshotTestRunner].
 */
@RunsInEdt // Apply the annotation to the whole class for consistency.
class ScreenshotTestRunnerTest {

  @get:Rule
  val projectRule = AndroidGradleProjectRule().onEdt()

  private lateinit var appModule: Module

  @Before
  fun setup() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APP_WITH_SCREENSHOT_TEST)
    IndexingTestUtil.waitUntilIndexesAreReady(projectRule.project)
    appModule = ModuleManager.getInstance(projectRule.project).modules.first { AndroidFacet.getInstance(it) != null }
  }

  @Test
  fun run_doesNothingIfNoProjectSystemFound() {
    // Arrange
    // In this test, we do NOT register a mock extension.
    val runner = ScreenshotTestRunner(projectRule.project, appModule)
    val latch = CountDownLatch(1)

    // Act
    runner.run(setOf("com.example.screenshottest.ExampleScreenshotTest"), object : TaskCallback {
      override fun onSuccess() = latch.countDown()
      override fun onFailure() = latch.countDown()
    })

    // Assert
    // The run method should return immediately without calling the callback.
    // We wait for a short period to confirm the latch was not triggered.
    val completed = latch.await(2, TimeUnit.SECONDS)
    assertFalse("Callback should not have been called when no project system is found.", completed)
  }

  //TODO(b/446134884): Add more test cases
}