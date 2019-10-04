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
package com.android.tools.idea.run.util

import com.google.common.truth.Truth.assertThat
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.contains
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

/**
 * Unit tests for [ProcessHandlerLaunchStatus].
 */
class ProcessHandlerLaunchStatusTest {
  @Mock
  lateinit var processHandler: ProcessHandler

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)
  }

  @Test
  fun testTerminationStateIsTiedWithProcessHandler() {
    `when`(processHandler.isProcessTerminating).thenReturn(false)
    `when`(processHandler.isProcessTerminated).thenReturn(false)
    assertThat(ProcessHandlerLaunchStatus(processHandler).isLaunchTerminated).isFalse()

    `when`(processHandler.isProcessTerminating).thenReturn(true)
    `when`(processHandler.isProcessTerminated).thenReturn(false)
    assertThat(ProcessHandlerLaunchStatus(processHandler).isLaunchTerminated).isTrue()

    `when`(processHandler.isProcessTerminating).thenReturn(false)
    `when`(processHandler.isProcessTerminated).thenReturn(true)
    assertThat(ProcessHandlerLaunchStatus(processHandler).isLaunchTerminated).isTrue()
  }

  @Test
  fun testGetProcessHandler() {
    val status = ProcessHandlerLaunchStatus(processHandler)
    assertThat(status.processHandler).isSameAs(processHandler)
  }

  @Test
  fun testTerminateLaunch() {
    `when`(processHandler.isProcessTerminating).thenReturn(false)
    `when`(processHandler.isProcessTerminated).thenReturn(false)
    val status = ProcessHandlerLaunchStatus(processHandler)

    assertThat(status.isLaunchTerminated).isFalse()

    status.terminateLaunch("This is error message", /*destroyProcess=*/false)

    assertThat(status.isLaunchTerminated).isTrue()
    verify(processHandler, never()).destroyProcess()
    verify(processHandler).notifyTextAvailable(contains("This is error message"), eq(ProcessOutputTypes.STDERR))
  }

  @Test
  fun testTerminateLaunchAndDestroyProcess() {
    `when`(processHandler.isProcessTerminating).thenReturn(false)
    `when`(processHandler.isProcessTerminated).thenReturn(false)
    val status = ProcessHandlerLaunchStatus(processHandler)

    assertThat(status.isLaunchTerminated).isFalse()

    status.terminateLaunch("This is error message", /*destroyProcess=*/true)

    assertThat(status.isLaunchTerminated).isTrue()
    verify(processHandler).destroyProcess()
    verify(processHandler).notifyTextAvailable(contains("This is error message"), eq(ProcessOutputTypes.STDERR))
  }

  @Test
  fun testTerminateLaunchWithNoErrorMessage() {
    `when`(processHandler.isProcessTerminating).thenReturn(false)
    `when`(processHandler.isProcessTerminated).thenReturn(false)
    val status = ProcessHandlerLaunchStatus(processHandler)

    assertThat(status.isLaunchTerminated).isFalse()

    status.terminateLaunch(null, /*destroyProcess=*/true)

    assertThat(status.isLaunchTerminated).isTrue()
    verify(processHandler).destroyProcess()
    verify(processHandler, never()).notifyTextAvailable(any(), any())
  }

  @Test
  fun testTerminateLaunchWithEmptyErrorMessage() {
    `when`(processHandler.isProcessTerminating).thenReturn(false)
    `when`(processHandler.isProcessTerminated).thenReturn(false)
    val status = ProcessHandlerLaunchStatus(processHandler)

    assertThat(status.isLaunchTerminated).isFalse()

    status.terminateLaunch(/*errorMessage=*/"", /*destroyProcess=*/true)

    assertThat(status.isLaunchTerminated).isTrue()
    verify(processHandler).destroyProcess()
    verify(processHandler, never()).notifyTextAvailable(any(), any())
  }

  @Test
  fun testTerminationConditions() {
    `when`(processHandler.isProcessTerminating).thenReturn(false)
    `when`(processHandler.isProcessTerminated).thenReturn(false)
    val status = ProcessHandlerLaunchStatus(processHandler)

    var condition1 = false
    var condition2 = false

    status.addLaunchTerminationCondition { condition1 }
    status.addLaunchTerminationCondition { condition2 }

    assertThat(status.isLaunchTerminated).isFalse()

    `when`(processHandler.isProcessTerminated).thenReturn(true)

    // isLaunchTerminated should return false even after the process handler is terminated because condition1
    // and condition2 are still false.
    assertThat(status.isLaunchTerminated).isFalse()

    condition2 = true
    assertThat(status.isLaunchTerminated).isFalse()

    condition1 = true
    assertThat(status.isLaunchTerminated).isTrue()
  }

  @Test
  fun testTerminationConditionsAreMetButProcessIsStillRunning() {
    `when`(processHandler.isProcessTerminating).thenReturn(false)
    `when`(processHandler.isProcessTerminated).thenReturn(false)
    val status = ProcessHandlerLaunchStatus(processHandler)

    status.addLaunchTerminationCondition { true }

    // A condition is met but the process is still running so isLaunchTerminated should return false.
    assertThat(status.isLaunchTerminated).isFalse()

    `when`(processHandler.isProcessTerminated).thenReturn(true)
    assertThat(status.isLaunchTerminated).isTrue()
  }

  @Test
  fun testTerminationConditionsAreIgnoredWhenForcefulTermination() {
    `when`(processHandler.isProcessTerminating).thenReturn(false)
    `when`(processHandler.isProcessTerminated).thenReturn(false)
    val status = ProcessHandlerLaunchStatus(processHandler)

    status.addLaunchTerminationCondition { false }

    assertThat(status.isLaunchTerminated).isFalse()

    // Forcefully terminate the launch regardless of its termination conditions.
    status.terminateLaunch(null, /*destroyProcess=*/false)
    assertThat(status.isLaunchTerminated).isTrue()
  }
}