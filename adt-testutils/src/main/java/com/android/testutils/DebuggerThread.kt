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

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project

/**
 * Run a block of code in the Debugger Manager Thread
 *
 * If the block of code throws an exception, raise it to the caller.
 */
fun runInDebuggerThread(
  project: Project,
  disposableParent: Disposable,
  vmProxy: VirtualMachineProxyImpl,
  test: () -> Unit,
  ) {
  val threadManager = DebuggerManagerThreadImpl.createTestInstance(disposableParent, project)
  @Suppress("UnstableApiUsage") threadManager.setVmProxy(vmProxy)
  var failure: Throwable? = null
  threadManager.invokeAndWait(
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
