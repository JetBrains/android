/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.report

import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.analytics.stubs.StubDateProvider
import com.android.utils.DateProvider
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.readText
import junit.framework.TestCase
import java.nio.file.Path
import java.nio.file.Paths

private const val FILE_NAME = "MetricsInfo.log"

private const val LOG_DATA = """2022-02-07 00:00:00 UTC
kind: EMULATOR_PING

2022-02-08 00:00:00 UTC
kind: EMULATOR_PING

2022-02-09 00:00:00 UTC
kind: EMULATOR_PING

2022-02-10 00:00:00 UTC
kind: EMULATOR_PING
"""

class MetricsLogFileProviderTest : TestCase() {
  lateinit var testDirectoryPath: Path
  lateinit var dateProvider: DateProvider

  override fun setUp() {
    super.setUp()
    dateProvider = AnalyticsSettings.dateProvider
    testDirectoryPath = FileUtil.createTempDirectory("MetricsLogFileProviderTest", null).toPath()
  }

  override fun tearDown() {
    FileUtils.deleteRecursivelyIfExists(testDirectoryPath.toFile())
    AnalyticsSettings.dateProvider = dateProvider
    super.tearDown()
  }

  fun testMetricsLogFileProvider() {
    val pathProvider = PathProvider(testDirectoryPath.toString(), null, null, null)
    val metricsLogFileProvider = MetricsLogFileProvider(pathProvider, 4)

    for (i in (1..10)) {
      AnalyticsSettings.dateProvider = StubDateProvider(2022, 1, i)
      val builder = AndroidStudioEvent.newBuilder().apply {
        kind = AndroidStudioEvent.EventKind.EMULATOR_PING
      }

      metricsLogFileProvider.processEvent(builder)
    }

    val fileInfo = metricsLogFileProvider.getFiles(null)
    assertThat(fileInfo.size).isEqualTo(1)

    val logFile = testDirectoryPath.resolve(FILE_NAME)
    fileInfo[0].apply {
      assertThat(source).isEqualTo(logFile)
      assertThat(destination).isEqualTo(Paths.get(FILE_NAME))
    }

    val data = logFile.readText()
    assertThat(data).isEqualTo(LOG_DATA)
  }
}