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
import com.intellij.openapi.util.io.FileUtil
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.io.File
import java.net.JarURLConnection
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.util.Locale
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.stream.Stream

private const val PATH = "images/material/icons/"
private const val SIMPLE_VD = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                              "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                              "    android:height=\"100dp\"\n" +
                              "    android:width=\"100dp\"\n" +
                              "    android:viewportHeight=\"100\"\n" +
                              "    android:viewportWidth=\"100\">\n" +
                              "  <path\n" +
                              "      android:fillColor=\"#FF000000\"\n" +
                              "      android:pathData=\"M 0,0 L 100,0 0,100 z\" />\n" +
                              "\n" +
                              "</vector>"

class MaterialVdIconsLoaderTest {

  @Test
  fun testLoaderWithMockJarProvider() {
    val metadata = createMaterialIconsMetadata()
    val loader = MaterialVdIconsLoader(metadata, MockStyleJarUrlProvider())
    checkIcons(loader.getMaterialVdIcons())
  }

  @Test
  fun testLoaderWithMockFileProvider() {
    val metadata = createMaterialIconsMetadata()
    val loader = MaterialVdIconsLoader(metadata, FakeStyleFileUrlProvider())
    checkIcons(loader.getMaterialVdIcons())
  }

  @Test
  fun testLoaderWithTestProvider() {
    val metadata = createMaterialIconsMetadata()
    val loader = MaterialVdIconsLoader(metadata, MaterialIconsTestUrlProvider())
    checkIcons(loader.getMaterialVdIcons())
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
    assertTrue(icons.styles.contains("style 1"))
    assertTrue(icons.styles.contains("style 2"))
    val style1Categories = icons.getCategories("style 1")
    Truth.assertThat(style1Categories).hasLength(3)
    Truth.assertThat(style1Categories[0]).isEqualTo("category1")
    Truth.assertThat(style1Categories[1]).isEqualTo("category2")
    Truth.assertThat(style1Categories[2]).isEqualTo("category3")
    val style1Icons = icons.getAllIcons("style 1")
    Truth.assertThat(style1Icons).hasLength(2)
    Truth.assertThat(style1Icons[0].name).isEqualTo("my_icon_1.xml")
    Truth.assertThat(style1Icons[1].name).isEqualTo("my_icon_2.xml")
    Truth.assertThat(icons.getIcons("style 1", "category1")).hasLength(2)
    Truth.assertThat(icons.getIcons("style 1", "category2")).hasLength(1)
    Truth.assertThat(icons.getIcons("style 1", "category2")).hasLength(1)

    val style2Categories = icons.getCategories("style 2")
    Truth.assertThat(style2Categories).hasLength(3)
    Truth.assertThat(style2Categories[0]).isEqualTo("category1")
    Truth.assertThat(style2Categories[1]).isEqualTo("category2")
    Truth.assertThat(style2Categories[2]).isEqualTo("category3")
    val style2Icons = icons.getAllIcons("style 2")
    Truth.assertThat(style2Icons).hasLength(2)
    Truth.assertThat(style2Icons[0].name).isEqualTo("my_icon_1.xml")
    Truth.assertThat(style2Icons[1].name).isEqualTo("my_icon_2.xml")
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

  private val jarUrls: Map<String, URL> = mapOf(Pair("style 1", createMockJarUrl("style1")), Pair("style 2", createMockJarUrl("style2")))

  override fun getStyleUrl(style: String): URL? = jarUrls[style]

  override fun getIconUrl(style: String, iconName: String, iconFileName: String): URL? {
    return MaterialVdIconsLoaderTest::class.java.classLoader.getResource(
      "${PATH}${style.toLowerCase(Locale.US).replace(" ", "")}/$iconName/$iconFileName")
  }

  private fun createMockJarUrl(style: String): URL {
    val jarMock = Mockito.mock(JarFile::class.java)
    `when`(jarMock.stream()).thenAnswer {
      Stream.of(
        JarEntry("images/material/icons/$style/"),
        JarEntry("images/material/icons/$style/my_icon_1/"),
        JarEntry("images/material/icons/$style/my_icon_1/my_icon_1.xml"),
        JarEntry("images/material/icons/$style/my_icon_2/"),
        JarEntry("images/material/icons/$style/my_icon_2/my_icon_2.xml")
      )
    }
    val jarConnectionMock = Mockito.mock(JarURLConnection::class.java)
    `when`(jarConnectionMock.jarFile).thenReturn(jarMock)
    val jarUrlHandler = object : URLStreamHandler() {
      override fun openConnection(u: URL?): URLConnection {
        return jarConnectionMock
      }
    }
    return URL("jar", "", -1, "", jarUrlHandler)
  }
}

/**
 * [MaterialIconsUrlProvider] implementation that returns a [URL] with a temp [File] for [MaterialIconsUrlProvider.getStyleUrl] and
 * references the test resources for [MaterialIconsUrlProvider.getIconUrl].
 */
private class FakeStyleFileUrlProvider : MaterialIconsUrlProvider {

  private val fileUrls: Map<String, URL> = mapOf(Pair("style 1", createFakeFileUrl("style1")), Pair("style 2", createFakeFileUrl("style2")))

  override fun getStyleUrl(style: String): URL? = fileUrls[style]

  override fun getIconUrl(style: String, iconName: String, iconFileName: String): URL? {
    return MaterialVdIconsLoaderTest::class.java.classLoader.getResource(
      "${PATH}${style.toLowerCase(Locale.US).replace(" ", "")}/$iconName/$iconFileName")
  }

  private fun createFakeFileUrl(styleDir: String): URL {
    val styleFile = FileUtil.createTempDirectory(javaClass.simpleName, null).resolve("${PATH}$styleDir/").apply { mkdirs() }.also {
      it.resolve("my_icon_1").apply { mkdir() }.resolve("my_icon_1.xml").writeText(SIMPLE_VD)
      it.resolve("my_icon_2").apply { mkdir() }.resolve("my_icon_2.xml").writeText(SIMPLE_VD)
      it.resolve("my_icon_3").apply { mkdir() }.resolve("my_icon_3.xml").writeText(SIMPLE_VD)
      it.resolve("my_icon_3.xml").writeText(SIMPLE_VD)
    }
    return URL("file", "", -1, styleFile.path)
  }
}

private class MaterialIconsTestUrlProvider : MaterialIconsUrlProvider {
  override fun getStyleUrl(style: String): URL? {
    return MaterialVdIconsLoaderTest::class.java.classLoader.getResource(getStylePath(style))
  }

  override fun getIconUrl(style: String, iconName: String, iconFileName: String): URL? {
    return MaterialVdIconsLoaderTest::class.java.classLoader.getResource("${getStylePath(style)}$iconName/$iconFileName")
  }

  private fun getStylePath(style: String): String {
    return "${PATH}${style.toLowerCase(Locale.US).replace(" ", "")}/"
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