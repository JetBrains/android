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
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshGradientPsiManagerTest : LightPlatformTestCase() {

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
                setVertex(1, 0, Offset(0.0000f, 0.5000f), Color(0xFF3F51B5))
                setVertex(1, 1, Offset(0.3333f, 0.5000f), Color(0xFF2196F3))
            }
        }
    }
    """
      .trimIndent()

  private val robustCodeTemplate =
    """
    package test

    import androidx.compose.ui.geometry.Offset
    import androidx.compose.ui.graphics.Color

    fun MyMesh() {
        val gradientPainter = remember {
            MeshGradientPainter(2, 3) {
                setVertex(0, 0, Offset(0.1f, 0.2f), Color(0xFF123456.toInt()))
                setVertex(0, 1, Offset(x = 0.3f, y = 0.4f), Color(1f, 0.5f, 0.2f))
                setVertex(1, 0, Offset(0.5f, 0.6f), Color(255, 128, 64, 255))
                setVertex(1, 1, Offset(0.7f, 0.8f), Color.Green)
            }
        }
    }
    """
      .trimIndent()

  private val aliasCodeTemplate =
    """
    package test

    import androidx.compose.ui.geometry.Offset
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.graphics.MeshGradientPainter as CustomMeshPainter

    fun MyMesh() {
        val gradientPainter = remember {
            CustomMeshPainter(rows = 2, columns = 3, hasBicubicColor = true) {
                setVertex(0, 0, Offset(0.0f, 0.0f), Color.Red)
            }
        }
    }
    """
      .trimIndent()

  @Test
  fun testParseMesh() {
    val psiFactory = KtPsiFactory(project)
    val file = psiFactory.createFile("Test.kt", codeTemplate)
    val psiManager = MeshGradientPsiManager(project)

    runReadActionBlocking {
      val call = psiManager.findMeshPainterCall(file)
      assertNotNull("Should find MeshGradientPainter call", call)

      val parsedMesh = psiManager.parseMesh(call!!)
      assertNotNull("Should parse mesh successfully", parsedMesh)

      assertEquals(3, parsedMesh!!.rows)
      assertEquals(4, parsedMesh.cols)
      assertEquals(4, parsedMesh.vertices.size)

      val v01 = parsedMesh.vertices.first { it.row == 0 && it.col == 1 }
      assertEquals(Offset(0.3333f, 0f), v01.offset)
      assertEquals(Color(0xFFE91E63), v01.color)
    }
  }

  @Test
  fun testFindMeshPainterCallWithImportAlias() {
    val psiFactory = KtPsiFactory(project)
    val file = psiFactory.createFile("Test.kt", aliasCodeTemplate)
    val psiManager = MeshGradientPsiManager(project)

    val call = runReadActionBlocking { psiManager.findMeshPainterCall(file) }
    assertNotNull("Should find call even when using import alias", call)

    val resolvedText = runReadActionBlocking { call!!.calleeExpression?.text }
    assertEquals("CustomMeshPainter", resolvedText)
  }

  @Test
  fun testRobustParsing() {
    val psiFactory = KtPsiFactory(project)
    val file = psiFactory.createFile("Test.kt", robustCodeTemplate)
    val psiManager = MeshGradientPsiManager(project)

    runReadActionBlocking {
      val call = psiManager.findMeshPainterCall(file)
      assertNotNull("Should find MeshGradientPainter call", call)

      val parsedMesh = psiManager.parseMesh(call!!)
      assertNotNull("Should parse mesh successfully", parsedMesh)

      assertEquals(3, parsedMesh!!.rows)
      assertEquals(4, parsedMesh.cols)
      assertEquals(4, parsedMesh.vertices.size)

      val v00 = parsedMesh.vertices.first { it.row == 0 && it.col == 0 }
      assertEquals(Offset(0.1f, 0.2f), v00.offset)
      assertEquals(Color(0xFF123456), v00.color)

      val v01 = parsedMesh.vertices.first { it.row == 0 && it.col == 1 }
      assertEquals(Offset(0.3f, 0.4f), v01.offset)
      assertEquals(Color(1f, 0.5f, 0.2f, 1f), v01.color)

      val v10 = parsedMesh.vertices.first { it.row == 1 && it.col == 0 }
      assertEquals(Offset(0.5f, 0.6f), v10.offset)
      assertEquals(Color(255, 128, 64, 255), v10.color)

      val v11 = parsedMesh.vertices.first { it.row == 1 && it.col == 1 }
      assertEquals(Offset(0.7f, 0.8f), v11.offset)
      assertEquals(Color.Green, v11.color)
    }
  }

  @Test
  fun testUpdateVertexColor() {
    val psiFactory = KtPsiFactory(project)
    val file = psiFactory.createFile("Test.kt", codeTemplate)
    val psiManager = MeshGradientPsiManager(project)

    WriteCommandAction.runWriteCommandAction(project) {
      val call = psiManager.findMeshPainterCall(file)
      assertNotNull(call)
      val success = psiManager.updateVertexColor(call!!, 0, 1, Color(0xFF2196F3))
      assertTrue("Should update color successfully", success)
    }

    val updatedText = runReadActionBlocking { file.text }
    assertTrue("Should contain updated color in code", updatedText.contains("setVertex(0, 1, Offset(0.3333f, 0.0000f), Color(0xFF2196F3))"))
  }

  @Test
  fun testUpdateVertexOffset() {
    val psiFactory = KtPsiFactory(project)
    val file = psiFactory.createFile("Test.kt", codeTemplate)
    val psiManager = MeshGradientPsiManager(project)

    WriteCommandAction.runWriteCommandAction(project) {
      val call = psiManager.findMeshPainterCall(file)
      assertNotNull(call)
      val success = psiManager.updateVertexOffset(call!!, 1, 0, Offset(0.1f, 0.6f))
      assertTrue("Should update offset successfully", success)
    }

    val updatedText = runReadActionBlocking { file.text }
    assertTrue(
      "Should contain updated offset in code",
      updatedText.contains("setVertex(1, 0, Offset(0.1000f, 0.6000f), Color(0xFF3F51B5))"),
    )
  }
}
