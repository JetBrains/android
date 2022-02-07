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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.configuration.AndroidWatchFaceConfiguration
import com.android.tools.idea.run.configuration.AndroidWatchFaceConfigurationType
import com.android.tools.idea.run.configuration.AndroidWatchFaceRunConfigurationProducer
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType

class AndroidWatchFaceRunConfigurationProducerTest : AndroidTestCase() {
  private lateinit var watchFaceFile: PsiFile

  override fun setUp() {
    super.setUp()
    StudioFlags.ALLOW_RUN_WEAR_CONFIGURATIONS_FROM_GUTTER.override(true)

    myFixture.addFileToProject(
      "src/android/support/wearable/watchface/WatchFaceService.kt",
      // language=kotlin - Simulates that 'com.google.android.support:wearable:xxx' was added to `build.gradle`
      """
      package android.support.wearable.watchface

      open class WatchFaceService
      """.trimIndent())

    watchFaceFile = myFixture.addFileToProject(
      "src/com/example/myapplication/MyTestWatchFace.kt",
      // language=kotlin
      """
      package com.example.myapplication

      import android.support.wearable.watchface.WatchFaceService

      /**
       * Some comment
       */
      class MyTestWatchFace : WatchFaceService() {
      }
      """.trimIndent())
  }

  override fun tearDown() {
    super.tearDown()
    StudioFlags.ALLOW_RUN_WEAR_CONFIGURATIONS_FROM_GUTTER.clearOverride()
  }

  fun testSetupConfigurationFromContext() {
    val classElement = watchFaceFile.findDescendantOfType<PsiElement> { it.node.text == "class" }!!
    val configurationFromClass = createConfigurationFromElement(classElement)

    println(configurationFromClass)

    assertEquals("MyTestWatchFace", configurationFromClass.name)
    assertEquals("com.example.myapplication.MyTestWatchFace", configurationFromClass.componentName)
    assertEquals(myModule, configurationFromClass.module)
  }

  private fun createConfigurationFromElement(element: PsiElement): AndroidWatchFaceConfiguration {
    val context = ConfigurationContext(element)
    val runConfiguration = newAndroidWatchFaceConfiguration()
    val producer = AndroidWatchFaceRunConfigurationProducer()
    producer.setupConfigurationFromContext(runConfiguration, context, Ref(context.psiLocation))

    return runConfiguration
  }

  private fun newAndroidWatchFaceConfiguration(): AndroidWatchFaceConfiguration =
    AndroidWatchFaceConfigurationType().configurationFactories[0].createTemplateConfiguration(project) as AndroidWatchFaceConfiguration
}