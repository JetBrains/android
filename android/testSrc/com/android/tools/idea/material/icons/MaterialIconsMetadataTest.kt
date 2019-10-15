/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.material.icons

import com.google.common.truth.Truth
import com.google.gson.JsonParseException
import com.intellij.openapi.util.io.FileUtil
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MaterialIconsMetadataTest {

  lateinit var testDirectory: File

  @Before
  fun setup() {
    testDirectory = FileUtil.createTempDirectory(MaterialIconsMetadataTest::class.java.simpleName, null)
  }

  @Test
  fun testParse() {
    val testMetadataFile = testDirectory.resolve("my_metadata_file.txt").apply {
      writeText(
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
        "      \"tags\": []\n" +
        "    }\n" +
        "  ]\n" +
        "}"
      )
    }
    val iconsMetadata = MaterialIconsMetadata.parse(BufferedReader(InputStreamReader(testMetadataFile.inputStream())))
    Truth.assertThat(iconsMetadata.families).hasLength(5)
    assertEquals(iconsMetadata.families[0], "Material Icons")
    assertEquals(iconsMetadata.families[1], "Material Icons Outlined")
    assertEquals(iconsMetadata.families[2], "Material Icons Round")
    assertEquals(iconsMetadata.families[3], "Material Icons Sharp")
    assertEquals(iconsMetadata.families[4], "Material Icons Two Tone")
    Truth.assertThat(iconsMetadata.icons).hasLength(1)
    val iconMetadata = iconsMetadata.icons[0]
    assertEquals(iconMetadata.name, "360")
    assertEquals(iconMetadata.version, 1)
    Truth.assertThat(iconMetadata.unsupportedFamilies).isEmpty()
    assertEquals(iconMetadata.categories[0], "maps")
    Truth.assertThat(iconMetadata.tags).isEmpty()
  }

  @Test
  fun testParseWithBadFile() {
    val badMetadataFile = testDirectory.resolve("my_metadata_file.txt").apply { writeText("Hello, World!") }

    assertFailsWith<JsonParseException> { MaterialIconsMetadata.parse(BufferedReader(InputStreamReader(badMetadataFile.inputStream()))) }
  }
}