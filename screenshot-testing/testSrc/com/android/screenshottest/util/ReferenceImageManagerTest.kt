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
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.RunsInEdt
import java.io.File
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class ReferenceImageManagerTest {

  @get:Rule val projectRule = AndroidProjectRule.withAndroidModel().onEdt()

  private lateinit var tempOutputDir: File

  @Before
  fun setUp() {
    tempOutputDir = File(projectRule.project.basePath, "build/tmp/outputs").canonicalFile.apply { mkdirs() }
  }

  @After fun tearDown() {}

  @Test
  fun copyReferenceImages_success() {
    // 1. Arrange
    val sourceImage = File(tempOutputDir, "image.png").canonicalFile.apply { writeText("image content") }
    val expectedDestFile = File(projectRule.project.basePath, "app/src/screenshotTestDebug/reference/MyTestClass/image.png").canonicalFile
    val imageData = createImageData(mapOf(sourceImage.canonicalPath to "MyTestClass"), expectedDestFile.canonicalPath)

    // 2. Act
    val failures = copyReferenceImages(listOf(imageData), projectRule.project.basePath!!)

    // 3. Assert
    assertTrue("There should be no failures", failures.isEmpty())
    assertTrue("Destination file should exist", expectedDestFile.exists())
    assertEquals("File content should match", "image content", expectedDestFile.readText())
  }

  @Test
  fun copyReferenceImages_success_multipleImages() {
    // 1. Arrange
    val sourceImage1 = File(tempOutputDir, "image1.png").canonicalFile.apply { writeText("content1") }
    val sourceImage2 = File(tempOutputDir, "image2.png").canonicalFile.apply { writeText("content2") }
    val expectedDestFile1 =
      File(projectRule.project.basePath, "app/src/screenshotTestDebug/reference/MyTestClass1/image1.png").canonicalFile
    val expectedDestFile2 =
      File(projectRule.project.basePath, "app/src/screenshotTestDebug/reference/MyTestClass2/image2.png").canonicalFile
    val imageData1 = createImageData(mapOf(sourceImage1.canonicalPath to "MyTestClass1"), expectedDestFile1.canonicalPath)
    val imageData2 = createImageData(mapOf(sourceImage2.canonicalPath to "MyTestClass2"), expectedDestFile2.canonicalPath)

    // 2. Act
    val failures = copyReferenceImages(listOf(imageData1, imageData2), projectRule.project.basePath!!)

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
    val expectedDestFile = File(projectRule.project.basePath, "app/src/screenshotTestDebug/reference/MyTestClass/image.png").canonicalFile
    expectedDestFile.parentFile.mkdirs()
    expectedDestFile.writeText("old content")

    val sourceImage = File(tempOutputDir, "image.png").canonicalFile.apply { writeText("new content") }
    val imageData = createImageData(mapOf(sourceImage.canonicalPath to "MyTestClass"), expectedDestFile.canonicalPath)

    // 2. Act
    val failures = copyReferenceImages(listOf(imageData), projectRule.project.basePath!!)

    // 3. Assert
    assertTrue("There should be no failures", failures.isEmpty())
    assertTrue("Destination file should exist", expectedDestFile.exists())
    assertEquals("File content should be overwritten", "new content", expectedDestFile.readText())
  }

  @Test
  fun copyReferenceImages_sourceFileMissing() {
    // 1. Arrange
    val missingImagePath = File(tempOutputDir, "missing_image.png").canonicalPath
    val expectedDestFile =
      File(projectRule.project.basePath, "app/src/screenshotTestDebug/reference/MyTestClass/missing_image.png").canonicalFile
    val imageData = createImageData(mapOf(missingImagePath to "MyTestClass"), expectedDestFile.canonicalPath)

    // 2. Act
    var failures: List<ImageData> = emptyList()
    LoggedErrorProcessor.executeAndReturnLoggedError { failures = copyReferenceImages(listOf(imageData), projectRule.project.basePath!!) }

    // 3. Assert
    assertEquals("There should be one failure", 1, failures.size)
    assertEquals("The failed item should be the input item", imageData, failures.first())

    assertFalse("Destination file should NOT exist", expectedDestFile.exists())
  }

  @Test
  fun copyReferenceImages_rejectOutOfBoundsDestination() {
    // 1. Arrange
    val sourceImage = File(tempOutputDir, "image.png").apply { writeText("payload") }
    val outOfBoundsDest = File(projectRule.project.basePath).parentFile.resolve("unauthorized_file.png")
    val imageData = createImageData(mapOf(sourceImage.path to "MyTestClass"), outOfBoundsDest.path)

    // 2. Act
    var failures: List<ImageData> = emptyList()
    LoggedErrorProcessor.executeAndReturnLoggedError { failures = copyReferenceImages(listOf(imageData), projectRule.project.basePath!!) }

    // 3. Assert
    assertEquals("There should be one failure", 1, failures.size)
    assertFalse("File should NOT have been copied out of bounds", outOfBoundsDest.exists())
  }

  @Test
  fun copyReferenceImages_rejectInvalidImageExtension() {
    // 1. Arrange
    val sourceImage = File(tempOutputDir, "image.png").apply { writeText("payload") }
    val expectedDestFile = File(projectRule.project.basePath, "app/src/screenshotTestDebug/reference/MyTestClass/unauthorized.sh")
    val imageData = createImageData(mapOf(sourceImage.path to "MyTestClass"), expectedDestFile.path)

    // 2. Act
    var failures: List<ImageData> = emptyList()
    LoggedErrorProcessor.executeAndReturnLoggedError { failures = copyReferenceImages(listOf(imageData), projectRule.project.basePath!!) }

    // 3. Assert
    assertEquals("There should be one failure", 1, failures.size)
    assertFalse("File with non-image extension should NOT be written", expectedDestFile.exists())
  }

  @Test
  fun copyReferenceImages_success_jpgImage() {
    // 1. Arrange
    val sourceImage = File(tempOutputDir, "test_image.jpg").apply { writeText("jpg content") }
    val expectedDestFile = File(projectRule.project.basePath, "app/src/screenshotTestDebug/reference/MyTestClass/test_image.jpg")
    val imageData = createImageData(mapOf(sourceImage.path to "MyTestClass"), expectedDestFile.path)

    // 2. Act
    val failures = copyReferenceImages(listOf(imageData), projectRule.project.basePath!!)

    // 3. Assert
    assertTrue("There should be no failures", failures.isEmpty())
    assertTrue("Destination file should exist", expectedDestFile.exists())
    assertEquals("File content should match", "jpg content", expectedDestFile.readText())
  }

  @Test
  fun copyReferenceImages_rejectNetworkSourceAndDestination() {
    // 1. Arrange
    val networkSource = "\\\\attacker\\share\\payload.png"
    val expectedDestFile = File(projectRule.project.basePath, "app/src/screenshotTestDebug/reference/MyTestClass/image.png")
    val imageData = createImageData(mapOf(networkSource to "MyTestClass"), expectedDestFile.path)

    // 2. Act
    var failures: List<ImageData> = emptyList()
    LoggedErrorProcessor.executeAndReturnLoggedError { failures = copyReferenceImages(listOf(imageData), projectRule.project.basePath!!) }

    // 3. Assert
    assertEquals("There should be one failure", 1, failures.size)
    assertFalse("Destination file should NOT exist", expectedDestFile.exists())
  }

  @Test
  fun copyReferenceImages_securityViolation_destOutsideReference() {
    // 1. Arrange
    val sourceImage = File(tempOutputDir, "image.png").canonicalFile.apply { writeText("content") }
    // Destination is inside the project, but not in the screenshotTest/reference path
    val expectedDestFile = File(projectRule.project.basePath, "app/src/main/java/com/example/test/DummyFile.kt").canonicalFile
    val imageData = createImageData(mapOf(sourceImage.canonicalPath to "MyTestClass"), expectedDestFile.canonicalPath)

    // 2. Act
    var failures: List<ImageData> = emptyList()
    LoggedErrorProcessor.executeAndReturnLoggedError { failures = copyReferenceImages(listOf(imageData), projectRule.project.basePath!!) }

    // 3. Assert
    assertEquals("Should be considered a failure due to security check", 1, failures.size)
  }

  @Test
  fun copyReferenceImages_securityViolation_srcOutsideProjectAndSystemTemp() {
    // 1. Arrange
    // Source file is outside both project base path and system temp dir on any OS
    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    val sourceImage = File(if (isWindows) "Z:\\unauthorized_outside\\outside_source.png" else "/unauthorized_outside/outside_source.png")
    val expectedDestFile = File(projectRule.project.basePath, "app/src/screenshotTestDebug/reference/MyTestClass/image.png").canonicalFile
    val imageData = createImageData(mapOf(sourceImage.path to "MyTestClass"), expectedDestFile.canonicalPath)

    // 2. Act
    var failures: List<ImageData> = emptyList()
    LoggedErrorProcessor.executeAndReturnLoggedError { failures = copyReferenceImages(listOf(imageData), projectRule.project.basePath!!) }

    // 3. Assert
    assertEquals("Should fail because source is outside project and system temp", 1, failures.size)
  }

  @Test
  fun copyReferenceImages_success_sourceInSystemTempDir() {
    // 1. Arrange
    val systemTempDir = File(System.getProperty("java.io.tmpdir")).canonicalFile
    val sourceImage = File(systemTempDir, "temp_source_image.png").canonicalFile
    sourceImage.writeText("temp image content")

    val expectedDestFile =
      File(projectRule.project.basePath, "app/src/screenshotTestDebug/reference/MyTestClass/temp_source_image.png").canonicalFile
    val imageData = createImageData(mapOf(sourceImage.canonicalPath to "MyTestClass"), expectedDestFile.canonicalPath)

    try {
      // 2. Act
      val failures = copyReferenceImages(listOf(imageData), projectRule.project.basePath!!)

      // 3. Assert
      assertTrue("There should be no failures", failures.isEmpty())
      assertTrue("Destination file should exist", expectedDestFile.exists())
      assertEquals("File content should match", "temp image content", expectedDestFile.readText())
    } finally {
      sourceImage.delete()
    }
  }

  @Test
  fun copyReferenceImages_caseInsensitivePaths_success() {
    // 1. Arrange
    // Source is under "Build" instead of "build"
    val sourceBuildDir = File(projectRule.project.basePath, "app/Build").canonicalFile
    sourceBuildDir.mkdirs()
    val sourceImage = File(sourceBuildDir, "image.png").canonicalFile.apply { writeText("mixed case build source content") }

    // Destination uses "ScreenshotTestDebug" and "Reference"
    val expectedDestFile = File(projectRule.project.basePath, "app/src/ScreenshotTestDebug/Reference/MyTestClass/image.png").canonicalFile
    val imageData = createImageData(mapOf(sourceImage.canonicalPath to "MyTestClass"), expectedDestFile.canonicalPath)

    // 2. Act
    val failures = copyReferenceImages(listOf(imageData), projectRule.project.basePath!!)

    // 3. Assert
    assertTrue("Should succeed on case-insensitive filesystems", failures.isEmpty())
    assertTrue("Destination file should exist", expectedDestFile.exists())
    assertEquals("Content should match", "mixed case build source content", expectedDestFile.readText())
  }

  @Test
  fun copyReferenceImages_classnameStartsWithScreenshotTest_success() {
    // 1. Arrange
    val sourceImage = File(tempOutputDir, "image.png").canonicalFile.apply { writeText("content") }
    // Destination path contains a test class name starting with "ScreenshotTest" (e.g. ScreenshotTestClass)
    val expectedDestFile =
      File(projectRule.project.basePath, "app/src/screenshotTest/reference/com/example/ScreenshotTestClass/image.png").canonicalFile
    val imageData = createImageData(mapOf(sourceImage.canonicalPath to "ScreenshotTestClass"), expectedDestFile.canonicalPath)

    // 2. Act
    val failures = copyReferenceImages(listOf(imageData), projectRule.project.basePath!!)

    // 3. Assert
    assertTrue("Should succeed even if test class name starts with ScreenshotTest", failures.isEmpty())
    assertTrue("Destination file should exist", expectedDestFile.exists())
    assertEquals("content", expectedDestFile.readText())
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
    val newFile = File(projectRule.project.basePath, FileUtil.toSystemDependentName(relativePath))
    FileUtil.createIfDoesntExist(newFile)
    newFile.writeText(content)
    return newFile
  }
}
