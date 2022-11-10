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
package com.android.tools.idea

import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.util.ThrowableRunnable

/**
 * Executes the given runnable and returns the logged error messages.
 */
fun executeCapturingLoggedErrors(runnable: ThrowableRunnable<RuntimeException>): List<String> {
  val errorProcessor = LoggedErrorCapturer()
  LoggedErrorProcessor.executeWith(errorProcessor, runnable)
  return errorProcessor.errorMessages
}

private class LoggedErrorCapturer : LoggedErrorProcessor() {
  val errorMessages = mutableListOf<String>()

  override fun processError(category: String, message: String, t: Throwable?, details: Array<out String>): Boolean {
    errorMessages.add(message)
    return false
  }
}
