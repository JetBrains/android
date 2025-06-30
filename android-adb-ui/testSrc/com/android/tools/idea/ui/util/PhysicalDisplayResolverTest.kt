/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.ui.util

import com.android.adblib.DeviceSelector
import com.android.adblib.testing.FakeAdbSession
import com.android.testutils.TestUtils.resolveWorkspacePathUnchecked
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.UsefulTestCase.assertThrows
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.nio.file.Files

/** Test for functions defined in PhysicalDisplayIdResolver.kt. */
internal class PhysicalDisplayIdResolverTest {

  private val dumpsysOutput = getDumpsysOutput("AutomotiveWithDistantDisplays")

  @Test
  fun testPhysicalDisplayIdLookup() = runTest {
    val adbSession = FakeAdbSession()
    val device = DeviceSelector.fromSerialNumber("123")
    adbSession.deviceServices.configureShellCommand(device, "dumpsys display", dumpsysOutput)
    assertThat(adbSession.getPhysicalDisplayId(device, 0)).isEqualTo(4619827259835644672)
    assertThat(adbSession.getPhysicalDisplayId(device, 2)).isEqualTo(4619827551948147201)
    assertThat(adbSession.getPhysicalDisplayId(device, 3)).isEqualTo(4619827124781842690)
    adbSession.closeAndJoin()
  }

  @Test
  fun testGetPhysicalDisplayIdFromDumpsysOutput() {
    assertThat(getPhysicalDisplayIdFromDumpsysOutput(dumpsysOutput, 0)).isEqualTo(4619827259835644672)
    assertThat(getPhysicalDisplayIdFromDumpsysOutput(dumpsysOutput, 2)).isEqualTo(4619827551948147201)
    assertThat(getPhysicalDisplayIdFromDumpsysOutput(dumpsysOutput, 3)).isEqualTo(4619827124781842690)
    assertThrows(RuntimeException::class.java) {
      getPhysicalDisplayIdFromDumpsysOutput(dumpsysOutput, 1)
    }
  }
}

private fun getDumpsysOutput(filename: String): String =
    Files.readString(resolveWorkspacePathUnchecked("tools/adt/idea/android-adb-ui/testData/dumpsys/$filename.txt"))
