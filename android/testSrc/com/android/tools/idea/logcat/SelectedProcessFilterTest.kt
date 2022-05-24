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
package com.android.tools.idea.logcat

import com.android.ddmlib.ClientData
import com.android.ddmlib.Log
import com.android.ddmlib.logcat.LogCatHeader
import com.android.ddmlib.logcat.LogCatMessage
import com.android.testutils.MockitoKt.mock
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito.`when`
import java.time.Instant

private const val PROCESS_ID = 123
private const val OTHER_PROCESS_ID = 1000
private const val PACKAGE_NAME = "com.package.name"
private const val OTHER_PACKAGE_NAME = "com.package.other"

/**
 * Tests for [SelectedProcessFilter]
 */
class SelectedProcessFilterTest {
  @Test
  fun presentation() {
    assertThat(SelectedProcessFilter(null).name).isEqualTo("Show only selected application")
  }

  @Test
  fun isApplicable_matchesPid() {
    assertThat(SelectedProcessFilter(mockClientData(PROCESS_ID, PACKAGE_NAME)).isApplicable(newLogMessage(PROCESS_ID, OTHER_PACKAGE_NAME)))
      .isTrue()
  }

  @Test
  fun isApplicable_matchesPackageName() {
    assertThat(SelectedProcessFilter(mockClientData(PROCESS_ID, PACKAGE_NAME)).isApplicable(newLogMessage(OTHER_PROCESS_ID, PACKAGE_NAME)))
      .isTrue()
  }

  @Test
  fun isApplicable_noMatch() {
    assertThat(
      SelectedProcessFilter(mockClientData(PROCESS_ID, PACKAGE_NAME))
        .isApplicable(newLogMessage(OTHER_PROCESS_ID, OTHER_PACKAGE_NAME))
    ).isFalse()
  }

  @Test
  fun setClient() {
    val selectedProcessFilter = SelectedProcessFilter(mockClientData(OTHER_PROCESS_ID, OTHER_PACKAGE_NAME))

    selectedProcessFilter.setClient(mockClientData(PROCESS_ID, PACKAGE_NAME))

    assertThat(selectedProcessFilter.isApplicable(newLogMessage(PROCESS_ID, PACKAGE_NAME))).isTrue()
  }

}

private fun newLogMessage(processId: Int, packageName: String): LogCatMessage =
  LogCatMessage(LogCatHeader(Log.LogLevel.INFO, processId, 321, packageName, "Tag", Instant.EPOCH), "message")

private fun mockClientData(pid: Int, packageName: String): ClientData {
  return mock<ClientData>().apply {
    `when`(this.pid).thenReturn(pid)
    `when`(this.packageName).thenReturn(packageName)
  }

}