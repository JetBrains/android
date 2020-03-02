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
package com.android.tools.profilers.cpu

import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.cpu.atrace.CpuThreadSliceInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Test

class MainProcessSelectorTest {

  private val processList = listOf(
    CpuThreadSliceInfo(100, "process1-Main", 100, "process1"),
    CpuThreadSliceInfo(200, "process1-Main", 200, "process2"),
    CpuThreadSliceInfo(300, "process1-Main", 300, "process3"),
    CpuThreadSliceInfo(400, "process1-Main", 400, "process4"))

  @Test
  fun `first check is by name hint`() {
    val service = FakeIdeProfilerServices()
    service.setListBoxOptionsIndex(1) // process2
    val selector = MainProcessSelector("com.android.process4", 300, service)
    assertThat(selector.apply(processList)).isEqualTo(400)
  }

  @Test
  fun `second check is by id hint`() {
    val service = FakeIdeProfilerServices()
    service.setListBoxOptionsIndex(1) // process2
    val selector = MainProcessSelector("dont_match", 300, service)
    assertThat(selector.apply(processList)).isEqualTo(300)
  }

  @Test
  fun `id hint as 0 never matches`() {
    val service = FakeIdeProfilerServices()
    service.setListBoxOptionsIndex(1) // process2
    val selector = MainProcessSelector("dont_match", 0, null)
    val newProcessList = listOf(
      CpuThreadSliceInfo(100, "process1-Main", 100, "process1"),
      CpuThreadSliceInfo(0, "process0-Main", 0, "process0"))

    assertThat(selector.apply(processList)).isEqualTo(100)
  }

  @Test
  fun `third check is by process selection dialog`() {
    val service = FakeIdeProfilerServices()
    service.setListBoxOptionsIndex(1) // process2
    val selector = MainProcessSelector("dont_match", 0, service)
    assertThat(selector.apply(processList)).isEqualTo(200)
  }

  @Test
  fun `if dialog is invoked, if the user cancels then throw`() {
    val service = FakeIdeProfilerServices()
    service.setListBoxOptionsIndex(-1) // process2
    val selector = MainProcessSelector("dont_match", 0, service)

    try {
      selector.apply(processList)
      fail()
    } catch (e: ProcessSelectorDialogAbortedException) {
    }
  }

  @Test
  fun `fallback to first element of the list if no checks matched`() {
    val selector = MainProcessSelector("dont_match", 0, null)
    assertThat(selector.apply(processList)).isEqualTo(100)
  }

  @Test
  fun `empty list returns null`() {
    val selector = MainProcessSelector("dont_match", 0, null)
    assertThat(selector.apply(listOf())).isNull()
  }
}
