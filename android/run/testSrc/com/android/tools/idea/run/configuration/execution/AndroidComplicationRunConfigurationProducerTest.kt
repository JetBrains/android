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
import com.android.tools.idea.run.configuration.AndroidComplicationConfiguration
import com.android.tools.idea.run.configuration.AndroidComplicationConfigurationType
import com.android.tools.idea.run.configuration.AndroidComplicationRunConfigurationProducer
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

class AndroidComplicationRunConfigurationProducerTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().onEdt()

  @Before
  fun setUp() {
    projectRule.fixture.addWearDependenciesToProject()
  }


  @Test
  @RunsInEdt
  fun testSetupConfigurationFromContext() {
    val complicationFile = projectRule.fixture.addFileToProject(
      "src/com/example/myapplication/MyComplicationService.kt",
      """
      package com.example.myapplication

      import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService

      class MyTestComplication : ComplicationDataSourceService() {
      }
      """.trimIndent())

    val classElement = complicationFile.findElementByText("class")
    val configurationFromClass = createConfigurationFromElement(classElement)

    assertEquals("MyTestComplication", configurationFromClass.name)
    assertEquals("com.example.myapplication.MyTestComplication", configurationFromClass.componentLaunchOptions.componentName)
    assertEquals(projectRule.fixture.module, configurationFromClass.module)
  }

  @Test
  @RunsInEdt
  fun testJavaSetupConfigurationFromContext() {
    val complicationFile = projectRule.fixture.addFileToProject(
      "src/com/example/myapplication/MyComplicationService.java",
      """
      package com.example.myapplication;

      import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService;
        
      public class MyComplicationService extends ComplicationDataSourceService {
      }
      """.trimIndent())

    val classElement = complicationFile.findElementByText("class")
    val configurationFromClass = createConfigurationFromElement(classElement)

    assertEquals("MyComplicationService", configurationFromClass.name)
    assertEquals("com.example.myapplication.MyComplicationService", configurationFromClass.componentLaunchOptions.componentName)
    assertEquals(projectRule.fixture.module, configurationFromClass.module)
  }

  @Test
  @RunsInEdt
  fun testSetupConfigurationFromContextHandlesMissingModuleGracefully() {
    val complicationFile = projectRule.fixture.addFileToProject(
      "src/com/example/myapplication/MyComplicationService.kt",
      """
      package com.example.myapplication

      import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService

      class MyTestComplication : ComplicationDataSourceService() {
      }
      """.trimIndent())

    val classElement = complicationFile.findElementByText("class")
    val context = mock<ConfigurationContext>()
    whenever(context.psiLocation).thenReturn(classElement)
    whenever(context.module).thenReturn(null)

    val producer = AndroidComplicationRunConfigurationProducer()
    assertThat(producer.setupConfigurationFromContext(createRunConfiguration(), context, Ref(context.psiLocation))).isFalse()
  }

  private fun createConfigurationFromElement(element: PsiElement): AndroidComplicationConfiguration {
    val context = ConfigurationContext(element)
    val runConfiguration = createRunConfiguration()
    val producer = AndroidComplicationRunConfigurationProducer()
    producer.setupConfigurationFromContext(runConfiguration, context, Ref(context.psiLocation))

    return runConfiguration
  }

  private fun createRunConfiguration() = AndroidComplicationConfigurationType().configurationFactories[0]
    .createTemplateConfiguration(projectRule.project) as AndroidComplicationConfiguration
}