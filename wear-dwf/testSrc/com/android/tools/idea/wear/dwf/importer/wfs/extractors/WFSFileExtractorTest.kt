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
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WFSFileExtractorTest {

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
  fun `test extract example WFS file`() = runTest {
    val extractor = WFSFileExtractor(StandardTestDispatcher(testScheduler))
    val wfsFile =
      LocalFileSystem.getInstance().findFileByNioFile(testDataPath.resolve("wfs/example.wfs"))
        ?: error("expected WFS file to exist")

    val mainFolderPath = fixture.tempDirPath.toNioPathOrNull() ?: error("expected path to be valid")
    val resFolderPath = fixture.tempDirFixture.findOrCreateDir("res").toNioPath()

    extractor.extract(wfsFile, mainFolderPath, resFolderPath)

    fixture.checkResultByFile("res/raw/watchface.xml", "wfs/expected/raw/watchface.xml", true)
    fixture.checkResultByFile("res/xml/watch_face.xml", "wfs/expected/xml/watch_face.xml", true)
    fixture.checkResultByFile(
      "res/xml/watch_face_info.xml",
      "wfs/expected/xml/watch_face_info.xml",
      true,
    )
    fixture.checkResultByFile(
      "res/xml/watch_face_shapes.xml",
      "wfs/expected/xml/watch_face_shapes.xml",
      true,
    )
    assertThat(fixture.findFileInTempDir("res/drawable-nodpi/preview.png").exists()).isTrue()
    assertThat(fixture.findFileInTempDir("res/drawable-nodpi/").children).hasLength(68)

    assertThat(fixture.tempDirFixture.getFile("res/values/strings.xml")).isNull()
    assertThat(fixture.tempDirFixture.getFile("honeyface.json")).isNull()
    assertThat(fixture.tempDirFixture.getFile("res/drawable-nodpi/preview_circular.png")).isNull()
  }
}
