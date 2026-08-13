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

import com.intellij.openapi.diagnostic.thisLogger
import java.awt.image.BufferedImage
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.Inflater
import javax.imageio.ImageIO
import kotlin.text.Charsets.US_ASCII
import kotlin.text.Charsets.UTF_8
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser

data class EnvironmentImage(val path: Path, val title: String, val isDefault: Boolean)

internal data class XmpMetadata(val title: String, val isDefault: Boolean)

/** Functions for scanning directories and extracting XMP metadata from emulator virtual scene environment image files. */
object EnvironmentFileAnalyzer {
  /**
   * Scans the specified directory for image files, parses their XMP metadata, and returns a list of [EnvironmentImage] records for files
   * that have a valid XMP title.
   */
  internal fun scanEnvironments(dir: Path): List<EnvironmentImage> {
    if (!Files.exists(dir)) return emptyList()
    val result = mutableListOf<EnvironmentImage>()
    Files.list(dir).use { stream ->
      stream
        // The emulator supports PNG environment images (e.g. for custom environments), so isImageFile
        // returns true for PNGs, even though built-in environments are currently only JPEGs.
        .filter { Files.isRegularFile(it) && isImageFile(it) }
        .forEach { file ->
          val metadata = readXmpMetadata(file)
          if (metadata != null) {
            result.add(EnvironmentImage(file, metadata.title, metadata.isDefault))
          }
        }
    }
    return result
  }

  private fun isImageFile(file: Path): Boolean {
    val name = file.fileName.toString()
    return name.endsWith(".jpg", ignoreCase = true) || name.endsWith(".jpeg", ignoreCase = true) || name.endsWith(".png", ignoreCase = true)
  }

  /**
   * Reads XMP metadata from the specified image file. Extracts the 'dc:title' and the custom 'studio:isDefault' properties. Returns null if
   * XMP metadata is missing or invalid.
   */
  internal fun readXmpMetadata(path: Path): XmpMetadata? {
    return try {
      Files.newInputStream(path).use { inputStream ->
        val xmpData = extractXmpData(inputStream) ?: return null
        parseXmpMetadata(xmpData)
      }
    } catch (e: Exception) {
      thisLogger().warn("Failed to read XMP metadata from $path", e)
      null
    }
  }

  private fun extractXmpData(inputStream: InputStream): ByteArray? {
    val bufferedStream = if (inputStream.markSupported()) inputStream else BufferedInputStream(inputStream)
    bufferedStream.mark(8)
    val signature = ByteArray(8)
    val bytesRead = bufferedStream.readNBytes(signature, 0, 8)
    bufferedStream.reset()
    if (bytesRead < 2) return null

    if (signature[0] == 0xFF.toByte() && signature[1] == 0xD8.toByte()) {
      return extractXmpFromJpeg(bufferedStream)
    }
    if (bytesRead == 8 && signature.contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))) {
      return extractXmpFromPng(bufferedStream)
    }
    return null
  }

  private fun extractXmpFromJpeg(inputStream: InputStream): ByteArray? {
    val header = ByteArray(2)
    if (inputStream.readNBytes(header, 0, 2) != 2 || header[0] != 0xFF.toByte() || header[1] != 0xD8.toByte()) {
      return null
    }
    while (true) {
      val markerHeader = ByteArray(4)
      val readMarker = inputStream.readNBytes(markerHeader, 0, 4)
      if (readMarker != 4) break
      if (markerHeader[0] != 0xFF.toByte()) {
        break
      }
      val marker = markerHeader[1].toInt() and 0xFF
      val length = ((markerHeader[2].toInt() and 0xFF) shl 8) or (markerHeader[3].toInt() and 0xFF)
      if (length < 2) break

      val payloadLength = length - 2
      if (marker == 0xE1) { // APP1
        val payload = inputStream.readNBytes(payloadLength)
        if (payload.size == payloadLength) {
          val xmpHeader = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(UTF_8)
          if (payload.size > xmpHeader.size && payload.sliceArray(xmpHeader.indices).contentEquals(xmpHeader)) {
            return payload.sliceArray(xmpHeader.size until payload.size)
          }
        }
      } else {
        skipNBytes(inputStream, payloadLength.toLong())
      }
    }
    return null
  }

  private fun extractXmpFromPng(inputStream: InputStream): ByteArray? {
    val signature = ByteArray(8)
    if (inputStream.readNBytes(signature, 0, 8) != 8) return null
    val expectedSig = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    if (!signature.contentEquals(expectedSig)) return null

    val buffer = ByteArray(4)
    while (true) {
      if (inputStream.readNBytes(buffer, 0, 4) != 4) break
      val length =
        ((buffer[0].toInt() and 0xFF) shl 24) or
          ((buffer[1].toInt() and 0xFF) shl 16) or
          ((buffer[2].toInt() and 0xFF) shl 8) or
          (buffer[3].toInt() and 0xFF)

      val typeBytes = ByteArray(4)
      if (inputStream.readNBytes(typeBytes, 0, 4) != 4) break
      val type = String(typeBytes, US_ASCII)

      if (type == "iTXt") {
        val data = inputStream.readNBytes(length)
        if (data.size != length) break
        val xmp = parseITXtChunk(data)
        if (xmp != null) {
          // Skip CRC
          skipNBytes(inputStream, 4)
          return xmp
        }
      } else {
        skipNBytes(inputStream, length.toLong())
      }

      // Read CRC
      if (inputStream.readNBytes(buffer, 0, 4) != 4) break

      if (type == "IEND") break
    }
    return null
  }

  private fun parseITXtChunk(data: ByteArray): ByteArray? {
    var pos = 0
    // 1. Keyword (null-terminated UTF-8/Latin-1)
    val keywordStart = pos
    while (pos < data.size && data[pos] != 0.toByte()) {
      pos++
    }
    if (pos >= data.size) return null
    val keyword = String(data, keywordStart, pos - keywordStart, UTF_8)
    pos++ // skip null byte

    if (keyword != "XML:com.adobe.xmp") return null

    if (pos + 2 > data.size) return null
    val compressionFlag = data[pos].toInt()
    pos++
    val compressionMethod = data[pos].toInt()
    pos++

    // Language tag (null-terminated ASCII)
    while (pos < data.size && data[pos] != 0.toByte()) {
      pos++
    }
    if (pos >= data.size) return null
    pos++ // skip null byte

    // Translated keyword (null-terminated UTF-8)
    while (pos < data.size && data[pos] != 0.toByte()) {
      pos++
    }
    if (pos >= data.size) return null
    pos++ // skip null byte

    val textLength = data.size - pos
    if (textLength <= 0) return null

    val textBytes = data.sliceArray(pos until data.size)
    return if (compressionFlag == 1) {
      if (compressionMethod != 0) return null
      decompressZlib(textBytes)
    } else {
      textBytes
    }
  }

  private fun decompressZlib(compressedData: ByteArray): ByteArray {
    val inflater = Inflater()
    try {
      inflater.setInput(compressedData)
      val outputStream = ByteArrayOutputStream(compressedData.size)
      val buffer = ByteArray(1024)
      while (!inflater.finished()) {
        val count = inflater.inflate(buffer)
        if (count == 0 && inflater.needsInput()) break
        outputStream.write(buffer, 0, count)
      }
      return outputStream.toByteArray()
    } finally {
      inflater.end()
    }
  }

  private fun skipNBytes(inputStream: InputStream, n: Long) {
    var remaining = n
    while (remaining > 0) {
      val skipped = inputStream.skip(remaining)
      if (skipped > 0) {
        remaining -= skipped
      } else {
        if (inputStream.read() == -1) {
          break
        }
        remaining--
      }
    }
  }

  private fun parseXmpMetadata(xmpData: ByteArray): XmpMetadata? {
    val parser =
      KXmlParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        setInput(xmpData.inputStream(), UTF_8.name())
      }

    var insideTitle = false
    var insideAlt = false
    var title: String? = null
    var isDefault = false

    while (true) {
      val type = parser.next()
      if (type == XmlPullParser.END_DOCUMENT) break

      if (type == XmlPullParser.START_TAG) {
        val ns = parser.namespace
        val name = parser.name
        if (ns == "http://purl.org/dc/elements/1.1/" && name == "title") {
          insideTitle = true
        } else if (insideTitle && ns == "http://www.w3.org/1999/02/22-rdf-syntax-ns#" && name == "Alt") {
          insideAlt = true
        } else if (insideAlt && ns == "http://www.w3.org/1999/02/22-rdf-syntax-ns#" && name == "li") {
          title = parser.nextText()
        } else if (ns == "urn:androidemulator:metadata:private:1.0" && name == "isDefault") {
          isDefault = parser.nextText().toBoolean()
        }
      } else if (type == XmlPullParser.END_TAG) {
        val ns = parser.namespace
        val name = parser.name
        if (ns == "http://purl.org/dc/elements/1.1/" && name == "title") {
          insideTitle = false
          insideAlt = false
        } else if (ns == "http://www.w3.org/1999/02/22-rdf-syntax-ns#" && name == "Alt") {
          insideAlt = false
        }
      }
    }
    if (title != null) {
      return XmpMetadata(title, isDefault)
    }
    return null
  }

  /** Checks if the image is a 360 image by looking at its XMP metadata. */
  fun is360Image(path: Path): Boolean {
    return try {
      Files.newInputStream(path).use { inputStream ->
        val xmpData = extractXmpData(inputStream) ?: return false
        parseIs360(xmpData)
      }
    } catch (e: Exception) {
      thisLogger().warn("Failed to check if image is 360: $path", e)
      false
    }
  }

  private fun parseIs360(xmpData: ByteArray): Boolean {
    val parser =
      KXmlParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        setInput(xmpData.inputStream(), UTF_8.name())
      }

    while (true) {
      val type = parser.next()
      if (type == XmlPullParser.END_DOCUMENT) break

      if (type == XmlPullParser.START_TAG) {
        val ns = parser.namespace
        val name = parser.name
        if (ns == "http://ns.google.com/photos/1.0/panorama/") {
          if (name == "ProjectionType") {
            val text = parser.nextText()
            if (text == "equirectangular") {
              return true
            }
          } else if (name == "UsePanoramaViewer") {
            val text = parser.nextText()
            if (text.equals("true", ignoreCase = true)) {
              return true
            }
          }
        }
        // Also check attributes
        for (i in 0 until parser.attributeCount) {
          val attrNamespace = parser.getAttributeNamespace(i)
          val attrName = parser.getAttributeName(i)
          val attrValue = parser.getAttributeValue(i)
          if (attrNamespace == "http://ns.google.com/photos/1.0/panorama/") {
            if (attrName == "ProjectionType" && attrValue == "equirectangular") {
              return true
            }
            if (attrName == "UsePanoramaViewer" && attrValue.equals("true", ignoreCase = true)) {
              return true
            }
          }
        }
      }
    }
    return false
  }

  /**
   * Checks if an image file without XMP metadata is a 360-degree candidate by checking for a 2:1 or greater aspect ratio and performing a
   * cyclic boundary test (left-right edge continuity using SSIM).
   */
  fun is360ImageCandidate(path: Path): Boolean {
    return try {
      Files.newInputStream(path).use { inputStream ->
        val image = ImageIO.read(inputStream) ?: return false
        is360ImageCandidate(image)
      }
    } catch (e: Exception) {
      thisLogger().warn("Failed to check if image is 360 candidate: $path", e)
      false
    }
  }

  private fun is360ImageCandidate(image: BufferedImage): Boolean =
    hasAtLeastTwoToOneAspectRatio(image.width, image.height) && hasCyclicBoundaryContinuity(image)

  private fun hasAtLeastTwoToOneAspectRatio(width: Int, height: Int): Boolean = width > 0 && height > 0 && width >= height * 2

  private fun hasCyclicBoundaryContinuity(image: BufferedImage, threshold: Double = 0.6): Boolean {
    if (image.width < 2) return false

    val leftStrip = image.getSubimage(0, 0, 1, image.height)
    val rightStrip = image.getSubimage(image.width - 1, 0, 1, image.height)

    val ssim = computeSsim(leftStrip, rightStrip)
    return ssim >= threshold
  }

  /**
   * Computes the Structural Similarity Index Measure (SSIM) between two images of equal dimensions, weighted across the Red, Green, and
   * Blue color channels using perceptual Rec. 601 weights.
   *
   * The returned value ranges from -1.0 to 1.0, where 1.0 indicates identical visual structure and color.
   *
   * @param img1 the first image strip
   * @param img2 the second image strip
   * @return the SSIM score between [img1] and [img2], or 0.0 if dimensions differ or are invalid
   */
  private fun computeSsim(img1: BufferedImage, img2: BufferedImage): Double {
    val w = img1.width
    val h = img1.height
    if (w != img2.width || h != img2.height || w <= 0 || h <= 0) return 0.0

    val ssimR = computeChannelSsim(img1, img2, shift = 16)
    val ssimG = computeChannelSsim(img1, img2, shift = 8)
    val ssimB = computeChannelSsim(img1, img2, shift = 0)

    // Combine channel SSIMs using Rec. 601 luma weights based on human visual sensitivity (Red: 29.9%, Green: 58.7%, Blue: 11.4%).
    return 0.299 * ssimR + 0.587 * ssimG + 0.114 * ssimB
  }

  private fun computeChannelSsim(img1: BufferedImage, img2: BufferedImage, shift: Int): Double {
    val w = img1.width
    val h = img1.height
    val n = (w * h).toDouble()

    var sumX = 0.0
    var sumY = 0.0
    var sumX2 = 0.0
    var sumY2 = 0.0
    var sumXY = 0.0

    for (y in 0 until h) {
      for (x in 0 until w) {
        val val1 = ((img1.getRGB(x, y) shr shift) and 0xFF).toDouble()
        val val2 = ((img2.getRGB(x, y) shr shift) and 0xFF).toDouble()
        sumX += val1
        sumY += val2
        sumX2 += val1 * val1
        sumY2 += val2 * val2
        sumXY += val1 * val2
      }
    }

    val meanX = sumX / n
    val meanY = sumY / n
    val varX = (sumX2 / n) - (meanX * meanX)
    val varY = (sumY2 / n) - (meanY * meanY)
    val covXY = (sumXY / n) - (meanX * meanY)

    val k1 = 0.01
    val k2 = 0.03
    val l = 255.0
    val c1 = (k1 * l) * (k1 * l)
    val c2 = (k2 * l) * (k2 * l)

    val numerator = (2.0 * meanX * meanY + c1) * (2.0 * covXY + c2)
    val denominator = (meanX * meanX + meanY * meanY + c1) * (varX + varY + c2)

    return numerator / denominator
  }

  /** Checks if the specified file is a 3D scene file (.obj). */
  fun is3dSceneFile(file: Path): Boolean = file.fileName?.toString()?.endsWith(".obj", ignoreCase = true) ?: false

  /** Checks if the specified file is a video file (.mp4 or .webm). */
  fun isVideoFile(file: Path): Boolean {
    val name = file.fileName.toString()
    return name.endsWith(".mp4", ignoreCase = true) || name.endsWith(".webm", ignoreCase = true)
  }

  /** Checks if the specified file is a valid Wavefront OBJ file. */
  fun isWavefrontObjFile(path: Path): Boolean {
    return try {
      val maxBytes = 4096
      val bytes =
        Files.newInputStream(path).use { stream ->
          val buffer = ByteArray(maxBytes)
          val read = stream.read(buffer)
          if (read <= 0) return false
          buffer.copyOf(read)
        }
      if (bytes.contains(0.toByte())) {
        return false
      }
      val text = String(bytes, UTF_8)
      val lines = text.lines()
      val linesToCheck = if (bytes.size == maxBytes) lines.dropLast(1) else lines

      var hasVertices = false
      var hasFaces = false
      var hasComments = false
      var hasOtherKeywords = false

      val knownKeywords =
        setOf(
          "v",
          "vt",
          "vn",
          "vp",
          "f",
          "g",
          "o",
          "s",
          "usemtl",
          "mtllib",
          "l",
          "p",
          "deg",
          "bmt",
          "step",
          "cstype",
          "parm",
          "trim",
          "hole",
          "scrv",
          "sp",
          "end",
          "con",
          "bevel",
          "c_tech",
          "d_tech",
          "lod",
          "shadow_obj",
          "trace_obj",
          "ctech",
          "dtech",
        )

      for (line in linesToCheck) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue
        if (trimmed.startsWith("#")) {
          hasComments = true
          continue
        }
        val parts = trimmed.split(Regex("\\s+"), 2)
        val keyword = parts[0]
        if (keyword in knownKeywords) {
          when (keyword) {
            "v" -> hasVertices = true
            "f" -> hasFaces = true
            else -> hasOtherKeywords = true
          }
        } else {
          if (!hasVertices && !hasFaces && !hasComments && !hasOtherKeywords) {
            return false
          }
        }
      }
      hasVertices || hasFaces || hasComments
    } catch (_: Exception) {
      false
    }
  }
}
