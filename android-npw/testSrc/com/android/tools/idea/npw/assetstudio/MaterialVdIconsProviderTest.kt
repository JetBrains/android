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

import com.android.prefs.AndroidLocationsSingleton
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.npw.assetstudio.material.icons.MaterialVdIcons
import com.android.tools.idea.npw.assetstudio.material.icons.common.MaterialIconsMetadataUrlProvider
import com.android.tools.idea.npw.assetstudio.material.icons.common.MaterialIconsUrlProvider
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.ApplicationRule
import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.net.URL
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val TEST_PATH = "images/material/icons/"
private const val METADATA_FILE_NAME = "icons_metadata_test.txt"
private const val WAIT_TIMEOUT_SECONDS = 10L
private val TIMEOUT_UNIT = TimeUnit.SECONDS

class MaterialVdIconsProviderTest {

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  private lateinit var disposable: Disposable

  @Before
  fun setup() {
    disposable = Disposer.newDisposable()
  }

  @After
  fun teardown() {
    Disposer.dispose(disposable)
  }

  @Test
  fun testGetMaterialIcons() {
    val latch = CountDownLatch(2) // A call for each style: "Style 1", "Style 2"
    var materialIcons = MaterialVdIcons.EMPTY
    val uiCallback: (MaterialVdIcons, MaterialVdIconsProvider.Status) -> Unit = { icons, _ ->
      materialIcons = icons
      latch.countDown()
    }
    MaterialVdIconsProvider.loadMaterialVdIcons(
      uiCallback, MaterialIconsMetadataTestUrlProvider(), MaterialIconsTestUrlProvider(), disposable)
    assertTrue(latch.await(WAIT_TIMEOUT_SECONDS, TIMEOUT_UNIT))
    Truth.assertThat(materialIcons.styles).hasLength(2)
    assertEquals("Style 1", materialIcons.styles[0])
    assertEquals("Style 2", materialIcons.styles[1])
    Truth.assertThat(materialIcons.getCategories("Style 1")).asList().containsAllIn(materialIcons.getCategories("Style 2"))
    val icons = materialIcons.getAllIcons("Style 1")
    Truth.assertThat(icons).hasLength(2)
    assertEquals("style1_my_icon_1_24.xml", icons[0].name)
    assertEquals("style1_my_icon_2_24.xml", icons[1].name)
  }

  @Test
  fun testBadMetadataProviderReturnsEmptyStyles() {
    val latch = CountDownLatch(1) // 1 call from having a provider that doesn't return a metadata
    var materialIcons: MaterialVdIcons? = null
    val uiCallback: (MaterialVdIcons, MaterialVdIconsProvider.Status) -> Unit = { icons, status ->
      materialIcons = icons
      assertEquals(MaterialVdIconsProvider.Status.FINISHED, status)
      latch.countDown()
    }
    MaterialVdIconsProvider.loadMaterialVdIcons(uiCallback, object : MaterialIconsMetadataUrlProvider {
      override fun getMetadataUrl(): URL? = null
    }, null, disposable)
    assertTrue(latch.await(WAIT_TIMEOUT_SECONDS, TIMEOUT_UNIT))
    Truth.assertThat(materialIcons!!.styles).isEmpty()
  }

  @Test
  fun testBadLoaderProviderReturnsEmptyIcons() {
    val latch = CountDownLatch(2) // A call for each style: "Style 1", "Style 2"
    var icons: MaterialVdIcons? = null
    val uiCallback: (MaterialVdIcons, MaterialVdIconsProvider.Status) -> Unit = { materialIcons, _ ->
      icons = materialIcons
      latch.countDown()
    }
    MaterialVdIconsProvider.loadMaterialVdIcons(uiCallback, MaterialIconsMetadataTestUrlProvider(), object : MaterialIconsUrlProvider {
      override fun getStyleUrl(style: String): URL? = null
      override fun getIconUrl(style: String, iconName: String, iconFileName: String): URL? = null
    }, disposable)
    assertTrue(latch.await(WAIT_TIMEOUT_SECONDS, TIMEOUT_UNIT))
    val materialIcons = icons!!
    Truth.assertThat(materialIcons.styles).hasLength(2)
    assertEquals("Style 1", materialIcons.styles[0])
    assertEquals("Style 2", materialIcons.styles[1])

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
    return "$TEST_PATH${style.lowercase(Locale.US).replace(" ", "")}/"
  }
}

private const val SIMPLE_METADATA =
  ")]}'\n" +
  "{\n" +
  "  \"host\": \"\",\n" +
  "  \"asset_url_pattern\": \"\",\n" +
  "  \"families\": [\n" +
  "    \"Style 1\"\n" +
  "  ],\n" +
  "  \"icons\": [\n" +
  "    {\n" +
  "      \"name\": \"my_sdk_icon\",\n" +
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

@Language("XML")
private const val SIMPLE_VD =
  "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
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


class MaterialVdIconsProviderTestWithSdk {

  @get:Rule
  val rule = AndroidProjectRule.inMemory()

  @Before
  fun setup() {
    val testDirectory = FileUtil.createTempDirectory(javaClass.simpleName, null)
    val testSdkDirectory = testDirectory.resolve("FakeSdk").apply { mkdir() }
    val testMaterialIconsSdkDirectory = testSdkDirectory.resolve("icons").resolve("material").apply { mkdirs() }
    testMaterialIconsSdkDirectory.resolve("icons_metadata.txt").writeText(SIMPLE_METADATA)
    testMaterialIconsSdkDirectory.resolve("style1").resolve("my_sdk_icon").apply { mkdirs() }.resolve(
      "style1_my_sdk_icon_24.xml").writeText(SIMPLE_VD)

    val sdkHandler = AndroidSdkHandler.getInstance(AndroidLocationsSingleton, testSdkDirectory.toPath())
    whenever(rule.mockService(AndroidSdks::class.java).tryToChooseSdkHandler()).thenReturn(sdkHandler)
  }

  @Test
  fun getMaterialIconsFromSdk() {
    val latch = CountDownLatch(1) // A call to load all icons of "Style 1"
    var materialIcons = MaterialVdIcons.EMPTY
    val uiCallback: (MaterialVdIcons, MaterialVdIconsProvider.Status) -> Unit = { icons, _ ->
      materialIcons = icons
      latch.countDown()
    }
    MaterialVdIconsProvider.loadMaterialVdIcons(uiCallback, null, null, // Use the 'real' URL providers
                                                rule.fixture.projectDisposable)
    assertTrue(latch.await(WAIT_TIMEOUT_SECONDS, TIMEOUT_UNIT))
    Truth.assertThat(materialIcons.styles).hasLength(1)
    assertEquals("Style 1", materialIcons.styles[0])
    val icons = materialIcons.getAllIcons("Style 1")
    Truth.assertThat(icons).hasLength(1)
    assertEquals("style1_my_sdk_icon_24.xml", icons[0].name)
  }
}