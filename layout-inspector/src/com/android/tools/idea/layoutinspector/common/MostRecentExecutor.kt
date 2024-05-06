/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.common

import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.Executor

/**
 * An [Executor] wrapper that ensures that only the command it most recently received gets executed.
 * This is useful if you expect lots of work where any new command means that any queued up command
 * is already obsolete and should be dropped.
 *
 * In other words, if nothing is running, it will get executed immediately. If a previous command is
 * still in progress, then the next command will be stored for later execution as soon as possible.
 * If two or more commands come in, while a previous command is running, then only the most recently
 * received one will be executed when ready.
 *
 * @param wrapped An inner executor that this one delegates to. The inner executor doesn't offer any
 *   special guarantees about running only recent work - it is only when going through this parent
 *   layer that this rule is enforced.
 */
class MostRecentExecutor(private val wrapped: Executor) : Executor {
  private class State {
    var isExecuting = false
    var nextCommand: Runnable? = null
  }

  private val state = State()

  override fun execute(command: Runnable) {
    synchronized(state) {
      if (!state.isExecuting) {
        state.isExecuting = true
        wrapped.execute {
          try {
            command.run()
          } catch (e: Exception) {
            Logger.getInstance(MostRecentExecutor::class.java).warn(e)
            throw e
          } finally {
            synchronized(state) {
              state.isExecuting = false
              val nextCommand = state.nextCommand
              state.nextCommand = null
              nextCommand?.let { execute(it) }
            }
          }
        }
      } else {
        state.nextCommand = command
      }
    }
  }
}
