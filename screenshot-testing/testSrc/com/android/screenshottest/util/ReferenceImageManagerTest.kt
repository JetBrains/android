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
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.RunsInEdt
import java.io.File
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [ReferenceImageManager].
 */
@RunsInEdt
class ReferenceImageManagerTest {

  @get:Rule
  val projectRule = AndroidGradleProjectRule().onEdt()

  private lateinit var appModule: Module
  private lateinit var testFunction: KtNamedFunction
  private lateinit var tempOutputDir: File

  @Before
  fun setup() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APP_WITH_SCREENSHOT_TEST)
    IndexingTestUtil.waitUntilIndexesAreReady(projectRule.project)
    appModule = ModuleManager.getInstance(projectRule.project).modules.first { AndroidFacet.getInstance(it) != null }

    // Create a dummy function to use for PreviewDetails, which is required by ImageData.
    val psiFile = createTestFile("app/src/main/java/com/example/test/DummyFile.kt", """
        package com.example.test
        fun myTestFunction() {}
    """.trimIndent())
    testFunction = PsiTreeUtil.findChildrenOfType(psiFile, KtNamedFunction::class.java).first()
    tempOutputDir = File(projectRule.project.basePath, "tmp/outputs").apply { mkdirs() }
  }

  @Test
  fun copyReferenceImages_setupFailure() {
    // 1. Arrange: Do NOT register a mock extension point.
    // Create some dummy data to copy.
    val sourceImage = File(tempOutputDir, "test_image.png").apply { writeText("image content") }
    val imageData = createImageData(mapOf(sourceImage.path to "MyTestClass"))

    // 2. Act: Run the copy operation and capture logged errors.
    var failures: List<ImageData> = emptyList()
    LoggedErrorProcessor.executeAndReturnLoggedError {
      failures = copyReferenceImages(appModule, listOf(imageData))
    }

    // 3. Assert: Verify that the operation failed for all items because setup failed.
    assertEquals("There should be one failure", 1, failures.size)
    assertEquals("The failed item should be the input item", imageData, failures.first())

    // 4. Assert that no file was copied.
    val expectedDestFile = File(projectRule.project.basePath, "app/src/screenshotTestDebug/reference/MyTestClass/test_image.png")
    assertFalse("Destination file should NOT exist", expectedDestFile.exists())
  }

  /** Creates a test data object with the given image paths. */
  private fun createImageData(imagePaths: Map<String, String>): ImageData {
    val details = PreviewDetails(testFunction, null, emptyList(), null)
    return ImageData(details, imagePaths)
  }

  /** Creates a file with content at a given path relative to the project root. */
  private fun createTestFile(relativePath: String, content: String): PsiFile {
    val file = createRelativeFilewithContent(relativePath, content)
    val virtualFile = VfsUtil.findFileByIoFile(file, true)!!
    return virtualFile.toPsiFile(projectRule.project)!!
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

  //TODO(b/446134884): Add more test cases
}