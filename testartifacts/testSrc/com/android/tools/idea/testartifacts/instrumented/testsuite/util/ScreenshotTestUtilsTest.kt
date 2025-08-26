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

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class ScreenshotTestUtilsTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  /**
   * Verifies that a valid percentage difference is correctly parsed and converted to a match percentage.
   */
  @Test
  fun testParseMatchPercentage() {
    val stackTrace = "Difference: 1.23%"
    val result = ScreenshotTestUtils.parseMatchPercentage(stackTrace)
    assertThat(result).isEqualTo("98.77%")
  }

  /**
   * Ensures that an invalid or non-numeric difference string results in a null match percentage.
   */
  @Test
  fun testParseMatchPercentage_invalid() {
    val stackTrace = "Some other error"
    val result = ScreenshotTestUtils.parseMatchPercentage(stackTrace)
    assertThat(result).isNull()
  }

  /**
   * Checks that a null input to parseMatchPercentage is handled gracefully and returns null.
   */
  @Test
  fun testParseMatchPercentage_null() {
    val result = ScreenshotTestUtils.parseMatchPercentage(null)
    assertThat(result).isNull()
  }

  /**
   * Tests the case where the difference is exactly 0%, expecting a 100% match.
   */
  @Test
  fun testParseMatchPercentage_zeroDifference() {
    val stackTrace = "Difference: 0%"
    val result = ScreenshotTestUtils.parseMatchPercentage(stackTrace)
    assertThat(result).isEqualTo("100.00%")
  }

  /**
   * Verifies that a 100% difference correctly results in a 0% match.
   */
  @Test
  fun testParseMatchPercentage_hundredDifference() {
    val stackTrace = "Difference: 100%"
    val result = ScreenshotTestUtils.parseMatchPercentage(stackTrace)
    assertThat(result).isEqualTo("0.00%")
  }

  /**
   * Ensures that integer percentage differences are correctly handled.
   */
  @Test
  fun testParseMatchPercentage_integerDifference() {
    val stackTrace = "Difference: 10%"
    val result = ScreenshotTestUtils.parseMatchPercentage(stackTrace)
    assertThat(result).isEqualTo("90.00%")
  }

  /**
   * Tests that percentage differences with more than two decimal places are correctly truncated and rounded.
   */
  @Test
  fun testParseMatchPercentage_manyDecimalPlaces() {
    val stackTrace = "Difference: 12.3456%"
    val result = ScreenshotTestUtils.parseMatchPercentage(stackTrace)
    assertThat(result).isEqualTo("87.65%")
  }

  /**
   * Checks that an empty input string is handled gracefully and results in a null match percentage.
   */
  @Test
  fun testParseMatchPercentage_emptyString() {
    val result = ScreenshotTestUtils.parseMatchPercentage("")
    assertThat(result).isNull()
  }

  /**
   * Verifies that metadata is correctly loaded from a valid PNG image file.
   */
  @Test
  fun testLoadImageMetadata_validPng() = runBlocking {
    val imageFile = createImageFile("valid.png", "png")
    val metadata = ScreenshotTestUtils.loadImageMetadata(imageFile.absolutePath)
    assertThat(metadata.dimensions).isEqualTo("100x50")
    assertThat(metadata.size).isEqualTo("0 KB")
    assertThat(metadata.date).isNotEqualTo(NOT_APPLICABLE)
  }

  /**
   * Verifies that metadata is correctly loaded from a valid JPEG image file.
   */
  @Test
  fun testLoadImageMetadata_validJpeg() = runBlocking {
    val imageFile = createImageFile("valid.jpeg", "jpeg")
    val metadata = ScreenshotTestUtils.loadImageMetadata(imageFile.absolutePath)
    assertThat(metadata.dimensions).isEqualTo("100x50")
    assertThat(metadata.size).isEqualTo("0 KB")
    assertThat(metadata.date).isNotEqualTo(NOT_APPLICABLE)
  }

  /**
   * Ensures that a null file path is handled correctly, returning "N/A" for all metadata fields.
   */
  @Test
  fun testLoadImageMetadata_nullPath() = runBlocking {
    val metadata = ScreenshotTestUtils.loadImageMetadata(null)
    assertThat(metadata.dimensions).isEqualTo(NOT_APPLICABLE)
    assertThat(metadata.size).isEqualTo(NOT_APPLICABLE)
    assertThat(metadata.date).isEqualTo(NOT_APPLICABLE)
  }

  /**
   * Checks that a non-existent file path is handled gracefully, returning "N/A" for all metadata fields.
   */
  @Test
  fun testLoadImageMetadata_nonExistentFile() = runBlocking {
    val metadata = ScreenshotTestUtils.loadImageMetadata("nonexistent.png")
    assertThat(metadata.dimensions).isEqualTo(NOT_APPLICABLE)
    assertThat(metadata.size).isEqualTo(NOT_APPLICABLE)
    assertThat(metadata.date).isEqualTo(NOT_APPLICABLE)
  }

  /**
   * Verifies that a file that is not a valid image is handled correctly, returning "N/A" for dimensions.
   */
  @Test
  fun testLoadImageMetadata_notAnImage() = runBlocking {
    val notAnImage = tempFolder.newFile("not_an_image.txt")
    notAnImage.writeText("This is not an image")
    val metadata = ScreenshotTestUtils.loadImageMetadata(notAnImage.absolutePath)
    assertThat(metadata.dimensions).isEqualTo(NOT_APPLICABLE)
    assertThat(metadata.size).isNotEmpty()
    assertThat(metadata.date).isNotEmpty()
  }

  /**
   * Ensures that a directory path is handled correctly, returning "N/A" for all metadata fields.
   */
  @Test
  fun testLoadImageMetadata_directory() = runBlocking {
    val directory = tempFolder.newFolder("a_directory")
    val metadata = ScreenshotTestUtils.loadImageMetadata(directory.absolutePath)
    assertThat(metadata.dimensions).isEqualTo(NOT_APPLICABLE)
    assertThat(metadata.size).isEqualTo(NOT_APPLICABLE)
    assertThat(metadata.date).isEqualTo(NOT_APPLICABLE)
  }

  /**
   * Checks that an empty file is handled gracefully, returning "N/A" for all metadata fields.
   */
  @Test
  fun testLoadImageMetadata_emptyFile() = runBlocking {
    val emptyFile = tempFolder.newFile("empty.png")
    val metadata = ScreenshotTestUtils.loadImageMetadata(emptyFile.absolutePath)
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
}
