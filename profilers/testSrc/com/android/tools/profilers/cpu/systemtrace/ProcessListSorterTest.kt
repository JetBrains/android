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
package com.android.tools.profilers.cpu.systemtrace

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProcessListSorterTest {

  @Test
  fun nameHintMatches() {
    val processList: List<ProcessModel> = listOf(
      ProcessModel(1, "com.google.app", mapOf(), mapOf()),
      ProcessModel(1, "another.google.app", mapOf(), mapOf()))

    val sorter = ProcessListSorter("com.google.app")

    assertThat(sorter.sort(processList)).containsExactly(
      ProcessModel(1, "com.google.app", mapOf(), mapOf()),
      ProcessModel(1, "another.google.app", mapOf(), mapOf())
    ).inOrder()
    assertThat(sorter.sort(processList.reversed())).containsExactly(
      ProcessModel(1, "com.google.app", mapOf(), mapOf()),
      ProcessModel(1, "another.google.app", mapOf(), mapOf())
    ).inOrder()
  }

  @Test
  fun validPackageNameHasPriority() {
    val processList: List<ProcessModel> = listOf(
      ProcessModel(1, "com.google.app", mapOf(), mapOf()),
      ProcessModel(1, "aaa", mapOf(), mapOf()))

    val sorter = ProcessListSorter("")

    assertThat(sorter.sort(processList)).containsExactly(
      ProcessModel(1, "com.google.app", mapOf(), mapOf()),
      ProcessModel(1, "aaa", mapOf(), mapOf())
    ).inOrder()
    assertThat(sorter.sort(processList.reversed())).containsExactly(
      ProcessModel(1, "com.google.app", mapOf(), mapOf()),
      ProcessModel(1, "aaa", mapOf(), mapOf())
    ).inOrder()
  }

  @Test
  fun processWithNameHasPriority() {
    val processList: List<ProcessModel> = listOf(
      ProcessModel(1, "<10548>", mapOf(), mapOf()),
      ProcessModel(1, "another.google.app", mapOf(), mapOf()))

    val sorter = ProcessListSorter("")

    assertThat(sorter.sort(processList)).containsExactly(
      ProcessModel(1, "another.google.app", mapOf(), mapOf()),
      ProcessModel(1, "<10548>", mapOf(), mapOf())
    ).inOrder()
    assertThat(sorter.sort(processList.reversed())).containsExactly(
      ProcessModel(1, "another.google.app", mapOf(), mapOf()),
      ProcessModel(1, "<10548>", mapOf(), mapOf())
    ).inOrder()
  }

  @Test
  fun processWithMoreThreadsHasPriority() {
    val thread5 = ThreadModel(5, 1, "Thread", listOf(), listOf(), listOf())
    val thread6 = ThreadModel(6, 1, "Thread", listOf(), listOf(), listOf())

    val processList: List<ProcessModel> = listOf(
      ProcessModel(1, "com.google.app", mapOf(5 to thread5, 6 to thread6), mapOf()),
      ProcessModel(1, "com.google.app", mapOf(5 to thread5), mapOf()))

    val sorter = ProcessListSorter("")

    assertThat(sorter.sort(processList)).containsExactly(
      ProcessModel(1, "com.google.app", mapOf(5 to thread5, 6 to thread6), mapOf()),
      ProcessModel(1, "com.google.app", mapOf(5 to thread5), mapOf())
    ).inOrder()
    assertThat(sorter.sort(processList.reversed())).containsExactly(
      ProcessModel(1, "com.google.app", mapOf(5 to thread5, 6 to thread6), mapOf()),
      ProcessModel(1, "com.google.app", mapOf(5 to thread5), mapOf())
    ).inOrder()
  }

  @Test
  fun sortAlphabetically() {
    val processList: List<ProcessModel> = listOf(
      ProcessModel(1, "com.google.app.a", mapOf(), mapOf()),
      ProcessModel(1, "com.google.app.b", mapOf(), mapOf()))

    val sorter = ProcessListSorter("")

    assertThat(sorter.sort(processList)).containsExactly(
      ProcessModel(1, "com.google.app.a", mapOf(), mapOf()),
      ProcessModel(1, "com.google.app.b", mapOf(), mapOf())
    ).inOrder()
    assertThat(sorter.sort(processList.reversed())).containsExactly(
      ProcessModel(1, "com.google.app.a", mapOf(), mapOf()),
      ProcessModel(1, "com.google.app.b", mapOf(), mapOf())
    ).inOrder()
  }

  @Test
  fun sortById() {
    val processList: List<ProcessModel> = listOf(
      ProcessModel(1, "com.google.app.a", mapOf(), mapOf()),
      ProcessModel(2, "com.google.app.a", mapOf(), mapOf()))

    val sorter = ProcessListSorter("")

    assertThat(sorter.sort(processList)).containsExactly(
      ProcessModel(1, "com.google.app.a", mapOf(), mapOf()),
      ProcessModel(2, "com.google.app.a", mapOf(), mapOf())
    ).inOrder()
    assertThat(sorter.sort(processList.reversed())).containsExactly(
      ProcessModel(1, "com.google.app.a", mapOf(), mapOf()),
      ProcessModel(2, "com.google.app.a", mapOf(), mapOf())
    ).inOrder()
  }

  @Test
  fun `empty list`() {
    val sorter = ProcessListSorter("")
    assertThat(sorter.sort(emptyList())).isEmpty()
  }
}