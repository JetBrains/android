/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.legacyselector

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.AndroidRunConfigurationModule
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.module.Module
import com.intellij.testFramework.replaceService
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidFacetConfiguration
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock

class LaunchCompatibilityCheckerSupplierTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testGet() {
    val project = projectRule.project
    val configurationModule: AndroidRunConfigurationModule = mock()
    whenever(configurationModule.module).thenReturn(projectRule.module)

    val configuration: AndroidRunConfiguration = mock()
    whenever(configuration.configurationModule).thenReturn(configurationModule)

    val configurationAndSettings: RunnerAndConfigurationSettings = mock()
    whenever(configurationAndSettings.configuration).thenReturn(configuration)

    val mockRunManager: RunManager = mock()
    whenever(mockRunManager.selectedConfiguration).thenReturn(configurationAndSettings)
    project.replaceService(RunManager::class.java, mockRunManager, projectRule.testRootDisposable)

    val checkerSupplier = LaunchCompatibilityCheckerSupplier(project) { module: Module -> newAndroidFacet(module) }
    Assert.assertNull(checkerSupplier.get())
  }

  companion object {
    private fun newAndroidFacet(module: Module): AndroidFacet {
      return AndroidFacet(module, "Android", mock(AndroidFacetConfiguration::class.java))
    }
  }
}
