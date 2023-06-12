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

import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.material.icons.common.MaterialIconsUrlProvider
import com.android.tools.idea.material.icons.metadata.MaterialIconsMetadata
import com.android.tools.idea.material.icons.metadata.MaterialMetadataIcon
import com.android.tools.idea.material.icons.utils.MaterialIconsUtils
import com.android.tools.idea.material.icons.utils.MaterialIconsUtils.toDirFormat
import com.android.utils.SdkUtils
import com.google.common.truth.Truth
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import java.net.JarURLConnection
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.stream.Stream
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.assertFailsWith

class MaterialVdIconsLoaderTest {

  @Test
  fun testLoaderWithMockJarProvider() {
    val metadata = createMaterialIconsMetadata()
    val loader = MaterialVdIconsLoader(metadata, MockStyleJarUrlProvider())
    val icons = loader.loadAll(metadata)
    checkIcons(icons)
  }

  @Test
  fun testLoaderWithMockFileProviderUsingWhitespace() {
    val metadata = createMaterialIconsMetadata()
    val loader = MaterialVdIconsLoader(metadata, TempFileUrlProvider("with White Spaces"))
    val icons = loader.loadAll(metadata)
    checkIcons(icons)
  }

  @Test
  fun testIllegalArgumentException() {
    val metadata = createMaterialIconsMetadata()
    val loader = MaterialVdIconsLoader(metadata, MaterialIconsTestUrlProvider())
    val exception = assertFailsWith<IllegalArgumentException> { loader.loadMaterialVdIcons("style") }
    Truth.assertThat(exception.message).isEqualTo("Style: style not part of the metadata.")
  }

  @Test
  fun testLoaderWithTestProvider() {
    val metadata = createMaterialIconsMetadata()
    val loader = MaterialVdIconsLoader(metadata, MaterialIconsTestUrlProvider())
    val icons = loader.loadAll(metadata)
    checkIcons(icons)
  }

  @Test
  fun testIncrementalLoadWithTestProvider() {
    val metadata = createMaterialIconsMetadata()
    var icons: MaterialVdIcons

    val loader1 = MaterialVdIconsLoader(metadata, MaterialIconsTestUrlProvider())
    // Load only "style 1" icons
    icons = loader1.loadMaterialVdIcons("style 1")
    checkStyle1Icons(icons)
    assertFalse(icons.styles.contains("style 2"))

    val loader2 = MaterialVdIconsLoader(metadata, MaterialIconsTestUrlProvider())
    // Load only "style 2" icons with a new loader
    icons = loader2.loadMaterialVdIcons("style 2")
    checkStyle2Icons(icons)
    assertFalse(icons.styles.contains("style 1"))

    // Check all icons are loaded by re-using the loaders
    icons = loader2.loadMaterialVdIcons("style 1")
    checkIcons(icons)
    icons = loader1.loadMaterialVdIcons("style 2")
    checkIcons(icons)
  }

  @Test
  fun testLoadIconsWithFileProviderAndMissingFile() {
    val iconsMetadataArray = createMaterialMetadataIconArray().toMutableList().apply {
      add(
        // Add a reference to an icon that doesn't exist.
        MaterialMetadataIcon(
          name = "fake",
          version = 1,
          categories = emptyArray(),
          unsupportedFamilies = emptyArray(),
          tags = emptyArray()
        )
      )
    }.toTypedArray()
    val metadata = MaterialIconsMetadata(
      host = "",
      urlPattern = "",
      families = arrayOf("style 1", "style 2"),
      icons = iconsMetadataArray
    )
    val icons = MaterialVdIconsLoader(metadata, TempFileUrlProvider("withMissingFile")).loadAll(metadata)
    // The check should still pass since the non-existing icon shouldn't load and is skipped.
    checkIcons(icons)
  }

  /**
   * Check that the contents of [MaterialVdIcons] are correct.
   *
   * The categories should match the icons they are referenced in the metadata.
   *
   * Icons and categories should be alphabetically ordered.
   *
   * The icons loaded should match only the icons listed in the metadata. Note that test resources intentionally contain extra or misplaced
   * icons.
   */
  private fun checkIcons(icons: MaterialVdIcons) {
    Truth.assertThat(icons.styles).hasLength(2)
    checkStyle1Icons(icons)
    checkStyle2Icons(icons)
  }

  private fun checkStyle1Icons(icons: MaterialVdIcons) {
    assertTrue(icons.styles.contains("style 1"))
    val style1Categories = icons.getCategories("style 1")
    Truth.assertThat(style1Categories).hasLength(3)
    Truth.assertThat(style1Categories[0]).isEqualTo("category1")
    Truth.assertThat(style1Categories[1]).isEqualTo("category2")
    Truth.assertThat(style1Categories[2]).isEqualTo("category3")
    val style1Icons = icons.getAllIcons("style 1")
    Truth.assertThat(style1Icons).hasLength(2)
    Truth.assertThat(style1Icons[0].name).isEqualTo("style1_my_icon_1_24.xml")
    Truth.assertThat(style1Icons[1].name).isEqualTo("style1_my_icon_2_24.xml")
    Truth.assertThat(icons.getIcons("style 1", "category1")).hasLength(2)
    Truth.assertThat(icons.getIcons("style 1", "category2")).hasLength(1)
    Truth.assertThat(icons.getIcons("style 1", "category2")).hasLength(1)
  }

  private fun checkStyle2Icons(icons: MaterialVdIcons) {
    assertTrue(icons.styles.contains("style 2"))
    val style2Categories = icons.getCategories("style 2")
    Truth.assertThat(style2Categories).hasLength(3)
    Truth.assertThat(style2Categories[0]).isEqualTo("category1")
    Truth.assertThat(style2Categories[1]).isEqualTo("category2")
    Truth.assertThat(style2Categories[2]).isEqualTo("category3")
    val style2Icons = icons.getAllIcons("style 2")
    Truth.assertThat(style2Icons).hasLength(2)
    Truth.assertThat(style2Icons[0].name).isEqualTo("style2_my_icon_1_24.xml")
    Truth.assertThat(style2Icons[1].name).isEqualTo("style2_my_icon_2_24.xml")
    Truth.assertThat(icons.getIcons("style 2", "category1")).hasLength(2)
    Truth.assertThat(icons.getIcons("style 2", "category2")).hasLength(1)
    Truth.assertThat(icons.getIcons("style 2", "category2")).hasLength(1)
  }
}

/**
 * [MaterialIconsUrlProvider] implementation that returns a [URL] with a mocked [JarFile] for [MaterialIconsUrlProvider.getStyleUrl] and
 * references the test resources for [MaterialIconsUrlProvider.getIconUrl].
 */
private class MockStyleJarUrlProvider : MaterialIconsUrlProvider {

  private val jarUrls: Map<String, URL?> = mapOf(
    Pair("images/material/icons/style1", createMockJarUrl("style1")),
    Pair("images/material/icons/style1/", null),
    Pair("images/material/icons/style2", createMockJarUrl("style2")),
    Pair("images/material/icons/style2/", null)
  )

  override fun getStyleUrl(style: String): URL? = jarUrls[MaterialIconsUtils.getBundledStyleDirectoryPath(style)]

  override fun getIconUrl(style: String, iconName: String, iconFileName: String): URL? {
    return MaterialVdIconsLoaderTest::class.java.classLoader.getResource(
      MaterialIconsUtils.getBundledIconPath(style, iconName, iconFileName)
    )
  }

  private fun createMockJarUrl(style: String): URL {
    val jarMock = Mockito.mock(JarFile::class.java)
    whenever(jarMock.stream()).thenAnswer {
      Stream.of(
        JarEntry("images/material/icons/$style"),
        JarEntry("images/material/icons/$style/my_icon_1"),
        JarEntry("images/material/icons/$style/my_icon_1/${style}_my_icon_1_24.xml"),
        JarEntry("images/material/icons/$style/my_icon_2"),
        JarEntry("images/material/icons/$style/my_icon_2/${style}_my_icon_2_24.xml")
      )
    }
    val jarConnectionMock = Mockito.mock(JarURLConnection::class.java)
    whenever(jarConnectionMock.jarFile).thenReturn(jarMock)
    val jarUrlHandler = object : URLStreamHandler() {
      override fun openConnection(u: URL?): URLConnection {
        return jarConnectionMock
      }
    }
    return URL("jar", "", -1, "", jarUrlHandler)
  }
}

/**
 * [MaterialIconsUrlProvider] implementation that returns [URL]s based on a temp directory.
 *
 * Populated similarly to the test resources in /images/material/icons.
 */
private class TempFileUrlProvider(tempDirPrefix: String) : MaterialIconsUrlProvider {
  private val tempDirPath = createTempDirectory(tempDirPrefix)

  private val styleToStyleDirUrl: Map<String, URL> = mapOf(
    Pair("style 1", populateStylePathAndReturnUrl("style1")),
    Pair("style 2", populateStylePathAndReturnUrl("style2"))
  )

  override fun getStyleUrl(style: String): URL? = styleToStyleDirUrl[style]

  override fun getIconUrl(style: String, iconName: String, iconFileName: String): URL? {
    return SdkUtils.fileToUrl(
      tempDirPath.resolve(style.toDirFormat()).resolve(iconName).resolve(iconFileName).toFile()
    )
  }

  private fun populateStylePathAndReturnUrl(styleDir: String): URL {
    val stylePath = tempDirPath.resolve(styleDir).apply { createDirectories() }.also {
      it.resolve("my_icon_1").apply { createDirectory() }.resolve("${styleDir}_my_icon_1_24.xml").writeText(SIMPLE_VD)
      it.resolve("my_icon_2").apply { createDirectory() }.resolve("${styleDir}_my_icon_2_24.xml").writeText(SIMPLE_VD)
      it.resolve("my_icon_3").apply { createDirectory() }.resolve("${styleDir}_my_icon_3_24.xml").writeText(SIMPLE_VD)
      it.resolve("my_icon_3.xml").writeText(SIMPLE_VD)
    }
    return SdkUtils.fileToUrl(stylePath.toFile())
  }
}

private class MaterialIconsTestUrlProvider : MaterialIconsUrlProvider {
  override fun getStyleUrl(style: String): URL? {
    return MaterialVdIconsLoaderTest::class.java.classLoader.getResource(MaterialIconsUtils.getBundledStyleDirectoryPath(style))
  }

  override fun getIconUrl(style: String, iconName: String, iconFileName: String): URL? {
    return MaterialVdIconsLoaderTest::class.java.classLoader.getResource(
      MaterialIconsUtils.getBundledIconPath(style, iconName, iconFileName)
    )
  }
}

private fun createMaterialIconsMetadata(): MaterialIconsMetadata =
  MaterialIconsMetadata(
    host = "",
    urlPattern = "",
    families = arrayOf("style 1", "style 2"),
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

private fun MaterialVdIconsLoader.loadAll(metadata: MaterialIconsMetadata): MaterialVdIcons {
  var icons: MaterialVdIcons = MaterialVdIcons.EMPTY
  metadata.families.forEach { icons = loadMaterialVdIcons(it) }
  return icons
}