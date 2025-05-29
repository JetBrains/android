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
package com.android.tools.idea.wear.dwf.importer.wfs.extractors

import com.android.testutils.TestUtils.resolveWorkspacePath
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import kotlin.io.path.Path
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidAppBundleExtractorTest {

  private val testDataPath = resolveWorkspacePath("tools/adt/idea/wear-dwf/testData")

  @Test
  fun `test extract items`() = runTest {
    val extractor = AndroidAppBundleExtractor(StandardTestDispatcher(testScheduler))

    val extractedItems = extractor.extract(testDataPath.resolve("import/aab/example.aab")).toList()

    assertThat(extractedItems).hasSize(86)

    val extractedManifest = extractedItems.filterIsInstance<ExtractedItem.Manifest>().single()
    assertThat(StringUtil.convertLineSeparators(extractedManifest.content))
      .isEqualTo(
        testDataPath.resolve("import/aab/expected/AndroidManifest_extracted.xml").readText()
      )

    val stringFolders =
      extractedItems
        .filterIsInstance<ExtractedItem.StringResource>()
        .map { FileUtil.normalize(it.filePath.pathString) }
        .toSet()
    assertThat(stringFolders)
      .containsExactly(
        "res/values/strings.xml",
        "res/values-en/strings.xml",
        "res/values-es/strings.xml",
        "res/values-ko/strings.xml",
      )
    assertThat(extractedItems)
      .contains(
        ExtractedItem.StringResource(
          value = "Camping style",
          name = "ID_CAMPING_STYLE",
          filePath = Path("res/values/strings.xml"),
        )
      )
    assertThat(extractedItems)
      .contains(
        ExtractedItem.StringResource(
          value = "Estilo de camping",
          name = "ID_CAMPING_STYLE",
          filePath = Path("res/values-es/strings.xml"),
        )
      )

    val rawWatchFace =
      extractedItems.filterIsInstance<ExtractedItem.TextResource>().find {
        it.filePath == Path("res/raw/watchface.xml")
      }
    assertThat(rawWatchFace).isNotNull()
    assertThat(StringUtil.convertLineSeparators(rawWatchFace!!.text))
      .isEqualTo(
        testDataPath.resolve("import/aab/expected/res/raw/watchface_extracted.xml").readText()
      )
  }
}
