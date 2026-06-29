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
package com.android.tools.idea.testartifacts.instrumented.testsuite.util

import com.android.tools.idea.testartifacts.instrumented.testsuite.util.ScreenshotTestUtils.calculateMatchPercentage
import com.android.tools.idea.testartifacts.instrumented.testsuite.util.ScreenshotTestUtils.loadImageMetadata
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Paths
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ScreenshotTestUtilsTest {

  @get:Rule val tempFolder = TemporaryFolder()

  /** Verifies that a valid difference ratio is correctly converted to a match percentage. */
  @Test
  fun testCalculateMatchPercentage() {
    val diffPercent = 0.0123 // Represents 1.23% difference
    val result = calculateMatchPercentage(diffPercent)
    assertThat(result).isEqualTo("98.77%")
  }

  /** Ensures that a null input to calculateMatchPercentage is handled gracefully and returns null. */
  @Test
  fun testCalculateMatchPercentage_null() {
    val result = calculateMatchPercentage(null)
    assertThat(result).isNull()
  }

  /** Tests the case where the difference is exactly 0.0, expecting a 100.00% match. */
  @Test
  fun testCalculateMatchPercentage_zeroDifference() {
    val diffPercent = 0.0
    val result = calculateMatchPercentage(diffPercent)
    assertThat(result).isEqualTo("100.00%")
  }

  /** Verifies that a 100% difference (ratio 1.0) correctly results in a 0.00% match. */
  @Test
  fun testCalculateMatchPercentage_hundredDifference() {
    val diffPercent = 1.0
    val result = calculateMatchPercentage(diffPercent)
    assertThat(result).isEqualTo("0.00%")
  }

  /** Tests a typical difference ratio. */
  @Test
  fun testCalculateMatchPercentage_typicalDifference() {
    val diffPercent = 0.1 // Represents 10% difference
    val result = calculateMatchPercentage(diffPercent)
    assertThat(result).isEqualTo("90.00%")
  }

  /**
   * Tests that difference ratios with more than four decimal places are correctly handled in the match percentage calculation, resulting in
   * a string rounded to two decimal places.
   */
  @Test
  fun testCalculateMatchPercentage_manyDecimalPlaces() {
    val diffPercent = 0.123456 // Represents 12.3456% difference
    val result = calculateMatchPercentage(diffPercent)
    assertThat(result).isEqualTo("87.65%")
  }

  /** Verifies that metadata is correctly loaded from a valid PNG image file. */
  @Test
  fun testLoadImageMetadata_validPng() = runBlocking {
    val imageFile = createImageFile("valid.png", "png")
    val metadata = loadImageMetadata(imageFile.absolutePath)
    assertThat(metadata.dimensions).isEqualTo("100x50")
    assertThat(metadata.size).isEqualTo("0 KB")
    assertThat(metadata.date).isNotEqualTo(NOT_APPLICABLE)
  }

  /** Verifies that metadata is correctly loaded from a valid JPEG image file. */
  @Test
  fun testLoadImageMetadata_validJpeg() = runBlocking {
    val imageFile = createImageFile("valid.jpeg", "jpeg")
    val metadata = loadImageMetadata(imageFile.absolutePath)
    assertThat(metadata.dimensions).isEqualTo("100x50")
    assertThat(metadata.size).isEqualTo("0 KB")
    assertThat(metadata.date).isNotEqualTo(NOT_APPLICABLE)
  }

  /** Ensures that a null file path is handled correctly, returning "N/A" for all metadata fields. */
  @Test
  fun testLoadImageMetadata_nullPath() = runBlocking {
    val metadata = loadImageMetadata(null)
    assertThat(metadata.dimensions).isEqualTo(NOT_APPLICABLE)
    assertThat(metadata.size).isEqualTo(NOT_APPLICABLE)
    assertThat(metadata.date).isEqualTo(NOT_APPLICABLE)
  }

  /** Checks that a non-existent file path is handled gracefully, returning "N/A" for all metadata fields. */
  @Test
  fun testLoadImageMetadata_nonExistentFile() = runBlocking {
    val metadata = loadImageMetadata("nonexistent.png")
    assertThat(metadata.dimensions).isEqualTo(NOT_APPLICABLE)
    assertThat(metadata.size).isEqualTo(NOT_APPLICABLE)
    assertThat(metadata.date).isEqualTo(NOT_APPLICABLE)
  }

  /** Verifies that a file that is not a valid image is handled correctly, returning "N/A" for dimensions. */
  @Test
  fun testLoadImageMetadata_notAnImage() = runBlocking {
    val notAnImage = tempFolder.newFile("not_an_image.txt")
    notAnImage.writeText("This is not an image")
    val metadata = loadImageMetadata(notAnImage.absolutePath)
    assertThat(metadata.dimensions).isEqualTo(NOT_APPLICABLE)
    assertThat(metadata.size).isNotEmpty()
    assertThat(metadata.date).isNotEmpty()
  }

  /** Ensures that a directory path is handled correctly, returning "N/A" for all metadata fields. */
  @Test
  fun testLoadImageMetadata_directory() = runBlocking {
    val directory = tempFolder.newFolder("a_directory")
    val metadata = loadImageMetadata(directory.absolutePath)
    assertThat(metadata.dimensions).isEqualTo(NOT_APPLICABLE)
    assertThat(metadata.size).isEqualTo(NOT_APPLICABLE)
    assertThat(metadata.date).isEqualTo(NOT_APPLICABLE)
  }

  /** Checks that an empty file is handled gracefully, returning "N/A" for all metadata fields. */
  @Test
  fun testLoadImageMetadata_emptyFile() = runBlocking {
    val emptyFile = tempFolder.newFile("empty.png")
    val metadata = loadImageMetadata(emptyFile.absolutePath)
    assertThat(metadata.dimensions).isEqualTo(NOT_APPLICABLE)
    assertThat(metadata.size).isEqualTo(NOT_APPLICABLE)
    assertThat(metadata.date).isEqualTo(NOT_APPLICABLE)
  }

  private fun createImageFile(name: String, format: String): File {
    val file = tempFolder.newFile(name)
    val image = BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB)
    ImageIO.write(image, format, file)
    return file
  }

  @Test
  fun testContainUnderProjectRoot_validRelativePath() {
    val project = mock<Project>()
    val basePath = tempFolder.root.canonicalPath
    whenever(project.basePath).thenReturn(basePath)
    val result = ScreenshotTestUtils.containUnderProjectRoot(project, "screenshots/image.png")
    val expected = Paths.get(basePath, "screenshots/image.png").toFile().canonicalFile.toPath().toString()
    assertThat(result).isEqualTo(expected)
  }

  @Test
  fun testContainUnderProjectRoot_escapingRelativePath() {
    val project = mock<Project>()
    val basePath = tempFolder.root.canonicalPath
    whenever(project.basePath).thenReturn(basePath)
    val result = ScreenshotTestUtils.containUnderProjectRoot(project, "../../etc/passwd")
    assertThat(result).isNull()
  }

  @Test
  fun testContainUnderProjectRoot_absolutePathOutside() {
    val project = mock<Project>()
    val basePath = tempFolder.root.canonicalPath
    whenever(project.basePath).thenReturn(basePath)
    val result = ScreenshotTestUtils.containUnderProjectRoot(project, "/etc/passwd")
    assertThat(result).isNull()
  }

  @Test
  fun testContainUnderProjectRoot_uncPath() {
    val project = mock<Project>()
    val basePath = tempFolder.root.canonicalPath
    whenever(project.basePath).thenReturn(basePath)
    val result = ScreenshotTestUtils.containUnderProjectRoot(project, "\\\\attacker.evil\\share\\image.png")
    assertThat(result).isNull()
  }

  @Test
  fun testContainUnderProjectRoot_networkPath() {
    val project = mock<Project>()
    val basePath = tempFolder.root.canonicalPath
    whenever(project.basePath).thenReturn(basePath)
    val result = ScreenshotTestUtils.containUnderProjectRoot(project, "//attacker.evil/share/image.png")
    assertThat(result).isNull()
  }

  @Test
  fun testContainUnderProjectRoot_nullOrEmpty() {
    val project = mock<Project>()
    val basePath = tempFolder.root.canonicalPath
    whenever(project.basePath).thenReturn(basePath)
    assertThat(ScreenshotTestUtils.containUnderProjectRoot(project, null)).isNull()
    assertThat(ScreenshotTestUtils.containUnderProjectRoot(project, "")).isNull()
  }

  @Test
  fun testContainUnderProjectRoot_nullProject() {
    assertThat(ScreenshotTestUtils.containUnderProjectRoot(null, "screenshots/image.png")).isNull()
  }

  @Test
  fun testIsNetworkPath() {
    assertThat(ScreenshotTestUtils.isNetworkPath("\\\\attacker.evil\\share\\image.png")).isTrue()
    assertThat(ScreenshotTestUtils.isNetworkPath("//attacker.evil/share/image.png")).isTrue()
    assertThat(ScreenshotTestUtils.isNetworkPath("  \\\\attacker.evil\\share\\image.png")).isTrue()
    assertThat(ScreenshotTestUtils.isNetworkPath("  //attacker.evil/share/image.png")).isTrue()
    assertThat(ScreenshotTestUtils.isNetworkPath("\\\\wsl$\\Ubuntu\\home\\user\\project")).isFalse()
    assertThat(ScreenshotTestUtils.isNetworkPath("\\\\wsl.localhost\\Ubuntu\\home")).isFalse()
    assertThat(ScreenshotTestUtils.isNetworkPath("\\\\WSL$\\Ubuntu\\home\\user\\project")).isFalse()
    assertThat(ScreenshotTestUtils.isNetworkPath("\\\\WSL.LOCALHOST\\Ubuntu\\home")).isFalse()
    assertThat(ScreenshotTestUtils.isNetworkPath("\\\\Wsl$\\Ubuntu\\home\\user\\project")).isFalse()
    assertThat(ScreenshotTestUtils.isNetworkPath("\\\\Wsl.Localhost\\Ubuntu\\home")).isFalse()
    assertThat(ScreenshotTestUtils.isNetworkPath("screenshots/image.png")).isFalse()
    assertThat(ScreenshotTestUtils.isNetworkPath("/absolute/local/path/image.png")).isFalse()
    assertThat(ScreenshotTestUtils.isNetworkPath(null)).isFalse()
    assertThat(ScreenshotTestUtils.isNetworkPath("")).isFalse()
  }

  @Test
  fun testResolvePath_validRelativePath() {
    val project = mock<Project>()
    val basePath = tempFolder.root.canonicalPath
    whenever(project.basePath).thenReturn(basePath)
    val result = ScreenshotTestUtils.resolvePath(project, null, "screenshots/image.png")
    val expected = Paths.get(basePath, "screenshots/image.png").toFile().canonicalFile.toPath().toString()
    assertThat(result).isEqualTo(expected)
  }

  @Test
  fun testResolvePath_escapingRelativePath() {
    val project = mock<Project>()
    val basePath = tempFolder.root.canonicalPath
    whenever(project.basePath).thenReturn(basePath)
    val result = ScreenshotTestUtils.resolvePath(project, null, "../../etc/passwd")
    assertThat(result).isNull()
  }

  @Test
  fun testResolvePath_absolutePathOutside() {
    val project = mock<Project>()
    val basePath = tempFolder.root.canonicalPath
    whenever(project.basePath).thenReturn(basePath)
    val result = ScreenshotTestUtils.resolvePath(project, null, "/etc/passwd")
    assertThat(result).isNull()
  }

  @Test
  fun testResolvePath_uncPath() {
    val project = mock<Project>()
    val basePath = tempFolder.root.canonicalPath
    whenever(project.basePath).thenReturn(basePath)
    val result = ScreenshotTestUtils.resolvePath(project, null, "\\\\attacker.evil\\share\\image.png")
    assertThat(result).isNull()
  }

  @Test
  fun testResolvePath_networkPath() {
    val project = mock<Project>()
    val basePath = tempFolder.root.canonicalPath
    whenever(project.basePath).thenReturn(basePath)
    val result = ScreenshotTestUtils.resolvePath(project, null, "//attacker.evil/share/image.png")
    assertThat(result).isNull()
  }

  @Test
  fun testResolvePath_null() {
    val project = mock<Project>()
    val basePath = tempFolder.root.canonicalPath
    whenever(project.basePath).thenReturn(basePath)
    assertThat(ScreenshotTestUtils.resolvePath(project, null, null)).isNull()
  }

  @Test
  fun testResolvePath_nullProject() {
    assertThat(ScreenshotTestUtils.resolvePath(null, null, "screenshots/image.png")).isNull()
  }
}
