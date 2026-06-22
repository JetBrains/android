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
package com.android.tools.idea.avdmanager

import com.android.testutils.TestUtils
import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Test

/**
 * Tests to validate that the image files in `artwork/resources/device-art-resources/ai_glasses_device` contain the exact XMP metadata that
 * they currently contain.
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
  fun testIndoorStudyDarkMetadata() {
    val file = getGlassesDeviceArtPath("indoor-study-dark.jpg")
    val xmp = readXmpMetadata(file)
    assertThat(xmp)
      .isEqualTo(
        """
        <x:xmpmeta xmlns:x="adobe:ns:meta/">
         <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
          <rdf:Description rdf:about=""
            xmlns:dc="http://purl.org/dc/elements/1.1/"
            xmlns:androidemulator="urn:androidemulator:metadata:private:1.0">
           <dc:title>
            <rdf:Alt>
             <rdf:li xml:lang="x-default">Indoor Study Dark</rdf:li>
            </rdf:Alt>
           </dc:title>
           <androidemulator:isDefault>true</androidemulator:isDefault>
          </rdf:Description>
         </rdf:RDF>
        </x:xmpmeta>
        """
          .trimIndent()
      )
  }

  @Test
  fun testOutdoorCityBrightMetadata() {
    val file = getGlassesDeviceArtPath("outdoor-city-bright.jpg")
    val xmp = readXmpMetadata(file)
    assertThat(xmp)
      .isEqualTo(
        """
        <x:xmpmeta xmlns:x="adobe:ns:meta/">
         <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
          <rdf:Description rdf:about=""
            xmlns:dc="http://purl.org/dc/elements/1.1/">
           <dc:title>
            <rdf:Alt>
             <rdf:li xml:lang="x-default">Outdoor City Bright</rdf:li>
            </rdf:Alt>
           </dc:title>
          </rdf:Description>
         </rdf:RDF>
        </x:xmpmeta>
        """
          .trimIndent()
      )
  }

  @Test
  fun testOutdoorNatureBrightMetadata() {
    val file = getGlassesDeviceArtPath("outdoor-nature-bright.jpg")
    val xmp = readXmpMetadata(file)
    assertThat(xmp)
      .isEqualTo(
        """
        <x:xmpmeta xmlns:x="adobe:ns:meta/">
         <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
          <rdf:Description rdf:about=""
            xmlns:dc="http://purl.org/dc/elements/1.1/">
           <dc:title>
            <rdf:Alt>
             <rdf:li xml:lang="x-default">Outdoor Nature Bright</rdf:li>
            </rdf:Alt>
           </dc:title>
          </rdf:Description>
         </rdf:RDF>
        </x:xmpmeta>
        """
          .trimIndent()
      )
  }

  private fun getGlassesDeviceArtPath(fileName: String): Path {
    return TestUtils.resolveWorkspacePathUnchecked("tools/adt/idea/artwork/resources/device-art-resources/ai_glasses_device/$fileName")
  }

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
