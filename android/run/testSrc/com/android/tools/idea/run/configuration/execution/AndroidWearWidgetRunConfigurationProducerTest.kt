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
package com.android.tools.idea.run.configuration.execution

import com.android.tools.idea.run.configuration.AndroidWearWidgetConfiguration
import com.android.tools.idea.run.configuration.AndroidWearWidgetConfigurationType
import com.android.tools.idea.run.configuration.AndroidWearWidgetRunConfigurationProducer
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

class AndroidWearWidgetRunConfigurationProducerTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  @Before
  fun setUp() {
    projectRule.fixture.addWearDependenciesToProject()
  }

  @Test
  @RunsInEdt
  fun testSetupConfigurationFromContext() {
    val widgetFile =
      projectRule.fixture.addFileToProject(
        "src/com/example/myapplication/MyWidgetService.kt",
        """
        package com.example.myapplication

        import androidx.glance.wear.GlanceWearWidgetService

        class MyTestWidget : GlanceWearWidgetService() {
        }
        """
          .trimIndent(),
      )

    val classElement = widgetFile.findElementByText("class")
    val configurationFromClass = createConfigurationFromElement(classElement)

    assertThat("MyTestWidget").isEqualTo(configurationFromClass.name)
    assertThat("com.example.myapplication.MyTestWidget").isEqualTo(configurationFromClass.componentLaunchOptions.componentName)
    assertThat(projectRule.fixture.module).isEqualTo(configurationFromClass.module)
  }

  @Test
  @RunsInEdt
  fun testJavaSetupConfigurationFromContext() {
    val widgetFile =
      projectRule.fixture.addFileToProject(
        "src/com/example/myapplication/MyWidgetService.java",
        """
        package com.example.myapplication;

        import androidx.glance.wear.GlanceWearWidgetService;

        public class MyWidgetService extends GlanceWearWidgetService {
        }
        """
          .trimIndent(),
      )

    val classElement = widgetFile.findElementByText("class")
    val configurationFromClass = createConfigurationFromElement(classElement)

    assertThat("MyWidgetService").isEqualTo(configurationFromClass.name)
    assertThat("com.example.myapplication.MyWidgetService").isEqualTo(configurationFromClass.componentLaunchOptions.componentName)
    assertThat(projectRule.fixture.module).isEqualTo(configurationFromClass.module)
  }

  @Test
  @RunsInEdt
  fun testSetupConfigurationFromContextHandlesMissingModuleGracefully() {
    val widgetFile =
      projectRule.fixture.addFileToProject(
        "src/com/example/myapplication/MyWidgetService.kt",
        """
        package com.example.myapplication

        import androidx.glance.wear.GlanceWearWidgetService

        class MyTestWidget : GlanceWearWidgetService() {
        }
        """
          .trimIndent(),
      )

    val classElement = widgetFile.findElementByText("class")
    val context = mock<ConfigurationContext>()
    whenever(context.psiLocation).thenReturn(classElement)
    whenever(context.module).thenReturn(null)

    val producer = AndroidWearWidgetRunConfigurationProducer()
    assertThat(producer.setupConfigurationFromContext(createRunConfiguration(), context, Ref(context.psiLocation))).isFalse()
  }

  private fun createConfigurationFromElement(element: PsiElement): AndroidWearWidgetConfiguration {
    val context = ConfigurationContext(element)
    val runConfiguration = createRunConfiguration()
    val producer = AndroidWearWidgetRunConfigurationProducer()
    producer.setupConfigurationFromContext(runConfiguration, context, Ref(context.psiLocation))

    return runConfiguration
  }

  private fun createRunConfiguration() =
    AndroidWearWidgetConfigurationType().configurationFactories.single().createTemplateConfiguration(projectRule.project)
      as AndroidWearWidgetConfiguration
}
