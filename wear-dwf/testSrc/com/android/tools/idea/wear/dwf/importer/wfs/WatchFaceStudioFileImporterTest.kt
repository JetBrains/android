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

import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WatchFaceStudioFileImporterTest {

  @get:Rule val projectRule = AndroidProjectRule.withAndroidModel()

  private val fixture
    get() = projectRule.fixture

  private val testDataPath = resolveWorkspacePath("tools/adt/idea/wear-dwf/testData")

  @Before
  fun setUp() {
    fixture.testDataPath = testDataPath.toString()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `test import example WFS file`() = runTest {
    val importer =
      WatchFaceStudioFileImporter.getInstanceForTest(
        project = projectRule.project,
        defaultDispatcher = StandardTestDispatcher(testScheduler),
        ioDispatcher = StandardTestDispatcher(testScheduler),
      )
    val wfsFile =
      LocalFileSystem.getInstance().findFileByNioFile(testDataPath.resolve("wfs/example.wfs"))
        ?: error("expected WFS file to exist")

    val result = importer.import(wfsFile)

    assertThat(result).isEqualTo(WFSImportResult.Success)
    fixture.checkResultByFile(
      "src/main/res/raw/watchface.xml",
      "wfs/expected/raw/watchface.xml",
      true,
    )
    fixture.checkResultByFile(
      "src/main/res/xml/watch_face.xml",
      "wfs/expected/xml/watch_face.xml",
      true,
    )
    fixture.checkResultByFile(
      "src/main/res/xml/watch_face_info.xml",
      "wfs/expected/xml/watch_face_info.xml",
      true,
    )
    fixture.checkResultByFile(
      "src/main/res/xml/watch_face_shapes.xml",
      "wfs/expected/xml/watch_face_shapes.xml",
      true,
    )
    assertThat(fixture.findFileInTempDir("src/main/res/drawable-nodpi/preview.png").exists())
      .isTrue()
    assertThat(fixture.findFileInTempDir("src/main/res/drawable-nodpi/").children).hasLength(68)

    assertThat(fixture.tempDirFixture.getFile("src/main/res/values/strings.xml")).isNull()
    assertThat(fixture.tempDirFixture.getFile("src/main/honeyface.json")).isNull()
    assertThat(fixture.tempDirFixture.getFile("src/main/res/drawable-nodpi/preview_circular.png"))
      .isNull()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `test import invalid file`() = runTest {
    val importer =
      WatchFaceStudioFileImporter.getInstanceForTest(
        project = projectRule.project,
        defaultDispatcher = StandardTestDispatcher(testScheduler),
        ioDispatcher = StandardTestDispatcher(testScheduler),
      )
    val invalidFile = fixture.addFileToProject("invalid.wfs", "")

    val result = importer.import(invalidFile.virtualFile)

    assertThat(result).isEqualTo(WFSImportResult.Error())
  }
}
