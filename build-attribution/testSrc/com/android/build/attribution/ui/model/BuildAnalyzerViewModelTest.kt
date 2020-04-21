/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.build.attribution.ui.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test


class BuildAnalyzerViewModelTest {

  private var callsCount = 0
  private val listenerMock: () -> Unit = {
    callsCount++
  }

  private val model = BuildAnalyzerViewModel().apply {
    dataSetSelectionListener = listenerMock
  }

  @Test
  fun testInitialDataSetSelection() {
    assertThat(model.selectedData).isEqualTo(BuildAnalyzerViewModel.DataSet.OVERVIEW)
  }

  @Test
  fun testChangeDataSetSelectionNotifiesListener() {
    model.selectedData = BuildAnalyzerViewModel.DataSet.TASKS
    assertThat(callsCount).isEqualTo(1)
    assertThat(model.selectedData).isEqualTo(BuildAnalyzerViewModel.DataSet.TASKS)

    model.selectedData = BuildAnalyzerViewModel.DataSet.WARNINGS
    assertThat(callsCount).isEqualTo(2)
    assertThat(model.selectedData).isEqualTo(BuildAnalyzerViewModel.DataSet.WARNINGS)
  }

  @Test
  fun testSettingSameDataSetDoesNotTriggerListener() {
    model.selectedData = BuildAnalyzerViewModel.DataSet.OVERVIEW
    assertThat(callsCount).isEqualTo(0)
    assertThat(model.selectedData).isEqualTo(BuildAnalyzerViewModel.DataSet.OVERVIEW)
  }
}