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
import com.android.tools.idea.run.configuration.AndroidWatchFaceRunMarkerContributor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType

class AndroidWatchFaceRunLineMarkerContributorTest : AndroidTestCase() {
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

  fun testGetInfo() {
    val contributor = AndroidWatchFaceRunMarkerContributor()
    val classElement = watchFaceFile.findDescendantOfType<PsiElement> { it.node.text == "class" }!!
    val packageElement = watchFaceFile.findDescendantOfType<PsiElement> { it.node.text == "package com.example.myapplication" }!!

    assertNotNull(contributor.getInfo(classElement))
    assertNull(contributor.getInfo(packageElement))
  }
}