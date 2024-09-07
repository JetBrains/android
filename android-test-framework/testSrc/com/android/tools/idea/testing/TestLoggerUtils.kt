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
package com.android.tools.idea.testing

import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.util.ThrowableRunnable

/** Executes the given runnable and returns the logged error messages. */
fun executeCapturingLoggedErrors(runnable: ThrowableRunnable<RuntimeException>): List<String> {
  val errorProcessor = LoggedErrorCapturer()
  LoggedErrorProcessor.executeWith(errorProcessor, runnable)
  return errorProcessor.errorMessages
}

/** Executes the given runnable and returns the logged warning messages. */
fun executeCapturingLoggedWarnings(runnable: ThrowableRunnable<RuntimeException>): List<String> {
  val errorProcessor = LoggedErrorCapturer()
  LoggedErrorProcessor.executeWith(errorProcessor, runnable)
  return errorProcessor.warningMessages
}

/** Executes the given runnable and returns the logged error and warning messages. */
fun executeCapturingLoggedErrorsAndWarnings(runnable: ThrowableRunnable<RuntimeException>): LoggedMessages {
  val errorProcessor = LoggedErrorCapturer()
  LoggedErrorProcessor.executeWith(errorProcessor, runnable)
  return LoggedMessages(errorProcessor.errorMessages, errorProcessor.warningMessages)
}

data class LoggedMessages(val errors: List<String>, val warnings: List<String>)

private class LoggedErrorCapturer : LoggedErrorProcessor() {
  val errorMessages = mutableListOf<String>()
  val warningMessages = mutableListOf<String>()

  override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> {
    errorMessages.add(message)
    return Action.NONE
  }

  override fun processWarn(category: String, message: String, t: Throwable?): Boolean {
    warningMessages.add(message)
    return super.processWarn(category, message, t)
  }
}
