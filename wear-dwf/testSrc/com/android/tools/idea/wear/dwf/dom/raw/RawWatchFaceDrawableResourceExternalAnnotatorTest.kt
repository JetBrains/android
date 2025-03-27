/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.wear.dwf.dom.raw

import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.rendering.GutterIconRenderer
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.flags.overrideForTest
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.EssentialHighlightingMode
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RawWatchFaceDrawableResourceExternalAnnotatorTest {
  @get:Rule val projectRule = AndroidProjectRule.onDisk().initAndroid(true)

  private val fixture
    get() = projectRule.fixture

  @Before
  fun setup() {
    projectRule.fixture.testDataPath =
      resolveWorkspacePath("tools/adt/idea/wear-dwf/testData/").toString()

    // add a manifest file for the `res/` folder to be considered a resource folder
    fixture.addFileToProject(FN_ANDROID_MANIFEST_XML, "")
  }

  @After
  fun tearDown() {
    EssentialHighlightingMode.setEnabled(false)
  }

  @Test
  fun `resources are not annotated when the flag is disabled`() {
    StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT.overrideForTest(
      false,
      projectRule.testRootDisposable,
    )
    setupSampleDrawableResourcesFromWatchFaceExample()
    val watchFaceFile = fixture.copyFileToProject("res/raw/watch_face_example.xml")
    fixture.configureFromExistingVirtualFile(watchFaceFile)

    val annotations =
      fixture
        .doHighlighting()
        .filter { it.gutterIconRenderer is GutterIconRenderer }
        .map { it.text }

    assertThat(annotations).isEmpty()
  }

  @Test
  fun `resources are not annotated when the essentials highlighting mode is enabled`() {
    EssentialHighlightingMode.setEnabled(true)
    setupSampleDrawableResourcesFromWatchFaceExample()
    val watchFaceFile = fixture.copyFileToProject("res/raw/watch_face_example.xml")
    fixture.configureFromExistingVirtualFile(watchFaceFile)

    val annotations =
      fixture
        .doHighlighting()
        .filter { it.gutterIconRenderer is GutterIconRenderer }
        .map { it.text }

    assertThat(annotations).isEmpty()
  }

  @Test
  fun `resources and icons are annotated`() {
    val drawables = setupSampleDrawableResourcesFromWatchFaceExample()
    val watchFaceFile = fixture.copyFileToProject("res/raw/watch_face_example.xml")
    fixture.configureFromExistingVirtualFile(watchFaceFile)

    val annotations =
      fixture
        .doHighlighting()
        .filter { it.gutterIconRenderer is GutterIconRenderer }
        .map { it.text }

    val expected = drawables.map { "\"$it\"" }
    assertThat(annotations).containsExactlyElementsIn(expected)
  }

  /**
   * Adds some sampled icons and resources drawables referenced from
   * `testData/res/raw/watch_face_example.xml`. This method returns the drawables used.
   */
  private fun setupSampleDrawableResourcesFromWatchFaceExample(): List<String> {
    val icons =
      listOf(
        "style_wfs_40fc6b01_0756_400d_8903_20a8808c8115_0",
        // this icon is referenced twice
        "style_wfs_40fc6b01_0756_400d_8903_20a8808c8115_0",
        "style_wfs_40fc6b01_0756_400d_8903_20a8808c8115_1",
        "style_wfs_40fc6b01_0756_400d_8903_20a8808c8115_2",
      )
    // this list is not exhaustive, it's just a sample of the drawables referenced in
    // watch_face_example.xml
    val resources =
      listOf(
        "wfs_0_c779e5a8_9290_400f_a0ad_761627ba3685",
        "wfs_apr_a66646f8_2213_4c0b_af5e_c69869f74d38",
        "wfs_battery_index_14255200_2541_4cfb_be12_e203e0fd2ca0",
        "wfs_basic_c6ee15b8_c624_45ad_a293_a9c89f15672a",
      )

    // add the references as drawables for them to be resolved
    for (drawable in (icons + resources).distinct()) {
      fixture.addFileToProject("res/drawable/$drawable.png", "")
    }
    projectRule.waitForResourceRepositoryUpdates()

    return icons + resources
  }
}
