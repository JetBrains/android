/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.run.configuration.execution

import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase.assertEquals
import com.android.tools.idea.run.configuration.AndroidTileConfiguration
import com.android.tools.idea.run.configuration.AndroidTileConfigurationType
import com.android.tools.idea.run.configuration.AndroidTileRunConfigurationProducer
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AndroidTileRunConfigurationProducerTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().onEdt()

  @Before
  fun setUp() {
    projectRule.fixture.addWearDependenciesToProject()
  }

  @Test
  @RunsInEdt
  fun testSetupConfigurationFromContext() {
    val tileFile = projectRule.fixture.addFileToProject(
      "src/com/example/myapplication/MyTileService.kt",
      """
      package com.example.myapplication

      import androidx.wear.tiles.TileService

      class MyTestTile : TileService() {
      }
      """.trimIndent())

    val classElement = tileFile.findElementByText("class")
    val configurationFromClass = createConfigurationFromElement(classElement)

    assertEquals("MyTestTile", configurationFromClass.name)
    assertEquals("com.example.myapplication.MyTestTile", configurationFromClass.componentLaunchOptions.componentName)
    assertEquals(projectRule.fixture.module, configurationFromClass.module)
  }

  @Test
  @RunsInEdt
  fun testJavaSetupConfigurationFromContext() {
    val tileFile = projectRule.fixture.addFileToProject(
      "src/com/example/myapplication/MyTileService.java",
      """
      package com.example.myapplication;

      import androidx.wear.tiles.TileService;
        
      public class MyTileService extends TileService {
      }
      """.trimIndent())

    val classElement = tileFile.findElementByText("class")
    val configurationFromClass = createConfigurationFromElement(classElement)

    assertEquals("MyTileService", configurationFromClass.name)
    assertEquals("com.example.myapplication.MyTileService", configurationFromClass.componentLaunchOptions.componentName)
    assertEquals(projectRule.fixture.module, configurationFromClass.module)
  }

  @Test
  @RunsInEdt
  fun testSetupConfigurationFromContextHandlesMissingModuleGracefully() {
    val tileFile = projectRule.fixture.addFileToProject(
      "src/com/example/myapplication/MyTileService.kt",
      """
      package com.example.myapplication

      import androidx.wear.tiles.TileService

      class MyTestTile : TileService() {
      }
      """.trimIndent())

    val classElement = tileFile.findElementByText("class")
    val context = mock<ConfigurationContext>()
    whenever(context.psiLocation).thenReturn(classElement)
    whenever(context.module).thenReturn(null)

    val producer = AndroidTileRunConfigurationProducer()
    assertThat(producer.setupConfigurationFromContext(createRunConfiguration(), context, Ref(context.psiLocation))).isFalse()
  }

  private fun createConfigurationFromElement(element: PsiElement): AndroidTileConfiguration {
    val context = ConfigurationContext(element)
    val runConfiguration = createRunConfiguration()
    val producer = AndroidTileRunConfigurationProducer()
    producer.setupConfigurationFromContext(runConfiguration, context, Ref(context.psiLocation))

    return runConfiguration
  }

  private fun createRunConfiguration() =
    AndroidTileConfigurationType().configurationFactories[0].createTemplateConfiguration(projectRule.project) as AndroidTileConfiguration
}