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
package com.android.tools.idea.avd

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import kotlin.text.Charsets.UTF_8
import org.junit.Test

/** Tests for [EnvironmentImageScanner]. */
class EnvironmentImageScannerTest {

  @Test
  fun testReadXmpMetadata() {
    val title = "My Custom Title"
    val jpegBytes = createMockJpegWithXmp(title, isDefault = true)

    val tempDir = Files.createTempDirectory("test")
    val tempFile = tempDir.resolve("test.jpg")
    Files.write(tempFile, jpegBytes)

    val metadata = EnvironmentImageScanner.readXmpMetadata(tempFile)
    assertThat(metadata).isNotNull()
    assertThat(metadata!!.title).isEqualTo(title)
    assertThat(metadata.isDefault).isTrue()
  }

  @Test
  fun testReadXmpMetadataNotDefault() {
    val title = "Another Title"
    val jpegBytes = createMockJpegWithXmp(title, isDefault = false)

    val tempDir = Files.createTempDirectory("test")
    val tempFile = tempDir.resolve("test.jpg")
    Files.write(tempFile, jpegBytes)

    val metadata = EnvironmentImageScanner.readXmpMetadata(tempFile)
    assertThat(metadata).isNotNull()
    assertThat(metadata!!.title).isEqualTo(title)
    assertThat(metadata.isDefault).isFalse()
  }

  @Test
  fun testReadXmpMetadataFailsGracefully() {
    val tempDir = Files.createTempDirectory("test")
    val tempFile = tempDir.resolve("invalid.jpg")
    Files.write(tempFile, byteArrayOf(1, 2, 3, 4))
    assertThat(EnvironmentImageScanner.readXmpMetadata(tempFile)).isNull()
  }

  private fun createMockJpegWithXmp(title: String, isDefault: Boolean): ByteArray {
    val androidemulatorAttribute = if (isDefault) " xmlns:androidemulator=\"urn:androidemulator:metadata:private:1.0\"" else ""
    val defaultTag = if (isDefault) "\n         <androidemulator:isDefault>true</androidemulator:isDefault>" else ""

    val xml =
      """
      <x:xmpmeta xmlns:x="adobe:ns:meta/">
       <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
        <rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/"$androidemulatorAttribute>
         <dc:title>
          <rdf:Alt>
           <rdf:li xml:lang="x-default">$title</rdf:li>
          </rdf:Alt>
         </dc:title>$defaultTag
        </rdf:Description>
       </rdf:RDF>
      </x:xmpmeta>
    """
        .trimIndent()

    val xmlBytes = xml.toByteArray(UTF_8)
    val xmpHeader = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(UTF_8)
    val payloadLength = xmpHeader.size + xmlBytes.size
    val totalLength = payloadLength + 2

    val jpeg = ByteArray(2 + 4 + payloadLength + 2)
    jpeg[0] = 0xFF.toByte()
    jpeg[1] = 0xD8.toByte() // SOI

    jpeg[2] = 0xFF.toByte()
    jpeg[3] = 0xE1.toByte() // APP1
    jpeg[4] = ((totalLength shr 8) and 0xFF).toByte()
    jpeg[5] = (totalLength and 0xFF).toByte()

    System.arraycopy(xmpHeader, 0, jpeg, 6, xmpHeader.size)
    System.arraycopy(xmlBytes, 0, jpeg, 6 + xmpHeader.size, xmlBytes.size)

    val eoiIdx = 6 + payloadLength
    jpeg[eoiIdx] = 0xFF.toByte()
    jpeg[eoiIdx + 1] = 0xD9.toByte() // EOI

    return jpeg
  }
}
