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
package com.android.tools.idea.wear.dwf.importer.wfs

import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchFaceStudioFileImporterTest {

  @get:Rule val projectRule = AndroidProjectRule.withAndroidModel()

  private val fixture
    get() = projectRule.fixture

  private val testDataPath = resolveWorkspacePath("tools/adt/idea/wear-dwf/testData")

  @Before
  fun setUp() {
    fixture.testDataPath = testDataPath.toString()
  }

  @Test
  fun `test import example AAB file`() = runTest {
    val importer =
      WatchFaceStudioFileImporter.getInstanceForTest(
        project = projectRule.project,
        defaultDispatcher = StandardTestDispatcher(testScheduler),
        ioDispatcher = StandardTestDispatcher(testScheduler),
      )
    val aabFilePath = testDataPath.resolve("import/example.aab")

    val result = importer.import(aabFilePath)

    assertThat(result).isEqualTo(WFSImportResult.Success)

    fixture.checkResultByFile(
      "src/main/$FN_ANDROID_MANIFEST_XML",
      "import/expected/$FN_ANDROID_MANIFEST_XML",
      true,
    )
    fixture.checkResultByFile(
      "src/main/res/raw/watchface.xml",
      "import/expected/res/raw/watchface.xml",
      true,
    )
    fixture.checkResultByFile(
      "src/main/res/xml/watch_face.xml",
      "import/expected/res/xml/watch_face.xml",
      true,
    )
    fixture.checkResultByFile(
      "src/main/res/xml/watch_face_info.xml",
      "import/expected/res/xml/watch_face_info.xml",
      true,
    )
    fixture.checkResultByFile(
      "src/main/res/xml/watch_face_shapes.xml",
      "import/expected/res/xml/watch_face_shapes.xml",
      true,
    )
    fixture.checkResultByFile(
      "src/main/res/values/strings.xml",
      "import/expected/res/values/strings.xml",
      true,
    )
    fixture.checkResultByFile(
      "src/main/res/values-es/strings.xml",
      "import/expected/res/values-es/strings.xml",
      true,
    )

    assertThat(fixture.findFileInTempDir("src/main/res/drawable-nodpi-v4/preview.png").exists())
      .isTrue()
    assertThat(fixture.findFileInTempDir("src/main/res/drawable-nodpi-v4/").children).hasLength(54)
  }

  @Test
  fun `import merges manifest with existing manifest`() = runTest {
    val importer =
      WatchFaceStudioFileImporter.getInstanceForTest(
        project = projectRule.project,
        defaultDispatcher = StandardTestDispatcher(testScheduler),
        ioDispatcher = StandardTestDispatcher(testScheduler),
      )
    val aabFilePath = testDataPath.resolve("import/example.aab")
    fixture.copyFileToProject(
      "import/AndroidManifest_existing.xml",
      "src/main/$FN_ANDROID_MANIFEST_XML",
    )

    val result = importer.import(aabFilePath)

    assertThat(result).isEqualTo(WFSImportResult.Success)

    fixture.checkResultByFile(
      "src/main/$FN_ANDROID_MANIFEST_XML",
      "import/expected/AndroidManifest_merged.xml",
      true,
    )
  }

  @Test
  fun `test import invalid file`() = runTest {
    val importer =
      WatchFaceStudioFileImporter.getInstanceForTest(
        project = projectRule.project,
        defaultDispatcher = StandardTestDispatcher(testScheduler),
        ioDispatcher = StandardTestDispatcher(testScheduler),
      )
    val invalidFile = fixture.addFileToProject("invalid.aab", "")

    val result = importer.import(invalidFile.virtualFile.toNioPath())

    assertThat(result).isEqualTo(WFSImportResult.Error())
  }
}
