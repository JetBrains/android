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

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.text.Charsets.UTF_8
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser

data class EnvironmentImage(val path: Path, val title: String, val isDefault: Boolean)

internal data class XmpMetadata(val title: String, val isDefault: Boolean)

/** Functions for scanning directories and extracting XMP metadata from emulator virtual scene environment image files. */
internal object EnvironmentImageScanner {
  /**
   * Scans the specified directory for image files, parses their XMP metadata, and returns a list of [EnvironmentImage] records for files
   * that have a valid XMP title.
   */
  fun scanEnvironments(dir: Path): List<EnvironmentImage> {
    if (!Files.exists(dir)) return emptyList()
    val result = mutableListOf<EnvironmentImage>()
    Files.list(dir).use { stream ->
      stream
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
    return name.endsWith(".jpg", ignoreCase = true) || name.endsWith(".jpeg", ignoreCase = true)
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
    } catch (_: Exception) {
      null
    }
  }

  private fun extractXmpData(inputStream: InputStream): ByteArray? {
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
    try {
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
    } catch (_: Exception) {
      // Ignore
    }
    return null
  }
}
