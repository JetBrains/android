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

import com.android.tools.idea.run.configuration.AndroidTileConfiguration
import com.android.tools.idea.run.configuration.AndroidTileConfigurationType
import com.android.tools.idea.run.configuration.AndroidTileRunConfigurationProducer
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import org.jetbrains.android.AndroidTestCase
import org.junit.Test

class AndroidTileRunConfigurationProducerTest : AndroidTestCase() {
  override fun setUp() {
    super.setUp()

    myFixture.addWearDependenciesToProject()
  }

  override fun tearDown() {
    super.tearDown()
  }

  @Test
  fun testSetupConfigurationFromContext() {
    val tileFile = myFixture.addFileToProject(
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
    assertEquals(myModule, configurationFromClass.module)
  }

  @Test
  fun testJavaSetupConfigurationFromContext() {
    val tileFile = myFixture.addFileToProject(
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
    assertEquals(myModule, configurationFromClass.module)
  }

  private fun createConfigurationFromElement(element: PsiElement): AndroidTileConfiguration {
    val context = ConfigurationContext(element)
    val runConfiguration =
      AndroidTileConfigurationType().configurationFactories[0].createTemplateConfiguration(project) as AndroidTileConfiguration
    val producer = AndroidTileRunConfigurationProducer()
    producer.setupConfigurationFromContext(runConfiguration, context, Ref(context.psiLocation))

    return runConfiguration
  }
}