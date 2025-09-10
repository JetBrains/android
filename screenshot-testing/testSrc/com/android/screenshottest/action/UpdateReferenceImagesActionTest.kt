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
package com.android.screenshottest.action

import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.onEdt
import com.android.utils.FileUtils
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.RunsInEdt
import java.io.File
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [UpdateReferenceImagesAction].
 */
class UpdateReferenceImagesActionTest {

  @get:Rule
  val projectRule = AndroidGradleProjectRule().onEdt()

  private lateinit var action: UpdateReferenceImagesAction

  @Before
  fun setup() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APP_WITH_SCREENSHOT_TEST)
    IndexingTestUtil.waitUntilIndexesAreReady(projectRule.project)
    action = UpdateReferenceImagesAction()

    // Stub annotations needed for the action's logic to resolve them.
    stubPreviewTestAnnotation()
    stubComposeAnnotation()
    stubPreviewAnnotation()
  }

  @Test
  @RunsInEdt
  fun findPreviewFunctions_findsCorrectFunctions() {
    val psiFile = createTestKtFile("""
        package com.example

        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.Preview
        import com.android.tools.screenshot.PreviewTest

        @PreviewTest
        @Preview
        @Composable
        fun MyPreview1() {}

        @PreviewTest
        @Preview
        @Composable
        fun MyPreview2() {}

        // Not a preview test
        @Composable
        fun NotAPreview() {}
    """.trimIndent())

    // After creating a new file, indexing might be triggered.
    // We need to wait for it to complete before performing any analysis.
    IndexingTestUtil.waitUntilIndexesAreReady(projectRule.project)

    val functions = DumbService.getInstance(projectRule.project).runReadActionInSmartMode<List<KtNamedFunction>> {
      action.findPreviewTestFunctions(psiFile)
    }

    assertEquals(2, functions.size)
    assertTrue(functions.any { it.name == "MyPreview1" })
    assertTrue(functions.any { it.name == "MyPreview2" })
  }

  @Test
  @RunsInEdt
  fun determineTestClassFqns_handlesClassAndTopLevel() {
    val psiFile = createTestKtFile("""
        package com.example

        class MyTestClass {
            fun previewInClass() {}
        }

        fun topLevelPreview() {}
    """.trimIndent(), fileName = "MyFile.kt")

    // Wait for indexing to complete for the newly created file.
    IndexingTestUtil.waitUntilIndexesAreReady(projectRule.project)

    val functionInClass = PsiTreeUtil.findChildrenOfType(psiFile, KtNamedFunction::class.java).first { it.name == "previewInClass" }
    val topLevelFunction = PsiTreeUtil.findChildrenOfType(psiFile, KtNamedFunction::class.java).first { it.name == "topLevelPreview" }

    val fqns = DumbService.getInstance(projectRule.project).runReadActionInSmartMode<Set<String>> {
      action.determineTestClassFqns(listOf(functionInClass, topLevelFunction))
    }

    assertEquals(2, fqns.size)
    assertTrue("Should contain FQN for the class", fqns.contains("com.example.MyTestClass"))
    assertTrue("Should contain FQN for the top-level function's file", fqns.contains("com.example.MyFileKt"))
  }

  @Test
  @RunsInEdt
  fun determineTestClassFqns_deduplicatesClasses() {
    val psiFile = createTestKtFile("""
        package com.example

        class MyTestClass {
            fun preview1() {}
            fun preview2() {}
        }
    """.trimIndent())

    // Wait for indexing to complete for the newly created file.
    IndexingTestUtil.waitUntilIndexesAreReady(projectRule.project)

    val functions = PsiTreeUtil.findChildrenOfType(psiFile, KtNamedFunction::class.java).toList()
    val fqns = DumbService.getInstance(projectRule.project).runReadActionInSmartMode<Set<String>> {
      action.determineTestClassFqns(functions)
    }

    assertEquals("Should only return one FQN for multiple functions in the same class", 1, fqns.size)
    assertEquals("com.example.MyTestClass", fqns.first())
  }

  // --- Test Helper Functions ---

  private fun createTestKtFile(content: String, fileName: String = "TestFile.kt"): KtFile {
    val file = createRelativeFileWithContent("app/src/main/java/com/example/$fileName", content)
    val virtualFile = VfsUtil.findFileByIoFile(file, true)!!
    return virtualFile.toPsiFile(projectRule.project)!! as KtFile
  }

  private fun createRelativeFileWithContent(relativePath: String, content: String): File {
    val newFile = File(projectRule.project.basePath, FileUtils.toSystemDependentPath(relativePath))
    FileUtil.createIfDoesntExist(newFile)
    newFile.writeText(content)
    return newFile
  }

  private fun stubPreviewTestAnnotation() {
    createRelativeFileWithContent(
      "app/src/main/java/com/android/tools/screenshot/PreviewTest.kt", """
        package com.android.tools.screenshot
        @Retention(AnnotationRetention.BINARY)
        @Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
        annotation class PreviewTest
      """.trimIndent())
  }

  private fun stubComposeAnnotation() {
    createRelativeFileWithContent(
      "app/src/main/java/androidx/compose/runtime/Composable.kt", """
        package androidx.compose.runtime
        @Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.TYPE_PARAMETER, AnnotationTarget.PROPERTY_GETTER)
        annotation class Composable
      """.trimIndent()
    )
  }

  private fun stubPreviewAnnotation() {
    createRelativeFileWithContent("app/src/main/java/androidx/compose/ui/tooling/preview/Preview.kt", """
        package androidx.compose.ui.tooling.preview
        @Repeatable
        annotation class Preview
      """.trimIndent()
    )
  }
}