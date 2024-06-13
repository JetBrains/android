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
package org.jetbrains.android.uipreview

import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.structure.model.getModuleByGradlePath
import com.android.tools.idea.testing.AndroidModuleDependency
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onSubscription
import java.nio.file.Files
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

internal class NotificationManagerTest {
  @get:Rule
  val projectRule =
    AndroidProjectRule.withAndroidModels(
      JavaModuleModelBuilder.rootModuleBuilder,
      AndroidModuleModelBuilder(
        gradlePath = ":app",
        selectedBuildVariant = "debug",
        projectBuilder =
          AndroidProjectBuilder(
            projectType = { IdeAndroidProjectType.PROJECT_TYPE_APP },
            namespace = { "com.example.app" },
            androidModuleDependencyList = {
              listOf(AndroidModuleDependency(moduleGradlePath = ":lib", variant = "debug"))
            },
          ),
      ),
      AndroidModuleModelBuilder(
        gradlePath = ":lib",
        selectedBuildVariant = "debug",
        projectBuilder =
          AndroidProjectBuilder(
            namespace = { "com.example.lib" },
            projectType = { IdeAndroidProjectType.PROJECT_TYPE_LIBRARY },
          ),
      ),
    )

  @Test
  fun `flow is updated on every modification`() = runBlocking {
    val app = projectRule.project.getModuleByGradlePath(":app") ?: fail("Could not find app")
    val lib = projectRule.project.getModuleByGradlePath(":lib") ?: fail("Could not find lib")

    // Copy the classes into a temp directory to use as overlay
    val tempOverlayPath = Files.createTempDirectory("overlayTest")

    withTimeout(5.seconds) {
      // Wait for the global modification count to reach 2
      ModuleClassLoaderOverlays.NotificationManager.getInstance(projectRule.project)
        .modificationFlow
        .onSubscription {
          // Only do the modifications once we know the subscription has started to avoid flaky tests.
          launch {
            ModuleClassLoaderOverlays.getInstance(app).pushOverlayPath(tempOverlayPath)
            ModuleClassLoaderOverlays.getInstance(lib).pushOverlayPath(tempOverlayPath)
          }
        }
        .takeWhile { it != 2L }
        .collect {}
    }

    assertEquals(1, ModuleClassLoaderOverlays.getInstance(app).modificationTracker.modificationCount)
    assertEquals(1, ModuleClassLoaderOverlays.getInstance(lib).modificationTracker.modificationCount)
  }
}
