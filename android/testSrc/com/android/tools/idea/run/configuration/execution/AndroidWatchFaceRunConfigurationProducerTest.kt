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

import com.android.tools.idea.run.configuration.AndroidWatchFaceConfiguration
import com.android.tools.idea.run.configuration.AndroidWatchFaceConfigurationType
import com.android.tools.idea.run.configuration.AndroidWatchFaceRunConfigurationProducer
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import org.jetbrains.android.AndroidTestCase
import org.junit.Test

class AndroidWatchFaceRunConfigurationProducerTest : AndroidTestCase() {
  override fun setUp() {
    super.setUp()

    myFixture.addWearDependenciesToProject()
  }

  override fun tearDown() {
    super.tearDown()
  }

  @Test
  fun testSetupConfigurationFromContext() {
    val watchFaceFile = myFixture.addFileToProject(
      "src/com/example/myapplication/MyTestWatchFace.kt",
      """
      package com.example.myapplication

      import android.support.wearable.watchface.WatchFaceService

      /**
       * Some comment
       */
      class MyTestWatchFace : WatchFaceService() {
      }
      """.trimIndent())

    val classElement = watchFaceFile.findElementByText("class")
    val configurationFromClass = createConfigurationFromElement(classElement)

    assertEquals("MyTestWatchFace", configurationFromClass.name)
    assertEquals("com.example.myapplication.MyTestWatchFace", configurationFromClass.componentLaunchOptions.componentName)
    assertEquals(myModule, configurationFromClass.module)
  }

  @Test
  fun testJavaSetupConfigurationFromContext() {
    val watchFaceFile = myFixture.addFileToProject(
      "src/com/example/myapplication/MyWatchFaceService.java",
      """
      package com.example.myapplication;

      import android.support.wearable.watchface.WatchFaceService;
        
      public class MyWatchFaceService extends WatchFaceService {
      }
      """.trimIndent())

    val classElement = watchFaceFile.findElementByText("class")
    val configurationFromClass = createConfigurationFromElement(classElement)

    assertEquals("MyWatchFaceService", configurationFromClass.name)
    assertEquals("com.example.myapplication.MyWatchFaceService", configurationFromClass.componentLaunchOptions.componentName)
    assertEquals(myModule, configurationFromClass.module)
  }

  private fun createConfigurationFromElement(element: PsiElement): AndroidWatchFaceConfiguration {
    val context = ConfigurationContext(element)
    val runConfiguration =
      AndroidWatchFaceConfigurationType().configurationFactories[0].createTemplateConfiguration(project) as AndroidWatchFaceConfiguration
    val producer = AndroidWatchFaceRunConfigurationProducer()
    producer.setupConfigurationFromContext(runConfiguration, context, Ref(context.psiLocation))

    return runConfiguration
  }
}