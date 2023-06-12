/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.material.icons.metadata

import com.android.utils.SdkUtils
import com.google.common.truth.Truth
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.io.FileUtil
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

class MaterialIconsMetadataTest {
  private lateinit var testDirectory: File
  private lateinit var testMetadataFile: File

  @Before
  fun setup() {
    testDirectory = FileUtil.createTempDirectory(MaterialIconsMetadataTest::class.java.simpleName, null)
    testMetadataFile = testDirectory.resolve("my_metadata_file.txt")
  }

  @Test
  fun testParse() {
    testMetadataFile.writeText(
      ")]}'\n" +
      "{\n" +
      "  \"host\": \"fonts.gstatic.com\",\n" +
      "  \"asset_url_pattern\": \"/s/i/{family}/{icon}/v{version}/{asset}\",\n" +
      "  \"families\": [\n" +
      "    \"Material Icons\",\n" +
      "    \"Material Icons Outlined\",\n" +
      "    \"Material Icons Round\",\n" +
      "    \"Material Icons Sharp\",\n" +
      "    \"Material Icons Two Tone\"\n" +
      "  ],\n" +
      "  \"icons\": [\n" +
      "    {\n" +
      "      \"name\": \"360\",\n" +
      "      \"version\": 1,\n" +
      "      \"unsupported_families\": [],\n" +
      "      \"categories\": [\n" +
      "        \"maps\"\n" +
      "      ],\n" +
      "      \"tags\": [\n" +
      "        \"plaît\",\n" +
      "        \"respond\",\n" +
      "        \"répondez\"\n" +
      "      ]\n" +
      "    }\n" +
      "  ]\n" +
      "}"
    )
    val iconsMetadata = MaterialIconsMetadata.parse(SdkUtils.fileToUrl(testMetadataFile), thisLogger())
    Truth.assertThat(iconsMetadata.families).hasLength(5)
    assertEquals("Material Icons", iconsMetadata.families[0])
    assertEquals("Material Icons Outlined", iconsMetadata.families[1])
    assertEquals("Material Icons Round", iconsMetadata.families[2])
    assertEquals("Material Icons Sharp", iconsMetadata.families[3])
    assertEquals("Material Icons Two Tone", iconsMetadata.families[4])
    Truth.assertThat(iconsMetadata.icons).hasLength(1)
    val iconMetadata = iconsMetadata.icons[0]
    assertEquals("360", iconMetadata.name)
    assertEquals(1, iconMetadata.version)
    Truth.assertThat(iconMetadata.unsupportedFamilies).isEmpty()
    assertEquals("maps", iconMetadata.categories[0])
    assertEquals(3, iconMetadata.tags.size)
    assertEquals("plaît", iconMetadata.tags[0])
    assertEquals("respond", iconMetadata.tags[1])
    assertEquals("répondez", iconMetadata.tags[2])
  }

  @Test
  fun testParseWithBadFile() {
    testMetadataFile.writeText("Hello, World!")

    assertEquals(MaterialIconsMetadata.EMPTY, getParsedTestMetadata())
  }

  @Test
  fun testParseToStringAndBack() {
    val iconsMetadata = arrayOf(
      MaterialMetadataIcon("icon_1", 1, emptyArray(), arrayOf("category_1"), emptyArray()),
      MaterialMetadataIcon("icon_2", 1, emptyArray(), arrayOf("category_2"), emptyArray())
    )
    // Create an instance of MaterialIconsMetadata
    val metadataObject = MaterialIconsMetadata(
      host = "my_host",
      urlPattern = "my_pattern",
      families = arrayOf("Style 1", "Style 2"),
      icons = iconsMetadata
    )
    // Create a Json String
    MaterialIconsMetadata.writeAsJson(metadataObject, testMetadataFile.toPath(), thisLogger())

    // Deserialize the Json String and compare resulting object
    assertEquals(metadataObject, getParsedTestMetadata())
  }

  private fun getParsedTestMetadata(): MaterialIconsMetadata =
    MaterialIconsMetadata.parse(SdkUtils.fileToUrl(testMetadataFile), thisLogger())
}