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
package com.android.tools.idea.run.configuration.execution

import com.android.ddmlib.IDevice
import com.android.ddmlib.IShellOutputReceiver
import com.android.testutils.MockitoKt.whenever
import com.android.tools.deployer.model.component.WearComponent.CommandResultReceiver.INVALID_ARGUMENT_CODE
import com.intellij.execution.ExecutionException
import com.intellij.openapi.progress.EmptyProgressIndicator
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito

class UtilsTest : AndroidTestCase() {
  fun testWearDebugSurfaceVersion() {
    val version = 10
    val device = getMockDevice { request ->
      when (request) {
        "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation version" ->
          "Broadcast completed: result=1, data=\"$version\""
        else -> "Unknown request: $request"
      }
    }

    assertEquals(version, device.getWearDebugSurfaceVersion(EmptyProgressIndicator()))
  }

  fun testWearDebugSurfaceVersionWhenInvalidResult() {
    val device = getMockDevice { request ->
      when (request) {
        "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation version" ->
          "Broadcast completed: result=0"
        else -> "Unknown request: $request"
      }
    }
    assertThrows(ExecutionException::class.java, "Error while checking version") {
      device.getWearDebugSurfaceVersion(EmptyProgressIndicator())
    }
  }

  fun testWearDebugSurfaceVersionWhenMissingVersionOp() {
    val device = getMockDevice { request ->
      when (request) {
        "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation version" ->
          "Broadcast completed: result=$INVALID_ARGUMENT_CODE, data=\"Unrecognized operation version\""
        else -> "Unknown request: $request"
      }
    }
    assertThrows(ExecutionException::class.java, "Device software is out of date") {
      device.getWearDebugSurfaceVersion(EmptyProgressIndicator())
    }

  }

  private fun getMockDevice(replies: (request: String) -> String = { request -> "Mock reply: $request" }): IDevice {
    val device = Mockito.mock(IDevice::class.java)
    whenever(
      device.executeShellCommand(Mockito.anyString(), Mockito.any(), Mockito.anyLong(), Mockito.any())
    ).thenAnswer { invocation ->
      val request = invocation.arguments[0] as String
      val receiver = invocation.arguments[1] as IShellOutputReceiver
      val reply = replies(request)
      val byteArray = "$reply\n".toByteArray(Charsets.UTF_8)
      receiver.addOutput(byteArray, 0, byteArray.size)
    }
    return device
  }
}