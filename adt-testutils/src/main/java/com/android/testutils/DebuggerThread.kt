/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.testutils

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.events.DebuggerCommandImpl

/**
 * Run a block of code in the Debugger Manager Thread
 *
 * If the block of code throws an exception, raise it to the caller.
 */
fun runInDebuggerThread(
  debugProcess: DebugProcessImpl,
  test: () -> Unit,
  ) {
  val managerThread = debugProcess.managerThread
  @Suppress("UnstableApiUsage") managerThread.setVmProxy(debugProcess.virtualMachineProxy)

  var failure: Throwable? = null
  managerThread.invokeAndWait(
    object : DebuggerCommandImpl() {
      override fun action() {
        try {
          test()
        } catch (e: Throwable) {
          failure = e
        }
      }
    }
  )
  failure?.let { throw it }
}
