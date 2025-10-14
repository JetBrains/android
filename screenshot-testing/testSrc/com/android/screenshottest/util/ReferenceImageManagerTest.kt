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

import com.android.screenshottest.ScreenshotTestBuildSystemAdapter
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.onEdt
import com.android.utils.FileUtils
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.RunsInEdt
import java.io.File
import org.jetbrains.android.facet.AndroidFacet
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

  private lateinit var appModule: Module
  private lateinit var testFunction: KtNamedFunction
  private lateinit var tempOutputDir: File

  /** A mock build system adapter for testing. */
  private class MockBuildSystemAdapter(private val modulePath: String, private val variantName: String) : ScreenshotTestBuildSystemAdapter {
    override fun getLinkedExternalProjectPath(module: Module): String? = modulePath
    override fun getSelectedVariantName(module: Module): String? = variantName
    override fun getScreenshotTestTaskName(module: Module, command: String): String? = null
    override fun getSystemId(): ProjectSystemId = ProjectSystemId("MOCK")
    override fun createScreenshotTaskSettings(module: Module, command: String, testClassFqns: Set<String>): ExternalSystemTaskExecutionSettings? = null
  }

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

  private fun registerMockBuildSystemAdapter() {
    val modulePath = File(projectRule.project.basePath, "app").path
    val mockAdapter = MockBuildSystemAdapter(modulePath, "debug")
    ExtensionTestUtil.maskExtensions(ScreenshotTestBuildSystemAdapter.EP_NAME, listOf(mockAdapter), disposableRule.disposable)
  }

  @Test
  fun copyReferenceImages_setupFailure() {
    // 1. Arrange: Create some dummy data to copy.
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

  @Test
  fun copyReferenceImages_success_singleImage() {
    registerMockBuildSystemAdapter()
    // 1. Arrange
    val sourceImage = File(tempOutputDir, "test_image.png").apply { writeText("image content") }
    val imageData = createImageData(mapOf(sourceImage.path to "MyTestClass"))

    // 2. Act
    val failures = copyReferenceImages(appModule, listOf(imageData))

    // 3. Assert
    assertTrue("There should be no failures", failures.isEmpty())
    val expectedDestFile = File(projectRule.project.basePath, "app/src/screenshotTestDebug/reference/MyTestClass/test_image.png")
    assertTrue("Destination file should exist", expectedDestFile.exists())
    assertEquals("File content should match", "image content", expectedDestFile.readText())
  }

  @Test
  fun copyReferenceImages_success_multipleImages() {
    registerMockBuildSystemAdapter()
    // 1. Arrange
    val sourceImage1 = File(tempOutputDir, "image1.png").apply { writeText("content1") }
    val sourceImage2 = File(tempOutputDir, "image2.png").apply { writeText("content2") }
    val imageData1 = createImageData(mapOf(sourceImage1.path to "MyTestClass1"))
    val imageData2 = createImageData(mapOf(sourceImage2.path to "MyTestClass2"))

    // 2. Act
    val failures = copyReferenceImages(appModule, listOf(imageData1, imageData2))

    // 3. Assert
    assertTrue("There should be no failures", failures.isEmpty())
    val expectedDestFile1 = File(projectRule.project.basePath, "app/src/screenshotTestDebug/reference/MyTestClass1/image1.png")
    val expectedDestFile2 = File(projectRule.project.basePath, "app/src/screenshotTestDebug/reference/MyTestClass2/image2.png")
    assertTrue("Destination file 1 should exist", expectedDestFile1.exists())
    assertTrue("Destination file 2 should exist", expectedDestFile2.exists())
    assertEquals("File 1 content should match", "content1", expectedDestFile1.readText())
    assertEquals("File 2 content should match", "content2", expectedDestFile2.readText())
  }

  @Test
  fun copyReferenceImages_overwriteExistingFile() {
    registerMockBuildSystemAdapter()
    // 1. Arrange
    val expectedDestFile = File(projectRule.project.basePath, "app/src/screenshotTestDebug/reference/MyTestClass/image.png")
    expectedDestFile.parentFile.mkdirs()
    expectedDestFile.writeText("old content")

    val sourceImage = File(tempOutputDir, "image.png").apply { writeText("new content") }
    val imageData = createImageData(mapOf(sourceImage.path to "MyTestClass"))

    // 2. Act
    val failures = copyReferenceImages(appModule, listOf(imageData))

    // 3. Assert
    assertTrue("There should be no failures", failures.isEmpty())
    assertTrue("Destination file should exist", expectedDestFile.exists())
    assertEquals("File content should be overwritten", "new content", expectedDestFile.readText())
  }

  @Test
  fun copyReferenceImages_sourceFileMissing() {
    registerMockBuildSystemAdapter()
    // 1. Arrange
    val missingImagePath = File(tempOutputDir, "missing_image.png").path
    val imageData = createImageData(mapOf(missingImagePath to "MyTestClass"))

    // 2. Act
    var failures: List<ImageData> = emptyList()
    LoggedErrorProcessor.executeAndReturnLoggedError {
      failures = copyReferenceImages(appModule, listOf(imageData))
    }

    // 3. Assert
    assertEquals("There should be one failure", 1, failures.size)
    assertEquals("The failed item should be the input item", imageData, failures.first())

    val expectedDestFile = File(projectRule.project.basePath, "app/src/screenshotTestDebug/reference/MyTestClass/missing_image.png")
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
}