/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.screenshottest.util

import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.onEdt
import com.android.utils.FileUtils
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.RunsInEdt
import java.io.File
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [TestIdentifier].
 */
class TestIdentifierTest {

  @get:Rule
  val projectRule = AndroidGradleProjectRule().onEdt()

  @Before
  fun setup() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APP_WITH_SCREENSHOT_TEST)
    // Wait for indexing to avoid race conditions when resolving PSI elements.
    IndexingTestUtil.waitUntilIndexesAreReady(projectRule.project)
    stubComposeAnnotation()
    stubPreviewAnnotation()
    stubPreviewParameterAnnotation()
  }

  @Test
  @RunsInEdt
  fun getIdentifier_forFunctionInClass() {
    val file = createTestFile("""
        package com.example.test
        import androidx.compose.ui.tooling.preview.Preview

        class MyTest {
            @Preview
            fun MyPreview() {}
        }
    """.trimIndent())

    val function = file.findFunction("MyPreview")
    val annotation = function.annotationEntries.first()
    val details = PreviewDetails(function, annotation, listOf(annotation), null)

    assertEquals("MyTest.MyPreview", getIdentifier(details))
  }

  @Test
  @RunsInEdt
  fun getIdentifier_forTopLevelFunction() {
    val file = createTestFile("""
        package com.example.test
        import androidx.compose.ui.tooling.preview.Preview

        @Preview
        fun MyPreview() {}
    """.trimIndent(), fileName = "TopLevelPreviews.kt")

    val function = file.findFunction("MyPreview")
    val annotation = function.annotationEntries.first()
    val details = PreviewDetails(function, annotation, listOf(annotation), null)

    // For top-level functions, the class name is derived from the file name.
    assertEquals("TopLevelPreviewsKt.MyPreview", getIdentifier(details))
  }

  @Test
  @RunsInEdt
  fun getIdentifier_withNoAnnotation() {
    val file = createTestFile("""
        package com.example.test
        class MyTest {
            fun MyPreview() {}
        }
    """.trimIndent())

    val function = file.findFunction("MyPreview")
    val details = PreviewDetails(function, null, emptyList(), null)

    assertEquals("MyTest.MyPreview", getIdentifier(details))
  }

  @Test
  @RunsInEdt
  fun getIdentifier_forPreviewParameter() {
    val file = createTestFile("""
        package com.example.test
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.PreviewParameter

        class MyProvider
        class MyTest {
            @Composable
            fun MyPreview(@PreviewParameter(MyProvider::class) s: String) {}
        }
    """.trimIndent())

    val function = file.findFunction("MyPreview")
    val details = PreviewDetails(function, null, emptyList(), null)

    assertEquals("MyTest.MyPreview_[{provider=MyProvider}]_", getIdentifier(details))
  }

  private fun PsiFile.findFunction(name: String): KtNamedFunction {
    return PsiTreeUtil.findChildrenOfType(this, KtNamedFunction::class.java).first { it.name == name }
  }

  private fun createTestFile(content: String, fileName: String = "TestFile.kt"): PsiFile {
    val file = createRelativeFileWithContent("app/src/main/java/com/example/test/$fileName", content)
    val virtualFile = VfsUtil.findFileByIoFile(file, true)!!
    return virtualFile.toPsiFile(projectRule.project)!!
  }

  private fun createRelativeFileWithContent(relativePath: String, content: String): File {
    val newFile = File(projectRule.project.basePath, FileUtils.toSystemDependentPath(relativePath))
    FileUtil.createIfDoesntExist(newFile)
    newFile.writeText(content)
    return newFile
  }

  private fun stubComposeAnnotation() {
    createRelativeFileWithContent(
      "app/src/main/java/androidx/compose/runtime/Composable.kt", """
        package androidx.compose.runtime
        annotation class Composable
      """.trimIndent()
    )
  }

  private fun stubPreviewAnnotation() {
    createRelativeFileWithContent("app/src/main/java/androidx/compose/ui/tooling/preview/Preview.kt", """
        package androidx.compose.ui.tooling.preview
        @Repeatable
        annotation class Preview(
          val name: String = "",
          val apiLevel: Int = -1,
          val device: String = "",
          val fontScale: Float = 1f
        )
      """.trimIndent()
    )
  }

  private fun stubPreviewParameterAnnotation() {
    createRelativeFileWithContent("app/src/main/java/androidx/compose/ui/tooling/preview/PreviewParameter.kt", """
        package androidx.compose.ui.tooling.preview
        import kotlin.reflect.KClass
        annotation class PreviewParameter(val provider: KClass<*>)
      """.trimIndent()
    )
  }
}