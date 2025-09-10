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
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class PreviewDetailsUtilTest {

  @get:Rule
  val projectRule = AndroidGradleProjectRule().onEdt()

  @Before
  fun setup() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APP_WITH_SCREENSHOT_TEST)
    stubComposeAnnotation()
    stubPreviewAnnotation()
  }

  @Test
  @RunsInEdt
  fun displayName_noAnnotation() {
    val file = createTestFile("""
        import androidx.compose.runtime.Composable

        @Composable
        fun MyTestPreview() {}
    """.trimIndent())

    val function = file.findFunction("MyTestPreview")
    val details = PreviewDetails(function, null, emptyList(), null)

    assertEquals("MyTestPreview", details.displayName)
  }

  @Test
  @RunsInEdt
  fun getProviderClassName_findsProvider() {
    val file = createTestFile("""
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.PreviewParameter
        import kotlin.reflect.KClass

        interface PreviewParameterProvider<T>
        class MyProvider : PreviewParameterProvider<String>

        @Composable
        fun MyTestPreview(@PreviewParameter(MyProvider::class) text: String) {}
    """.trimIndent())

    val function = file.findFunction("MyTestPreview")
    val providerName = getProviderClassName(function)

    assertEquals("MyProvider", providerName)
  }

  @Test
  @RunsInEdt
  fun getProviderClassName_noProvider() {
    val file = createTestFile("""
        import androidx.compose.runtime.Composable

        @Composable
        fun MyTestPreview(text: String) {}
    """.trimIndent())

    val function = file.findFunction("MyTestPreview")
    val providerName = getProviderClassName(function)

    assertEquals(null, providerName)
  }

  private fun PsiFile.findFunction(name: String): KtNamedFunction {
    return PsiTreeUtil.findChildrenOfType(this, KtNamedFunction::class.java).first { it.name == name }
  }

  private fun createTestFile(content: String): PsiFile {
    val file = createRelativeFilewithContent("app/src/main/java/com/example/test/TestFile.kt", content)
    val virtualFile = VfsUtil.findFileByIoFile(file, true)!!
    return virtualFile.toPsiFile(projectRule.project)!!
  }

  private fun stubComposeAnnotation() {
    createRelativeFilewithContent(
      "app/src/main/java/androidx/compose/runtime/Composable.kt", """
        package androidx.compose.runtime
        @Target(AnnotationTarget.FUNCTION)
        annotation class Composable
        """.trimIndent()
    )
  }

  private fun stubPreviewAnnotation() {
    createRelativeFilewithContent("app/src/main/java/androidx/compose/ui/tooling/preview/Preview.kt", """
        package androidx.compose.ui.tooling.preview
        import kotlin.reflect.KClass

        @Repeatable
        annotation class Preview(
          val name: String = "",
          val apiLevel: Int = -1,
          val device: String = "",
          val fontScale: Float = 1f
        )

        annotation class PreviewParameter(
            val provider: KClass<out PreviewParameterProvider<*>>
        )
        """.trimIndent()
    )
  }

  private fun createRelativeFilewithContent(relativePath: String, content: String): File {
    val newFile = File(
      projectRule.project.basePath,
      FileUtils.toSystemDependentPath(relativePath)
    )
    FileUtil.createIfDoesntExist(newFile)
    newFile.writeText(content)
    return newFile
  }
}