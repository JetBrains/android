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
package com.android.tools.idea.material.icons

import com.android.ide.common.vectordrawable.VdIcon
import com.android.tools.idea.material.icons.metadata.MaterialIconsMetadata
import com.android.tools.idea.material.icons.metadata.MaterialMetadataIcon
import com.android.utils.SdkUtils
import com.intellij.openapi.util.io.FileUtil
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val INCOMPLETE_METADATA =
  ")]}'\n" +
  "{\n" +
  "  \"host\": \"\",\n" +
  "  \"asset_url_pattern\": \"\",\n" +
  "  \"families\": [\n" +
  "    \"Style 1\",\n" +
  "    \"Style 2\"\n" +
  "  ],\n" +
  "  \"icons\": [\n" +
  "    {\n" +
  "      \"name\": \"my_icon_1\",\n" +
  "      \"version\": 1,\n" +
  "      \"unsupported_families\": [],\n" +
  "      \"categories\": [\n" +
  "        \"category1\",\n" +
  "        \"category2\"\n" +
  "      ],\n" +
  "      \"tags\": []\n" +
  "    }\n" +
  "  ]\n" +
  "}"

class MaterialIconsCopyHandlerTest {

  private lateinit var testDirectory: File

  @Before
  fun setup() {
    testDirectory = FileUtil.createTempDirectory(javaClass.simpleName, null)
  }

  @Test
  fun copyToDirectory() {
    val targetDir = testDirectory.resolve("icons").apply { mkdir() }
    val metadata = createMaterialIconsMetadata()
    val vdIcons = createMaterialVdIcons(testDirectory)
    val copyHandler = MaterialIconsCopyHandler(metadata, vdIcons)

    assertTrue(targetDir.list()!!.isEmpty())
    copyHandler.copyTo(targetDir)
    assertEquals(3, targetDir.list()!!.size)
    assertTrue(targetDir.resolve("icons_metadata.txt").exists())

    assertStyleDir(targetDir.resolve("style1"))
    assertStyleDir(targetDir.resolve("style2"))
  }

  @Test
  fun errorWhenCopyToNonDir() {
    val targetDir = testDirectory.resolve("icons.txt").apply { writeText("") }
    val metadata = createMaterialIconsMetadata()
    val vdIcons = createMaterialVdIcons(testDirectory)
    val copyHandler = MaterialIconsCopyHandler(metadata, vdIcons)

    assertTrue(targetDir.exists())
    assertFalse(targetDir.isDirectory)
    assertFailsWith<IllegalArgumentException> { copyHandler.copyTo(targetDir) }
  }

  @Test
  fun finishInterruptedCopy() {
    val targetDir = testDirectory.resolve("icons").apply { mkdir() }
    val metadata = createMaterialIconsMetadata()
    val vdIcons = createMaterialVdIcons(testDirectory)
    val copyHandler = MaterialIconsCopyHandler(metadata, vdIcons)

    setupIncompleteCopy(targetDir)

    assertTrue(targetDir.resolve("icons_metadata_temp_copy.txt").exists())

    val iconFile = targetDir.resolve("style1").resolve("my_icon_1").resolve("my_icon_1.xml")
    assertTrue(iconFile.exists())
    val lastModified = iconFile.lastModified()

    copyHandler.copyTo(targetDir)

    assertTrue(targetDir.resolve("icons_metadata.txt").exists())
    // The temporary metadata file should stop existing after copy
    assertFalse(targetDir.resolve("icons_metadata_temp_copy.txt").exists())

    // Existing drawable files should not be modified during copy
    assertEquals(lastModified, iconFile.lastModified())

    assertStyleDir(targetDir.resolve("style1"))
    assertStyleDir(targetDir.resolve("style2"))
  }
}

private fun createMaterialIconsMetadata(): MaterialIconsMetadata =
  MaterialIconsMetadata(
    host = "",
    urlPattern = "",
    families = arrayOf("Style 1", "Style 2"),
    icons = createMaterialMetadataIconArray()
  )

private fun createMaterialMetadataIconArray(): Array<MaterialMetadataIcon> = arrayOf(
  MaterialMetadataIcon(
    name = "my_icon_1",
    version = 1,
    unsupportedFamilies = emptyArray(),
    categories = arrayOf("category1", "category2"),
    tags = emptyArray()
  ),
  MaterialMetadataIcon(
    name = "my_icon_2",
    version = 1,
    unsupportedFamilies = emptyArray(),
    categories = arrayOf("category1", "category3"),
    tags = emptyArray()
  )
)

private fun createMaterialVdIcons(dir: File): MaterialVdIcons {
  val stylesToSortedIcons = mapOf<String, Array<VdIcon>>(
    Pair("Style 1", arrayOf(createVdIcon(dir, "my_icon_1"), createVdIcon(dir, "my_icon_2"))),
    Pair("Style 2", arrayOf(createVdIcon(dir, "my_icon_1"), createVdIcon(dir, "my_icon_2")))
  )
  val stylesCategoriesToIcons = mapOf<String, HashMap<String, Array<VdIcon>>>(
    // No need to properly populate.
    Pair("Style 1", HashMap()),
    Pair("Style 2", HashMap())
  )
  return MaterialVdIcons(stylesCategoriesToIcons, stylesToSortedIcons)
}

private fun createVdIcon(dir: File, name: String): VdIcon {
  val iconFile = dir.resolve("test_icons").apply { mkdir() }.resolve("$name.xml").apply { writeText(SIMPLE_VD) }
  return VdIcon(SdkUtils.fileToUrl(iconFile))
}

private fun assertStyleDir(styleDir: File) {
  assertTrue(styleDir.exists() && styleDir.isDirectory)
  assertEquals(2, styleDir.list()!!.size)

  assertIconFile(styleDir, "my_icon_1")
  assertIconFile(styleDir, "my_icon_2")
}

private fun assertIconFile(parentDir: File, iconName: String) {
  val iconFile = parentDir.resolve(iconName).resolve("$iconName.xml")
  assertTrue(iconFile.exists())
  assertFalse(iconFile.isDirectory)
  assertEquals(SIMPLE_VD, iconFile.readText())
}

private fun setupIncompleteCopy(targetDir: File) {
  val incompleteMetadata = targetDir.resolve("icons_metadata_temp_copy.txt").apply { writeText(INCOMPLETE_METADATA) }
  assertTrue(incompleteMetadata.exists())
  assertFalse(incompleteMetadata.isDirectory)
  targetDir.resolve("style1").resolve("my_icon_1").apply { mkdirs() }.resolve("my_icon_1.xml").writeText(SIMPLE_VD)
  targetDir.resolve("style2").resolve("my_icon_1").apply { mkdirs() }.resolve("my_icon_1.xml").writeText(SIMPLE_VD)
}