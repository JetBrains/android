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
package com.android.tools.compose.debug.utils

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.impl.PrioritizedTask
import java.util.concurrent.CompletableFuture

fun DebugProcessImpl.invokeOnDebuggerManagerThread(f: () -> Unit) {
  val future = CompletableFuture<Unit>()

  managerThread.invokeAndWait(
    object : DebuggerCommandImpl(PrioritizedTask.Priority.NORMAL) {
      override fun action() {
        try {
          future.complete(f())
        } catch (t: Throwable) {
          future.completeExceptionally(t)
        }
      }
    }
  )

  future.get()
}
