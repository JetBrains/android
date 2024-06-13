/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.adb

import com.android.adblib.testingutils.CoroutineTestUtils
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.tools.debugging.properties
import com.android.adblib.tools.testutils.AdbLibToolsJdwpTestBase
import com.android.jdwptracer.JDWPTracer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioAdbLibSCacheJdwpSessionPipelineTest : AdbLibToolsJdwpTestBase() {
  /**
   * Note: We force jdwp trace to be enabled to ensure the [JDWPTracer] specific code in [StudioAdbLibSCacheJdwpSessionPipeline]
   * is covered.
   */
  private val enableJdwpTracer = true

  @Test
  fun factoryInstallWorks() = runBlockingWithTimeout {
    // Prepare
    var enabledForDeviceWasCalled = false
    StudioAdbLibSCacheJdwpSessionPipelineFactory.install(
      session,
      enableForDevice = { _ ->
        enabledForDeviceWasCalled = true
        true
      },
      enableJdwpTracer = { enableJdwpTracer }
    )
    val jdwpSessionInfo = createJdwpProxySession(pid = 11)
    val jdwpSession = jdwpSessionInfo.debuggerJdwpSession

    // Act
    val reply = sendVmVersionPacket(jdwpSession)
    jdwpSession.close()

    // Assert
    assertTrue(reply.isReply)
    assertTrue(enabledForDeviceWasCalled)
  }

  @Test
  fun externalDebuggerCanDetachAndReAttach() = runBlockingWithTimeout {
    // Prepare
    StudioAdbLibSCacheJdwpSessionPipelineFactory.install(
      session,
      enableForDevice = { _ -> true },
      enableJdwpTracer = { enableJdwpTracer }
    )
    val jdwpSessionInfo = createJdwpProxySession(pid = 11)
    val jdwpProcess = jdwpSessionInfo.process
    val debuggerSocketAddress = jdwpProcess.properties.jdwpSessionProxyStatus.socketAddress

    // Act
    val jdwpSession1 = jdwpSessionInfo.debuggerJdwpSession
    val reply1 = sendVmVersionPacket(jdwpSession1)

    // Close JDWP session and wait for process to reflect new status
    jdwpSession1.close()
    CoroutineTestUtils.yieldUntil {
      !jdwpProcess.properties.jdwpSessionProxyStatus.isExternalDebuggerAttached
    }
    val debuggerSocketAddress2 = jdwpProcess.properties.jdwpSessionProxyStatus.socketAddress

    // Open 2nd session
    val jdwpSession2 = attachDebuggerSession(jdwpProcess)
    val reply2 = sendVmVersionPacket(jdwpSession2)

    // Assert
    assertTrue(jdwpProcess.properties.jdwpSessionProxyStatus.isExternalDebuggerAttached)
    assertEquals(debuggerSocketAddress, debuggerSocketAddress2)
    assertTrue(reply1.isReply)
    assertTrue(reply2.isReply)
  }
}
