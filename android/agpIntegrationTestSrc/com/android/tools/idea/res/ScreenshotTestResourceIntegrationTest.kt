/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.res

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.findModule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class ScreenshotTestResourceIntegrationTest {

  @get:Rule val projectRule = AndroidGradleProjectRule().onEdt()

  @Test
  fun testScreenshotModuleResourcesIncludeAppResources() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APP_WITH_SCREENSHOT_TEST)

    val project = projectRule.project

    // The name of the screenshot test module. It usually follows "app.screenshotTest"
    val screenshotModule = project.findModule("app.screenshotTest") ?: error("Screenshot test module not found")
    val appModule = project.findModule("app.main") ?: error("App main module not found")

    val repoManager = StudioResourceRepositoryManager.getInstance(screenshotModule) ?: error("Repo manager not found")
    // Verify AppResourceRepository
    val appResources = repoManager.appResources
    val stringResources = appResources.getResources(ResourceNamespace.RES_AUTO, ResourceType.STRING)
    assertThat(stringResources.keySet()).contains("app_name")

    // Verify TestAppResourceRepository (covers your other file change)
    val testAppResources = repoManager.testAppResources
    val testStringResources = testAppResources.getResources(ResourceNamespace.RES_AUTO, ResourceType.STRING)
    assertThat(testStringResources.keySet()).contains("app_name")
  }
}
