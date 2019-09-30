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
package com.android.tools.idea.npw.assetstudio

import com.android.tools.idea.material.icons.MaterialIconsUrlProvider
import com.google.common.truth.Truth
import org.junit.Test
import java.net.URL
import java.util.Locale
import kotlin.test.assertEquals

private const val TEST_PATH = "images/material/icons/"
private const val METADATA_FILE_NAME = "icons_metadata_test.txt"

class MaterialVdIconsProviderTest {

  @Test
  fun testGetMaterialIcons() {
    val materialIcons = with(MaterialVdIconsProvider(MaterialIconsMetadataTestUrlProvider(), MaterialIconsTestUrlProvider())) {
      getMaterialIcons().get()
    }
    Truth.assertThat(materialIcons.styles).hasLength(2)
    assertEquals(materialIcons.styles[0], "Style 1")
    assertEquals(materialIcons.styles[1], "Style 2")
    Truth.assertThat(materialIcons.getCategories("Style 1")).asList().containsAllIn(materialIcons.getCategories("Style 2"))
    val icons = materialIcons.getAllIcons("Style 1")
    Truth.assertThat(icons).hasLength(2)
    assertEquals(icons[0].name, "my_icon_1.xml")
    assertEquals(icons[1].name, "my_icon_2.xml")
  }

  @Test
  fun testBadMetadataProviderReturnsEmptyStyles() {
    val materialIcons = with(MaterialVdIconsProvider(object : MaterialIconsMetadataUrlProvider {
      override fun getMetadataUrl(): URL? = null
    })) {
      getMaterialIcons().get()
    }

    Truth.assertThat(materialIcons.styles).isEmpty()
  }

  @Test
  fun testBadLoaderProviderReturnsEmptyIcons() {
    val materialIcons = with(MaterialVdIconsProvider(MaterialIconsMetadataTestUrlProvider(), object : MaterialIconsUrlProvider {
      override fun getStyleUrl(style: String): URL? = null
      override fun getIconUrl(style: String, iconName: String, iconFileName: String): URL? = null
    })) {
      getMaterialIcons().get()
    }

    Truth.assertThat(materialIcons.styles).hasLength(2)
    assertEquals(materialIcons.styles[0], "Style 1")
    assertEquals(materialIcons.styles[1], "Style 2")

    Truth.assertThat(materialIcons.getAllIcons("Style 1")).isEmpty()
    Truth.assertThat(materialIcons.getAllIcons("Style 2")).isEmpty()
  }
}

private class MaterialIconsMetadataTestUrlProvider : MaterialIconsMetadataUrlProvider {
  override fun getMetadataUrl(): URL? =
    MaterialVdIconsProviderTest::class.java.classLoader.getResource("$TEST_PATH$METADATA_FILE_NAME")
}

private class MaterialIconsTestUrlProvider : MaterialIconsUrlProvider {
  override fun getStyleUrl(style: String): URL? {
    return MaterialVdIconsProviderTest::class.java.classLoader.getResource(getStylePath(style))
  }

  override fun getIconUrl(style: String, iconName: String, iconFileName: String): URL? {
    return MaterialVdIconsProviderTest::class.java.classLoader.getResource("${getStylePath(style)}$iconName/$iconFileName")
  }

  private fun getStylePath(style: String): String {
    return "$TEST_PATH${style.toLowerCase(Locale.US).replace(" ", "")}/"
  }
}