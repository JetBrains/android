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
package com.android.tools.idea.compose.meshgradient

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshGradientEditorDialogTest : LightPlatformTestCase() {

  private val codeTemplate =
    """
    package test

    import androidx.compose.ui.geometry.Offset
    import androidx.compose.ui.graphics.Color

    fun MyMesh() {
        val gradientPainter = remember {
            MeshGradientPainter(rows = 2, columns = 3, hasBicubicColor = true) {
                setVertex(0, 0, Offset(0.0000f, 0.0000f), Color(0xFFF44336))
                setVertex(0, 1, Offset(0.3333f, 0.0000f), Color(0xFFE91E63))
                setVertex(0, 2, Offset(0.6666f, 0.0000f), Color(0xFF9C27B0))
                setVertex(0, 3, Offset(1.0000f, 0.0000f), Color(0xFF673AB7))

                setVertex(1, 0, Offset(0.0000f, 0.5000f), Color(0xFF3F51B5))
                setVertex(1, 1, Offset(0.3333f, 0.5000f), Color(0xFF2196F3))
                setVertex(1, 2, Offset(0.6666f, 0.5000f), Color(0xFF03A9F4))
                setVertex(1, 3, Offset(1.0000f, 0.5000f), Color(0xFF00BCD4))

                setVertex(2, 0, Offset(0.0000f, 1.0000f), Color(0xFF009688))
                setVertex(2, 1, Offset(0.3333f, 1.0000f), Color(0xFF4CAF50))
                setVertex(2, 2, Offset(0.6666f, 1.0000f), Color(0xFF8BC34A))
                setVertex(2, 3, Offset(1.0000f, 1.0000f), Color(0xFFCDDC39))
            }
        }
    }
    """
      .trimIndent()

  @Test
  fun testDialogInitialization() {
    val psiFactory = KtPsiFactory(project)
    val file = psiFactory.createFile("Test.kt", codeTemplate)
    val psiManager = MeshGradientPsiManager(project)

    val call = runReadActionBlocking { psiManager.findMeshPainterCall(file) }
    assertNotNull(call)

    val dialog = MeshGradientEditorDialog(project, file, call!!)

    assertEquals(3, dialog.state.rows)
    assertEquals(4, dialog.state.cols)

    val p01 = dialog.state.meshPoints[0][1]
    assertEquals(Offset(0.3333f, 0f), p01.first)
    assertEquals(Color(0xFFE91E63), p01.second)

    dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
  }

  @Test
  fun testDialogOkActionCommitsChanges() {
    val psiFactory = KtPsiFactory(project)
    val file = psiFactory.createFile("Test.kt", codeTemplate)
    val psiManager = MeshGradientPsiManager(project)

    val call = runReadActionBlocking { psiManager.findMeshPainterCall(file) }
    assertNotNull(call)

    val dialog = MeshGradientEditorDialog(project, file, call!!)

    dialog.state.updateMeshPoint(1, 1, Offset(0.4f, 0.6f))

    dialog.doOKAction()

    val updatedText = runReadActionBlocking { file.text }
    assertTrue(
      "Should contain updated offset in code",
      updatedText.contains("setVertex(1, 1, Offset(0.4000f, 0.6000f), Color(0xFF2196F3))"),
    )
  }
}
