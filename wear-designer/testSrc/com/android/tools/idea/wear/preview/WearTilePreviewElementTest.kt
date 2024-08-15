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
package com.android.tools.idea.wear.preview

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.addFileToProjectAndInvalidate
import com.android.tools.preview.PreviewConfiguration
import com.android.tools.preview.PreviewDisplaySettings
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WearTilePreviewElementTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  private lateinit var psiFile: PsiFile

  @Before
  fun setup() {
    psiFile =
      fixture.addFileToProjectAndInvalidate(
        "com/android/test/File.kt",
        // language=kotlin
        """
        package com.android.test

        import androidx.wear.tiles.tooling.preview.Preview
        import androidx.wear.tiles.tooling.preview.TilePreviewData

        @Preview
        private fun tilePreview(): TilePreviewData {
          return TilePreviewData()
        }
        """
          .trimIndent(),
      )
  }

  @Test
  fun twoPreviewElementsWithTheSameValuesShouldBeEqual() {
    val previewElement1 =
      WearTilePreviewElement(
        displaySettings =
          PreviewDisplaySettings(
            "some name",
            "some base name",
            "parameter name",
            "some group",
            false,
            false,
            "0xffabcd",
          ),
        previewElementDefinition = runReadAction { SmartPointerManager.createPointer(psiFile) },
        previewBody = runReadAction { SmartPointerManager.createPointer(psiFile.lastChild) },
        methodFqn = "someMethodFqn",
        configuration = PreviewConfiguration.cleanAndGet(device = "id:wearos_small_round"),
      )

    val previewElement2 =
      WearTilePreviewElement(
        displaySettings =
          PreviewDisplaySettings(
            "some name",
            "some base name",
            "parameter name",
            "some group",
            false,
            false,
            "0xffabcd",
          ),
        previewElementDefinition = runReadAction { SmartPointerManager.createPointer(psiFile) },
        previewBody = runReadAction { SmartPointerManager.createPointer(psiFile.lastChild) },
        methodFqn = "someMethodFqn",
        configuration = PreviewConfiguration.cleanAndGet(device = "id:wearos_small_round"),
      )

    assertEquals(previewElement1, previewElement2)
  }

  @Test
  fun testCreateDerivedInstance() {
    val originalPreviewElement =
      WearTilePreviewElement(
        displaySettings =
          PreviewDisplaySettings(
            "some name",
            "some base name",
            "parameter name",
            "some group",
            false,
            false,
            "0xffabcd",
          ),
        previewElementDefinition = runReadAction { SmartPointerManager.createPointer(psiFile) },
        previewBody = runReadAction { SmartPointerManager.createPointer(psiFile.lastChild) },
        methodFqn = "someMethodFqn",
        configuration = PreviewConfiguration.cleanAndGet(device = "id:wearos_small_round"),
      )

    val newPreviewDisplaySettings =
      PreviewDisplaySettings(
        "derived name",
        "derived base name",
        "parameter name",
        "derived group",
        true,
        true,
        "0xffffff",
      )
    val newConfig =
      PreviewConfiguration.cleanAndGet(
        device = "id:wearos_square",
        fontScale = 3f,
        locale = "fr-FR",
      )

    val derivedPreviewElement =
      originalPreviewElement.createDerivedInstance(newPreviewDisplaySettings, newConfig)

    assertEquals(newPreviewDisplaySettings, derivedPreviewElement.displaySettings)
    assertEquals(newConfig, derivedPreviewElement.configuration)
    assertEquals(originalPreviewElement.methodFqn, derivedPreviewElement.methodFqn)
    assertEquals(originalPreviewElement.instanceId, derivedPreviewElement.instanceId)
    assertEquals(
      originalPreviewElement.previewElementDefinition,
      derivedPreviewElement.previewElementDefinition,
    )
    assertEquals(originalPreviewElement.previewBody, derivedPreviewElement.previewBody)
    assertEquals(originalPreviewElement.hasAnimations, derivedPreviewElement.hasAnimations)
  }
}
