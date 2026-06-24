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

import com.android.testutils.TestUtils
import com.google.common.truth.Truth.assertWithMessage
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

/**
 * Tests to validate that all image files in `artwork/resources/device-art-resources/ai_glasses_device` contain valid XMP metadata with a
 * title, and that exactly one of them is set as default.
 *
 * To recreate the XMP metadata from scratch, you can use the following commands:
 * ```bash
 * # 1. Prepare the XMP metadata for indoor-study-dark.jpg:
 * echo '<x:xmpmeta xmlns:x="adobe:ns:meta/">
 *  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
 *   <rdf:Description rdf:about=""
 *     xmlns:dc="http://purl.org/dc/elements/1.1/"
 *     xmlns:androidemulator="urn:androidemulator:metadata:private:1.0">
 *    <dc:title>
 *     <rdf:Alt>
 *      <rdf:li xml:lang="x-default">Indoor Study Dark</rdf:li>
 *     </rdf:Alt>
 *    </dc:title>
 *    <androidemulator:isDefault>true</androidemulator:isDefault>
 *   </rdf:Description>
 *  </rdf:RDF>
 * </x:xmpmeta>' | exiftool "-xmp<=-" indoor-study-dark.jpg
 *
 * # 2. Prepare the XMP metadata for outdoor-city-bright.jpg:
 * echo '<x:xmpmeta xmlns:x="adobe:ns:meta/">
 *  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
 *   <rdf:Description rdf:about=""
 *     xmlns:dc="http://purl.org/dc/elements/1.1/">
 *    <dc:title>
 *     <rdf:Alt>
 *      <rdf:li xml:lang="x-default">Outdoor City Bright</rdf:li>
 *     </rdf:Alt>
 *    </dc:title>
 *   </rdf:Description>
 *  </rdf:RDF>
 * </x:xmpmeta>' | exiftool "-xmp<=-" outdoor-city-bright.jpg
 *
 * # 3. Prepare the XMP metadata for outdoor-nature-bright.jpg:
 * echo '<x:xmpmeta xmlns:x="adobe:ns:meta/">
 *  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
 *   <rdf:Description rdf:about=""
 *     xmlns:dc="http://purl.org/dc/elements/1.1/">
 *    <dc:title>
 *     <rdf:Alt>
 *      <rdf:li xml:lang="x-default">Outdoor Nature Bright</rdf:li>
 *     </rdf:Alt>
 *    </dc:title>
 *   </rdf:Description>
 *  </rdf:RDF>
 * </x:xmpmeta>' | exiftool "-xmp<=-" outdoor-nature-bright.jpg
 * ```
 *
 * Note: standard `exiftool` will add `<?xpacket?>` headers when writing, which is standard and parses cleanly.
 */
class AiGlassesDeviceArtMetadataTest {

  @Test
  fun testDeviceArtMetadata() {
    val dir = getGlassesDeviceArtDir()
    val files =
      Files.list(dir).use { stream ->
        stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".jpg", ignoreCase = true) }.collect(Collectors.toList())
      }
    assertWithMessage("No JPG files found in $dir").that(files).isNotEmpty()

    var defaultCount = 0
    for (file in files) {
      val xmp = readXmpMetadata(file)
      assertWithMessage("File $file does not contain XMP metadata").that(xmp).isNotNull()

      val document =
        DocumentBuilderFactory.newInstance()
          .apply { isNamespaceAware = true }
          .newDocumentBuilder()
          .parse(ByteArrayInputStream(xmp!!.toByteArray(Charsets.UTF_8)))

      val titles = document.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", "title")
      assertWithMessage("File $file does not contain exactly one dc:title element").that(titles.length).isEqualTo(1)
      val titleNode = titles.item(0)

      val rdfLis = (titleNode as Element).getElementsByTagNameNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "li")
      assertWithMessage("File $file dc:title does not contain a rdf:li element").that(rdfLis.length).isAtLeast(1)
      val titleText = rdfLis.item(0).textContent.trim()
      assertWithMessage("File $file has empty title").that(titleText).isNotEmpty()

      val isDefaultList = document.getElementsByTagNameNS("urn:androidemulator:metadata:private:1.0", "isDefault")
      val isDefault =
        if (isDefaultList.length > 0) {
          isDefaultList.item(0).textContent.trim().toBoolean()
        } else {
          false
        }
      if (isDefault) {
        defaultCount++
      }
    }

    assertWithMessage("Expected exactly 1 default environment image, but found $defaultCount").that(defaultCount).isEqualTo(1)
  }

  private fun getGlassesDeviceArtDir(): Path =
    TestUtils.resolveWorkspacePathUnchecked("tools/adt/idea/artwork/resources/device-art-resources/ai_glasses_device")

  private fun readXmpMetadata(file: Path): String? {
    val bytes = Files.readAllBytes(file)
    val content = String(bytes, Charsets.UTF_8).replace("\r\n", "\n")
    val startTag = "<x:xmpmeta"
    val endTag = "</x:xmpmeta>"
    val startIndex = content.indexOf(startTag)
    if (startIndex == -1) return null
    val endIndex = content.indexOf(endTag, startIndex)
    if (endIndex == -1) return null
    return content.substring(startIndex, endIndex + endTag.length)
  }
}
