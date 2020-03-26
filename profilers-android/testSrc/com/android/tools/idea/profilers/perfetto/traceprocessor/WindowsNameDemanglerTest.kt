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
package com.android.tools.idea.profilers.perfetto.traceprocessor

import com.android.tools.profiler.proto.Memory
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.SystemInfo
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.Executor

class WindowsNameDemanglerTest {
  @Test
  fun demangleStringWindows() {
    assumeTrue(SystemInfo.isWindows)
    val demangler = WindowsNameDemangler()
    val stackFrame = Memory.AllocationStack.StackFrame.newBuilder()
      .setMethodName("_ZN7android6Parcel13continueWriteEm")
    demangler.demangleInplace(mutableListOf(stackFrame))
    assertThat(stackFrame.methodName).isEqualTo("android::Parcel::continueWrite(unsigned long)")
  }

  @Test
  fun demangleStringOther() {
    assumeFalse(SystemInfo.isWindows)
    val demangler = WindowsNameDemangler()
    val stackFrame = Memory.AllocationStack.StackFrame.newBuilder()
      .setMethodName("_ZN7android6Parcel13continueWriteEm")
    demangler.demangleInplace(mutableListOf(stackFrame))
    assertThat(stackFrame.methodName).isEqualTo("_ZN7android6Parcel13continueWriteEm")
  }
}