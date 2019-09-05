/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.build.attribution.analyzers

import com.android.build.attribution.BuildAttributionWarningsFilter
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.TaskData
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CriticalPathAnalyzerTest {

  @Test
  fun testCriticalPathAnalyzer() {
    val analyzer = CriticalPathAnalyzer(BuildAttributionWarningsFilter())

    val pluginA = createBinaryPluginIdentifierStub("pluginA")
    val pluginB = createBinaryPluginIdentifierStub("pluginB")

    analyzer.onBuildStart()

    // Given tasks (A, B, C, D, E, F) with the following dependencies and execution times
    // A(10) -> B(20) -> D(20) -> F(10)
    // |                 ^
    // -------> C(30) -----> E(20)

    var taskA = createTaskFinishEventStub(":app:taskA", pluginA, emptyList(), 0, 10)
    var taskB = createTaskFinishEventStub(":app:taskB", pluginB, listOf(taskA), 10, 30)
    var taskC = createTaskFinishEventStub(":lib:taskC", pluginA, listOf(taskA), 10, 40)
    var taskD = createTaskFinishEventStub(":app:taskD", pluginB, listOf(taskB, taskC), 40, 60)
    var taskE = createTaskFinishEventStub(":app:taskE", pluginA, listOf(taskC), 40, 60)
    var taskF = createTaskFinishEventStub(":lib:taskF", pluginB, listOf(taskD), 60, 70)

    analyzer.receiveEvent(taskA)
    analyzer.receiveEvent(taskB)
    analyzer.receiveEvent(taskC)
    analyzer.receiveEvent(taskD)
    analyzer.receiveEvent(taskE)
    analyzer.receiveEvent(taskF)

    // When the build is finished successfully and the analyzer is run

    analyzer.onBuildSuccess(null)

    // Then the analyzer should find this critical path
    // A(10)             D(20) -> F(10)
    // |                 ^
    // -------> C(30) ----

    assertThat(analyzer.criticalPathDuration).isEqualTo(70)

    assertThat(analyzer.tasksCriticalPath).isEqualTo(
      listOf(TaskData.createTaskData(taskA), TaskData.createTaskData(taskC), TaskData.createTaskData(taskD),
             TaskData.createTaskData(taskF)))

    assertThat(analyzer.pluginsCriticalPath).hasSize(2)
    assertThat(analyzer.pluginsCriticalPath[0].plugin).isEqualTo(PluginData(pluginA))
    assertThat(analyzer.pluginsCriticalPath[0].buildDuration).isEqualTo(40)
    assertThat(analyzer.pluginsCriticalPath[1].plugin).isEqualTo(PluginData(pluginB))
    assertThat(analyzer.pluginsCriticalPath[1].buildDuration).isEqualTo(30)


    // A subsequent build has started, the analyzer should reset its state and prepare for the next build data
    analyzer.onBuildStart()

    // Given tasks (A, B, C, D, E, F) with the following dependencies and execution times
    // A(10) -> B(5) -> D(25) -> F(10)
    // |                V
    // -------> C(40)   --> E(15)

    taskA = createTaskFinishEventStub(":app:taskA", pluginA, emptyList(), 0, 10)
    taskB = createTaskFinishEventStub(":app:taskB", pluginB, listOf(taskA), 10, 15)
    taskC = createTaskFinishEventStub(":lib:taskC", pluginA, listOf(taskA), 10, 50)
    taskD = createTaskFinishEventStub(":app:taskD", pluginB, listOf(taskB), 15, 40)
    taskE = createTaskFinishEventStub(":app:taskE", pluginA, listOf(taskD), 40, 55)
    taskF = createTaskFinishEventStub(":lib:taskF", pluginB, listOf(taskD), 40, 50)

    analyzer.receiveEvent(taskA)
    analyzer.receiveEvent(taskB)
    analyzer.receiveEvent(taskC)
    analyzer.receiveEvent(taskD)
    analyzer.receiveEvent(taskE)
    analyzer.receiveEvent(taskF)

    // When the build is finished successfully and the analyzer is run

    analyzer.onBuildSuccess(null)

    // Then the analyzer should find this critical path
    // A(10) -> B(5) -> D(25)
    //                  V
    //                  --> E(15)

    assertThat(analyzer.criticalPathDuration).isEqualTo(55)

    assertThat(analyzer.tasksCriticalPath).isEqualTo(
      listOf(TaskData.createTaskData(taskA), TaskData.createTaskData(taskB), TaskData.createTaskData(taskD),
             TaskData.createTaskData(taskE)))

    assertThat(analyzer.pluginsCriticalPath).hasSize(2)
    assertThat(analyzer.pluginsCriticalPath[0].plugin).isEqualTo(PluginData(pluginB))
    assertThat(analyzer.pluginsCriticalPath[0].buildDuration).isEqualTo(30)
    assertThat(analyzer.pluginsCriticalPath[1].plugin).isEqualTo(PluginData(pluginA))
    assertThat(analyzer.pluginsCriticalPath[1].buildDuration).isEqualTo(25)
  }
}
