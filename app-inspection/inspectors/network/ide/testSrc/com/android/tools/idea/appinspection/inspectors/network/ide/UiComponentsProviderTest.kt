/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.ide

import com.android.tools.adtui.stdui.ContentType
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.inspectors.common.ui.dataviewer.DataViewer
import com.android.tools.inspectors.common.ui.dataviewer.IntellijDataViewer
import com.android.tools.inspectors.common.ui.dataviewer.IntellijImageDataViewer
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.io.readBytes
import javax.swing.JLabel
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class UiComponentsProviderTest {

  @get:Rule val edtRule = EdtRule()

  @get:Rule val projectRule = AndroidProjectRule.onDisk()

  @Test
  fun createImageDataViewer() {
    val componentsProvider = DefaultUiComponentsProvider(projectRule.project)

    // Valid image results in the creation of an image data viewer.
    assertThat(
        componentsProvider.createDataViewer(
          TEST_IMAGE.readBytes(),
          ContentType.PNG,
          DataViewer.Style.RAW,
          false
        )
      )
      .isInstanceOf(IntellijImageDataViewer::class.java)

    val viewer =
      componentsProvider.createDataViewer(
        ByteArray(0),
        ContentType.GIF,
        DataViewer.Style.RAW,
        false
      )
    // Invalid image bytes in the creation of a regular data viewer with text saying preview is not
    // available.
    assertThat(viewer).isInstanceOf(IntellijDataViewer::class.java)
    assertThat((viewer.component as JLabel).text).isEqualTo("No preview available")
  }

  @Test
  fun createTextDataViewer() {
    val componentsProvider = DefaultUiComponentsProvider(projectRule.project)

    val viewer =
      componentsProvider.createDataViewer(
        "csv,file".toByteArray(),
        ContentType.CSV,
        DataViewer.Style.RAW,
        false
      )
    assertThat(viewer).isInstanceOf(IntellijDataViewer::class.java)
    assertThat(viewer.style).isEqualTo(DataViewer.Style.RAW)
  }

  @Test
  fun createPrettyDataViewer() {
    val componentsProvider = DefaultUiComponentsProvider(projectRule.project)

    val viewer =
      componentsProvider.createDataViewer(
        "<html></html>".toByteArray(),
        ContentType.HTML,
        DataViewer.Style.PRETTY,
        true
      )
    assertThat(viewer).isInstanceOf(IntellijDataViewer::class.java)
    assertThat(viewer.style).isEqualTo(DataViewer.Style.PRETTY)
  }
}
