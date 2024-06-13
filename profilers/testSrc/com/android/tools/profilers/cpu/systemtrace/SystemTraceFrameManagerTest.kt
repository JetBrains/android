/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.profilers.cpu.CpuThreadInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.TimeUnit

class SystemTraceFrameManagerTest {

  private companion object {
    val TEST_PID = 1
    val TEST_RENDER_ID = 2

    val mainThreadModel = ThreadModel(TEST_PID, TEST_PID, "Main",
                                      listOf(
                                        createEvent(2, 5, "Choreographer#doFrame"), // Good frame
                                        createEvent(7, 11, "mainLoop#Compute"), // Not a frame event
                                        createEvent(15, 35, "Choreographer#doFrame"), // Bad frame
                                        createEvent(40, 60, "mainLoop#Compute"), // Not a frame event
                                        createEvent(65, 82, "Choreographer#doFrame") // Good frame
                                      ),
                                      listOf(), listOf())
    val renderThreadModel = ThreadModel(TEST_RENDER_ID, TEST_PID, CpuThreadInfo.RENDER_THREAD_NAME,
                                        listOf(
                                          createEvent(4, 7, "DrawFrame"), // Good frame
                                          createEvent(10, 13, "doFrame"), // Good frame
                                          createEvent(17, 35, "DrawFrame"), // Bad frame
                                          createEvent(36, 39, "waitIO"), // Not a frame event
                                          createEvent(40, 57, "queueBuffer"), // Good frame
                                          createEvent(60, 80, "waitIO"), // Not a frame event
                                          createEvent(81, 100, "queueBuffer") // Bad frame
                                        ),
                                        listOf(), listOf())
    val processModel = ProcessModel(TEST_PID, "Test",
                                    mapOf(TEST_PID to mainThreadModel, TEST_RENDER_ID to renderThreadModel),
                                    emptyMap())

    private fun createEvent(startTimeMillis: Long, endTimeMillis: Long, name: String): TraceEventModel {
      val startUs = TimeUnit.MILLISECONDS.toMicros(startTimeMillis)
      val endUs = TimeUnit.MILLISECONDS.toMicros(endTimeMillis)
      return TraceEventModel(name, startUs, endUs, endUs - startUs, listOf())
    }
  }

  @Test
  fun framesOfMainThread() {
    val frameManager = SystemTraceFrameManager(processModel)
    val frames = frameManager.getFramesList(SystemTraceFrame.FrameThread.MAIN)
    // Validation metrics come from systrace for the same file.
    assertThat(frames).hasSize(3)
    assertThat(frames.count { it.perfClass == SystemTraceFrame.PerfClass.GOOD}).isEqualTo(2)
    assertThat(frames.count { it.perfClass == SystemTraceFrame.PerfClass.BAD}).isEqualTo(1)
    assertThat(frames.count { it.perfClass == SystemTraceFrame.PerfClass.NOT_SET}).isEqualTo(0)
  }

  @Test
  fun framesOfRenderThread() {
    val frameManager = SystemTraceFrameManager(processModel)
    val frames = frameManager.getFramesList(SystemTraceFrame.FrameThread.RENDER)
    // Validation metrics come from systrace for the same file.
    assertThat(frames).hasSize(5)
    assertThat(frames.count { it.perfClass == SystemTraceFrame.PerfClass.GOOD}).isEqualTo(3)
    assertThat(frames.count { it.perfClass == SystemTraceFrame.PerfClass.BAD}).isEqualTo(2)
    assertThat(frames.count { it.perfClass == SystemTraceFrame.PerfClass.NOT_SET}).isEqualTo(0)
  }

  @Test
  fun noMatchingThreadReturnsEmptyList() {
    val frameManager = SystemTraceFrameManager(processModel)
    assertThat(frameManager.getFramesList(SystemTraceFrame.FrameThread.OTHER)).hasSize(0)
  }

  @Test
  fun mainThreadFramesShouldBeAssociatedWithRenderThreadFrames() {
    val mainThread = ThreadModel(TEST_PID, TEST_PID, "Main",
                                 listOf(
                                   createEvent(2, 5, "Choreographer#doFrame"),
                                   createEvent(7, 11, "Choreographer#doFrame"),
                                   createEvent(20, 22, "Choreographer#doFrame"),
                                   createEvent(30, 50, "Choreographer#doFrame")
                                 ),
                                 listOf(), listOf())
    val renderThread = ThreadModel(TEST_RENDER_ID, TEST_PID, CpuThreadInfo.RENDER_THREAD_NAME,
                                   listOf(
                                     createEvent(4, 7, "DrawFrame"),
                                     createEvent(10, 13, "doFrame"),
                                     createEvent(15, 17, "queueBuffer"),
                                     createEvent(18, 20, "DrawFrame"),
                                     createEvent(40, 55, "queueBuffer")
                                   ),
                                   listOf(), listOf())

    val process = ProcessModel(TEST_PID, "Test",
                               mapOf(TEST_PID to mainThread, TEST_RENDER_ID to renderThread),
                               emptyMap())

    val frameManager = SystemTraceFrameManager(process)

    val mainThreadFrames = frameManager.getFramesList(SystemTraceFrame.FrameThread.MAIN)
    val renderThreadFrames = frameManager.getFramesList(SystemTraceFrame.FrameThread.RENDER)

    assertThat(mainThreadFrames.size).isEqualTo(4)
    assertThat(renderThreadFrames.size).isEqualTo(5)

    assertThat(mainThreadFrames[0].associatedFrame).isEqualTo(renderThreadFrames[0])
    assertThat(mainThreadFrames[1].associatedFrame).isEqualTo(renderThreadFrames[1])
    assertThat(mainThreadFrames[2].associatedFrame).isEqualTo(null)
    assertThat(mainThreadFrames[3].associatedFrame).isEqualTo(renderThreadFrames[4])

    assertThat(renderThreadFrames[0].associatedFrame).isEqualTo(mainThreadFrames[0])
    assertThat(renderThreadFrames[1].associatedFrame).isEqualTo(mainThreadFrames[1])
    assertThat(renderThreadFrames[2].associatedFrame).isEqualTo(null)
    assertThat(renderThreadFrames[3].associatedFrame).isEqualTo(null)
    assertThat(renderThreadFrames[4].associatedFrame).isEqualTo(mainThreadFrames[3])
  }

  @Test
  fun framesEndWithEmptyFrame() {
    val frameManager = SystemTraceFrameManager(processModel)
    val frames = frameManager.getFrames(SystemTraceFrame.FrameThread.MAIN)
    // Each frame has a empty frame after it for spacing.
    assertThat(frames).hasSize(3 * 2)
    for (i in 0 until frames.size step 2) {
      assertThat(frames[i].value).isNotEqualTo(SystemTraceFrame.EMPTY)
      assertThat(frames[i + 1].value).isEqualTo(SystemTraceFrame.EMPTY)
    }
  }
}