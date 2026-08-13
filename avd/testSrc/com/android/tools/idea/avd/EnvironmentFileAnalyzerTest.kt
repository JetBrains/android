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
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.Deflater
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.sin
import kotlin.text.Charsets.US_ASCII
import kotlin.text.Charsets.UTF_8
import org.junit.Test

/** Tests for [EnvironmentFileAnalyzer]. */
class EnvironmentFileAnalyzerTest {

  @Test
  fun testReadXmpMetadata() {
    val title = "My Custom Title"
    val jpegBytes = createMockJpegWithXmp(title, isDefault = true)

    val tempDir = Files.createTempDirectory("test")
    val tempFile = tempDir.resolve("test.jpg")
    Files.write(tempFile, jpegBytes)

    val metadata = EnvironmentFileAnalyzer.readXmpMetadata(tempFile)
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

    val metadata = EnvironmentFileAnalyzer.readXmpMetadata(tempFile)
    assertThat(metadata).isNotNull()
    assertThat(metadata!!.title).isEqualTo(title)
    assertThat(metadata.isDefault).isFalse()
  }

  @Test
  fun testReadXmpMetadataFailsGracefully() {
    val tempDir = Files.createTempDirectory("test")
    val tempFile = tempDir.resolve("invalid.jpg")
    Files.write(tempFile, byteArrayOf(1, 2, 3, 4))
    assertThat(EnvironmentFileAnalyzer.readXmpMetadata(tempFile)).isNull()
  }

  @Test
  fun testIs360Image_withProjectionTypeElement() {
    val xml =
      """
      <x:xmpmeta xmlns:x="adobe:ns:meta/">
       <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
        <rdf:Description rdf:about="" xmlns:GPano="http://ns.google.com/photos/1.0/panorama/">
         <GPano:ProjectionType>equirectangular</GPano:ProjectionType>
        </rdf:Description>
       </rdf:RDF>
      </x:xmpmeta>
      """
        .trimIndent()
    val jpegBytes = createMockJpegWithXml(xml)
    val tempFile = Files.createTempFile("test", ".jpg")
    Files.write(tempFile, jpegBytes)
    assertThat(EnvironmentFileAnalyzer.is360Image(tempFile)).isTrue()
  }

  @Test
  fun testIs360Image_withProjectionTypeAttribute() {
    val xml =
      """
      <x:xmpmeta xmlns:x="adobe:ns:meta/">
       <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
        <rdf:Description rdf:about="" xmlns:GPano="http://ns.google.com/photos/1.0/panorama/" GPano:ProjectionType="equirectangular" />
       </rdf:RDF>
      </x:xmpmeta>
      """
        .trimIndent()
    val jpegBytes = createMockJpegWithXml(xml)
    val tempFile = Files.createTempFile("test", ".jpg")
    Files.write(tempFile, jpegBytes)
    assertThat(EnvironmentFileAnalyzer.is360Image(tempFile)).isTrue()
  }

  @Test
  fun testIs360Image_withUsePanoramaViewerElement() {
    val xml =
      """
      <x:xmpmeta xmlns:x="adobe:ns:meta/">
       <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
        <rdf:Description rdf:about="" xmlns:GPano="http://ns.google.com/photos/1.0/panorama/">
         <GPano:UsePanoramaViewer>True</GPano:UsePanoramaViewer>
        </rdf:Description>
       </rdf:RDF>
      </x:xmpmeta>
      """
        .trimIndent()
    val jpegBytes = createMockJpegWithXml(xml)
    val tempFile = Files.createTempFile("test", ".jpg")
    Files.write(tempFile, jpegBytes)
    assertThat(EnvironmentFileAnalyzer.is360Image(tempFile)).isTrue()
  }

  @Test
  fun testIs360Image_withUsePanoramaViewerAttribute() {
    val xml =
      """
      <x:xmpmeta xmlns:x="adobe:ns:meta/">
       <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
        <rdf:Description rdf:about="" xmlns:GPano="http://ns.google.com/photos/1.0/panorama/" GPano:UsePanoramaViewer="true" />
       </rdf:RDF>
      </x:xmpmeta>
      """
        .trimIndent()
    val jpegBytes = createMockJpegWithXml(xml)
    val tempFile = Files.createTempFile("test", ".jpg")
    Files.write(tempFile, jpegBytes)
    assertThat(EnvironmentFileAnalyzer.is360Image(tempFile)).isTrue()
  }

  @Test
  fun testIs360Image_falseForNormalImage() {
    val xml =
      """
      <x:xmpmeta xmlns:x="adobe:ns:meta/">
       <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
        <rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/">
         <dc:title>Some Title</dc:title>
        </rdf:Description>
       </rdf:RDF>
      </x:xmpmeta>
      """
        .trimIndent()
    val jpegBytes = createMockJpegWithXml(xml)
    val tempFile = Files.createTempFile("test", ".jpg")
    Files.write(tempFile, jpegBytes)
    assertThat(EnvironmentFileAnalyzer.is360Image(tempFile)).isFalse()
  }

  private fun createMockJpegWithXml(xml: String): ByteArray {
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

  @Test
  fun testIs360Image_pngUncompressed() {
    val xml =
      """
      <x:xmpmeta xmlns:x="adobe:ns:meta/">
       <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
        <rdf:Description rdf:about="" xmlns:GPano="http://ns.google.com/photos/1.0/panorama/" GPano:ProjectionType="equirectangular" />
       </rdf:RDF>
      </x:xmpmeta>
      """
        .trimIndent()
    val pngBytes = createMockPngWithXml(xml, compressed = false)
    val tempFile = Files.createTempFile("test", ".png")
    Files.write(tempFile, pngBytes)
    assertThat(EnvironmentFileAnalyzer.is360Image(tempFile)).isTrue()
  }

  @Test
  fun testIs360Image_pngCompressed() {
    val xml =
      """
      <x:xmpmeta xmlns:x="adobe:ns:meta/">
       <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
        <rdf:Description rdf:about="" xmlns:GPano="http://ns.google.com/photos/1.0/panorama/">
         <GPano:UsePanoramaViewer>True</GPano:UsePanoramaViewer>
        </rdf:Description>
       </rdf:RDF>
      </x:xmpmeta>
      """
        .trimIndent()
    val pngBytes = createMockPngWithXml(xml, compressed = true)
    val tempFile = Files.createTempFile("test", ".png")
    Files.write(tempFile, pngBytes)
    assertThat(EnvironmentFileAnalyzer.is360Image(tempFile)).isTrue()
  }

  @Test
  fun testIs360Image_pngNot360() {
    val xml =
      """
      <x:xmpmeta xmlns:x="adobe:ns:meta/">
       <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
        <rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/">
         <dc:title>Just a Title</dc:title>
        </rdf:Description>
       </rdf:RDF>
      </x:xmpmeta>
      """
        .trimIndent()
    val pngBytes = createMockPngWithXml(xml, compressed = false)
    val tempFile = Files.createTempFile("test", ".png")
    Files.write(tempFile, pngBytes)
    assertThat(EnvironmentFileAnalyzer.is360Image(tempFile)).isFalse()
  }

  private fun createMockPngWithXml(xml: String, compressed: Boolean = false): ByteArray {
    val bos = ByteArrayOutputStream()
    // Signature
    bos.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))

    // IHDR
    writePngChunk(bos, "IHDR", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0))

    // iTXt
    val chunkBos = ByteArrayOutputStream()
    chunkBos.write("XML:com.adobe.xmp\u0000".toByteArray(UTF_8))
    if (compressed) {
      chunkBos.write(1) // compression flag
      chunkBos.write(0) // compression method
      chunkBos.write(0) // language tag null
      chunkBos.write(0) // translated keyword null
      val deflatedBytes = ByteArray(1024)
      val compressedBos = ByteArrayOutputStream()
      Deflater().use { deflater ->
        deflater.setInput(xml.toByteArray(UTF_8))
        deflater.finish()
        while (!deflater.finished()) {
          val count = deflater.deflate(deflatedBytes)
          compressedBos.write(deflatedBytes, 0, count)
        }
      }
      chunkBos.write(compressedBos.toByteArray())
    } else {
      chunkBos.write(0) // compression flag
      chunkBos.write(0) // compression method
      chunkBos.write(0) // language tag null
      chunkBos.write(0) // translated keyword null
      chunkBos.write(xml.toByteArray(UTF_8))
    }
    writePngChunk(bos, "iTXt", chunkBos.toByteArray())

    // IEND
    writePngChunk(bos, "IEND", ByteArray(0))

    return bos.toByteArray()
  }

  private fun writePngChunk(bos: ByteArrayOutputStream, type: String, data: ByteArray) {
    val length = data.size
    bos.write((length shr 24) and 0xFF)
    bos.write((length shr 16) and 0xFF)
    bos.write((length shr 8) and 0xFF)
    bos.write(length and 0xFF)
    bos.write(type.toByteArray(US_ASCII))
    bos.write(data)
    // CRC (dummy)
    bos.write(byteArrayOf(0, 0, 0, 0))
  }

  @Test
  fun testIs360ImageCandidate_2To1SeamlessBoundary() {
    val w = 200
    val h = 100
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until h) {
      for (x in 0 until w) {
        val v = (128 + 127 * sin(2 * PI * x / w)).toInt().coerceIn(0, 255)
        val color = (v shl 16) or (v shl 8) or v
        img.setRGB(x, y, color)
      }
    }

    val tempFile = Files.createTempFile("seamless", ".png")
    ImageIO.write(img, "png", tempFile.toFile())

    assertThat(EnvironmentFileAnalyzer.is360ImageCandidate(tempFile)).isTrue()
  }

  @Test
  fun testIs360ImageCandidate_2To1DiscontinuousBoundary() {
    val w = 200
    val h = 100
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until h) {
      for (x in 0 until w) {
        val color = if (x < w / 2) 0x000000 else 0xFFFFFF
        img.setRGB(x, y, color)
      }
    }

    val tempFile = Files.createTempFile("discontinuous", ".png")
    ImageIO.write(img, "png", tempFile.toFile())

    assertThat(EnvironmentFileAnalyzer.is360ImageCandidate(tempFile)).isFalse()
  }

  @Test
  fun testIs360ImageCandidate_16To9Image() {
    val img = BufferedImage(160, 90, BufferedImage.TYPE_INT_RGB)
    val tempFile = Files.createTempFile("not2to1", ".png")
    ImageIO.write(img, "png", tempFile.toFile())

    assertThat(EnvironmentFileAnalyzer.is360ImageCandidate(tempFile)).isFalse()
  }

  @Test
  fun testIs3dSceneFile() {
    val tempDir = Files.createTempDirectory("test")
    val objFile = tempDir.resolve("scene.obj")
    val txtFile = tempDir.resolve("scene.txt")
    assertThat(EnvironmentFileAnalyzer.is3dSceneFile(objFile)).isTrue()
    assertThat(EnvironmentFileAnalyzer.is3dSceneFile(txtFile)).isFalse()
  }

  @Test
  fun testIsVideoFile() {
    val tempDir = Files.createTempDirectory("test")
    val mp4File = tempDir.resolve("video.mp4")
    val webmFile = tempDir.resolve("video.webm")
    val jpgFile = tempDir.resolve("image.jpg")
    assertThat(EnvironmentFileAnalyzer.isVideoFile(mp4File)).isTrue()
    assertThat(EnvironmentFileAnalyzer.isVideoFile(webmFile)).isTrue()
    assertThat(EnvironmentFileAnalyzer.isVideoFile(jpgFile)).isFalse()
  }

  @Test
  fun testIsWavefrontObjFile() {
    val tempDir = Files.createTempDirectory("test")
    val validObj = tempDir.resolve("valid.obj")
    Files.writeString(validObj, "# Wavefront OBJ file\nv 1.0 2.0 3.0\nf 1 2 3\n")
    assertThat(EnvironmentFileAnalyzer.isWavefrontObjFile(validObj)).isTrue()

    val invalidObj = tempDir.resolve("invalid.obj")
    Files.writeString(invalidObj, "This is not a valid OBJ file")
    assertThat(EnvironmentFileAnalyzer.isWavefrontObjFile(invalidObj)).isFalse()
  }
}
