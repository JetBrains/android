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
import com.android.tools.profilers.cpu.systemtrace.ProcessModel
import com.android.tools.profilers.cpu.systemtrace.ThreadModel
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Test

class MainProcessSelectorTest {

  private val processList = listOf(
    ProcessModel(100, "process1", mapOf(), mapOf()),
    ProcessModel(200, "process2", mapOf(), mapOf()),
    ProcessModel(300, "process3", mapOf(), mapOf()),
    ProcessModel(400, "process4", mapOf(), mapOf()))

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
      ProcessModel(100, "process1", emptyMap(), emptyMap()),
      ProcessModel(0, "process0", emptyMap(), emptyMap()))

    assertThat(selector.apply(newProcessList)).isEqualTo(100)
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

  @Test
  fun `use process safe name for name hint when empty name`() {
    val selector = MainProcessSelector("process0", 0, null)
    val newProcessList = listOf(
      ProcessModel(100, "process1", emptyMap(), emptyMap()),
      // Second process has a blank name, but its main thread has a name.
      ProcessModel(10, "",
                   mapOf(10 to ThreadModel(10, 10, "process0", listOf(), listOf(), listOf())),
                   emptyMap()))

    assertThat(selector.apply(newProcessList)).isEqualTo(10)
  }

  @Test
  fun `use process safe name for name hint when PID name`() {
    val selector = MainProcessSelector("process0", 0, null)
    val newProcessList = listOf(
      ProcessModel(100, "process1", emptyMap(), emptyMap()),
      // Second process has a name in the format of <PID>, but its main thread has a name.
      ProcessModel(10, "<10>",
                   mapOf(10 to ThreadModel(10, 10, "process0", listOf(), listOf(), listOf())),
                   emptyMap()))

    assertThat(selector.apply(newProcessList)).isEqualTo(10)
  }
}
