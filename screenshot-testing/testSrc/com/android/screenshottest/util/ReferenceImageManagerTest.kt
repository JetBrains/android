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

import com.android.screenshottest.ui.PreviewDetails
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.onEdt
import com.android.utils.FileUtils
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.RunsInEdt
import java.io.File
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Unit tests for [ReferenceImageManager].
 */
@RunsInEdt
class ReferenceImageManagerTest {

  private val projectRule = AndroidGradleProjectRule().onEdt()
  private val disposableRule = DisposableRule()

  @get:Rule
  val rules: RuleChain = RuleChain
    .outerRule(projectRule)
    .around(disposableRule)

  private lateinit var testFunction: KtNamedFunction
  private lateinit var tempOutputDir: File


  @Before
  fun setup() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APP_WITH_SCREENSHOT_TEST)
    IndexingTestUtil.waitUntilIndexesAreReady(projectRule.project)

    // Create a dummy function to use for PreviewDetails, which is required by ImageData.
    val psiFile = createTestFile("app/src/main/java/com/example/test/DummyFile.kt", """
        package com.example.test
        fun myTestFunction() {}
    """.trimIndent())
    testFunction = PsiTreeUtil.findChildrenOfType(psiFile, KtNamedFunction::class.java).first()
    tempOutputDir = File(projectRule.project.basePath, "tmp/outputs").apply { mkdirs() }
  }

  @Test
  fun copyReferenceImages_success_singleImage() {
    // 1. Arrange
    val sourceImage = File(tempOutputDir, "test_image.png").apply { writeText("image content") }
    val expectedDestFile = File(projectRule.project.basePath, "app/src/screenshotTestDebug/reference/MyTestClass/test_image.png")
    val imageData = createImageData(mapOf(sourceImage.path to "MyTestClass"), expectedDestFile.path)

    // 2. Act
    val failures = copyReferenceImages(listOf(imageData))

    // 3. Assert
    assertTrue("There should be no failures", failures.isEmpty())
    assertTrue("Destination file should exist", expectedDestFile.exists())
    assertEquals("File content should match", "image content", expectedDestFile.readText())
  }

  @Test
  fun copyReferenceImages_success_multipleImages() {
    // 1. Arrange
    val sourceImage1 = File(tempOutputDir, "image1.png").apply { writeText("content1") }
    val sourceImage2 = File(tempOutputDir, "image2.png").apply { writeText("content2") }
    val expectedDestFile1 = File(projectRule.project.basePath, "app/src/screenshotTestDebug/reference/MyTestClass1/image1.png")
    val expectedDestFile2 = File(projectRule.project.basePath, "app/src/screenshotTestDebug/reference/MyTestClass2/image2.png")
    val imageData1 = createImageData(mapOf(sourceImage1.path to "MyTestClass1"), expectedDestFile1.path)
    val imageData2 = createImageData(mapOf(sourceImage2.path to "MyTestClass2"), expectedDestFile2.path)

    // 2. Act
    val failures = copyReferenceImages(listOf(imageData1, imageData2))

    // 3. Assert
    assertTrue("There should be no failures", failures.isEmpty())
    assertTrue("Destination file 1 should exist", expectedDestFile1.exists())
    assertTrue("Destination file 2 should exist", expectedDestFile2.exists())
    assertEquals("File 1 content should match", "content1", expectedDestFile1.readText())
    assertEquals("File 2 content should match", "content2", expectedDestFile2.readText())
  }

  @Test
  fun copyReferenceImages_overwriteExistingFile() {
    // 1. Arrange
    val expectedDestFile = File(projectRule.project.basePath, "app/src/screenshotTestDebug/reference/MyTestClass/image.png")
    expectedDestFile.parentFile.mkdirs()
    expectedDestFile.writeText("old content")

    val sourceImage = File(tempOutputDir, "image.png").apply { writeText("new content") }
    val imageData = createImageData(mapOf(sourceImage.path to "MyTestClass"), expectedDestFile.path)

    // 2. Act
    val failures = copyReferenceImages(listOf(imageData))

    // 3. Assert
    assertTrue("There should be no failures", failures.isEmpty())
    assertTrue("Destination file should exist", expectedDestFile.exists())
    assertEquals("File content should be overwritten", "new content", expectedDestFile.readText())
  }

  @Test
  fun copyReferenceImages_sourceFileMissing() {
    // 1. Arrange
    val missingImagePath = File(tempOutputDir, "missing_image.png").path
    val expectedDestFile = File(projectRule.project.basePath, "app/src/screenshotTestDebug/reference/MyTestClass/missing_image.png")
    val imageData = createImageData(mapOf(missingImagePath to "MyTestClass"), expectedDestFile.path)

    // 2. Act
    var failures: List<ImageData> = emptyList()
    LoggedErrorProcessor.executeAndReturnLoggedError {
      failures = copyReferenceImages(listOf(imageData))
    }

    // 3. Assert
    assertEquals("There should be one failure", 1, failures.size)
    assertEquals("The failed item should be the input item", imageData, failures.first())

    assertFalse("Destination file should NOT exist", expectedDestFile.exists())
  }

  /** Creates a test data object with the given image paths. */
  private fun createImageData(imagePaths: Map<String, String>, destImagePath: String): ImageData {
    val details = PreviewDetails("", "", "", "", destImagePath = destImagePath)
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
}