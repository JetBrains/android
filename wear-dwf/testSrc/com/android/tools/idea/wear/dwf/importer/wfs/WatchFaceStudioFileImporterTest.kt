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
import com.android.tools.idea.run.configuration.AndroidDeclarativeWatchFaceConfiguration
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.wear.dwf.importer.wfs.WFSImportResult.Error.Type.UNSUPPORTED_FILE_EXTENSION
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchFaceStudioFileImporterTest {

  @get:Rule val projectRule = AndroidProjectRule.withAndroidModel(AndroidProjectBuilder())

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
    val aabFilePath = testDataPath.resolve("import/aab/example.aab")

    val result = importer.import(aabFilePath)

    assertThat(result).isEqualTo(WFSImportResult.Success)

    fixture.checkResultByFile(
      "src/main/$FN_ANDROID_MANIFEST_XML",
      "import/aab/expected/$FN_ANDROID_MANIFEST_XML",
      true,
    )
    fixture.checkResultByFile(
      "src/main/res/raw/watchface.xml",
      "import/aab/expected/res/raw/watchface.xml",
      true,
    )
    fixture.checkResultByFile(
      "src/main/res/xml/watch_face.xml",
      "import/aab/expected/res/xml/watch_face.xml",
      true,
    )
    fixture.checkResultByFile(
      "src/main/res/xml/watch_face_info.xml",
      "import/aab/expected/res/xml/watch_face_info.xml",
      true,
    )
    fixture.checkResultByFile(
      "src/main/res/xml/watch_face_shapes.xml",
      "import/aab/expected/res/xml/watch_face_shapes.xml",
      true,
    )
    fixture.checkResultByFile(
      "src/main/res/values/strings.xml",
      "import/aab/expected/res/values/strings.xml",
      true,
    )
    fixture.checkResultByFile(
      "src/main/res/values-es/strings.xml",
      "import/aab/expected/res/values-es/strings.xml",
      true,
    )

    assertThat(fixture.findFileInTempDir("src/main/res/drawable-nodpi-v4/preview.png").exists())
      .isTrue()
    assertThat(fixture.findFileInTempDir("src/main/res/drawable-nodpi-v4/").children).hasLength(54)
  }

  @Test
  fun `test import example APK file`() = runTest {
    val importer =
      WatchFaceStudioFileImporter.getInstanceForTest(
        project = projectRule.project,
        defaultDispatcher = StandardTestDispatcher(testScheduler),
        ioDispatcher = StandardTestDispatcher(testScheduler),
      )
    val apkFilePath = testDataPath.resolve("import/apk/example.apk")

    val result = importer.import(apkFilePath)

    assertThat(result).isEqualTo(WFSImportResult.Success)

    fixture.checkResultByFile(
      "src/main/$FN_ANDROID_MANIFEST_XML",
      "import/apk/expected/$FN_ANDROID_MANIFEST_XML",
      true,
    )
    fixture.checkResultByFile(
      "src/main/res/raw/watchface.xml",
      "import/apk/expected/res/raw/watchface.xml",
      true,
    )
    fixture.checkResultByFile(
      "src/main/res/xml/watch_face.xml",
      "import/apk/expected/res/xml/watch_face.xml",
      true,
    )
    fixture.checkResultByFile(
      "src/main/res/xml/watch_face_info.xml",
      "import/apk/expected/res/xml/watch_face_info.xml",
      true,
    )
    fixture.checkResultByFile(
      "src/main/res/xml/watch_face_shapes.xml",
      "import/apk/expected/res/xml/watch_face_shapes.xml",
      true,
    )
    fixture.checkResultByFile(
      "src/main/res/values/strings.xml",
      "import/apk/expected/res/values/strings.xml",
      true,
    )
    fixture.checkResultByFile(
      "src/main/res/values-es/strings.xml",
      "import/apk/expected/res/values-es/strings.xml",
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
    val aabFilePath = testDataPath.resolve("import/aab/example.aab")
    fixture.copyFileToProject(
      "import/AndroidManifest_existing.xml",
      "src/main/$FN_ANDROID_MANIFEST_XML",
    )

    val result = importer.import(aabFilePath)

    assertThat(result).isEqualTo(WFSImportResult.Success)

    fixture.checkResultByFile(
      "src/main/$FN_ANDROID_MANIFEST_XML",
      "import/aab/expected/AndroidManifest_merged.xml",
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

  @Test
  fun `test supported file types`() = runTest {
    val importer =
      WatchFaceStudioFileImporter.getInstanceForTest(
        project = projectRule.project,
        defaultDispatcher = StandardTestDispatcher(testScheduler),
        ioDispatcher = StandardTestDispatcher(testScheduler),
      )

    val supportedFileTypes = importer.supportedFileTypes

    assertThat(supportedFileTypes).isEqualTo(setOf("aab", "apk"))
  }

  @Test
  fun `test import unsupported file type`() = runTest {
    val importer =
      WatchFaceStudioFileImporter.getInstanceForTest(
        project = projectRule.project,
        defaultDispatcher = StandardTestDispatcher(testScheduler),
        ioDispatcher = StandardTestDispatcher(testScheduler),
      )
    val invalidFile = fixture.addFileToProject("invalid.unsupported", "")

    val result = importer.import(invalidFile.virtualFile.toNioPath())

    assertThat(result).isEqualTo(WFSImportResult.Error(UNSUPPORTED_FILE_EXTENSION))
  }

  @Test
  fun `the watchface's run configuration is added`() = runTest {
    Dispatchers.Default
    val importer =
      WatchFaceStudioFileImporter.getInstanceForTest(
        project = projectRule.project,
        defaultDispatcher = StandardTestDispatcher(testScheduler),
        ioDispatcher = StandardTestDispatcher(testScheduler),
      )

    val result = importer.import(testDataPath.resolve("import/aab/example.aab"))

    assertThat(result).isEqualTo(WFSImportResult.Success)
    val watchFaceRunConfiguration =
      RunManager.getInstance(projectRule.project).allConfigurationsList.find {
        it is AndroidDeclarativeWatchFaceConfiguration
      }
    assertThat(watchFaceRunConfiguration).isNotNull()
    assertThat(watchFaceRunConfiguration!!.name).isEqualTo("Camping")
  }
}
