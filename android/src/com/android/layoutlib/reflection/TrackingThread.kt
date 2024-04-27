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
package com.android.layoutlib.reflection

import com.android.ide.common.rendering.api.ILayoutLog
import com.intellij.openapi.application.ApplicationManager

private const val RESTRICTION_MESSAGE = "It is not allowed to create new threads in the preview"
private const val COROUTINE_DEFAULT_EXECUTOR = "kotlinx.coroutines.DefaultExecutor"

open class TrackingThread : Thread {
  constructor() : super() {
    reportIllegalThread()
  }

  constructor(target: Runnable) : super(target) {
    reportIllegalThread()
  }

  constructor(group: ThreadGroup, target: Runnable) : super(group, target) {
    reportIllegalThread()
  }

  constructor(name: String) : super(name) {
    reportIllegalThread()
  }

  constructor(group: ThreadGroup, name: String) : super(group, name) {
    reportIllegalThread()
  }

  constructor(target: Runnable, name: String) : super(target, name) {
    if (name != COROUTINE_DEFAULT_EXECUTOR) {
      reportIllegalThread()
    }
  }

  constructor(group: ThreadGroup, target: Runnable, name: String) : super(group, target, name) {
    reportIllegalThread()
  }

  constructor(group: ThreadGroup, target: Runnable, name: String, stackSize: Long) : super(group, target, name, stackSize) {
    reportIllegalThread()
  }

  constructor(group: ThreadGroup, target: Runnable, name: String, stackSize: Long, inheritThreadLocals: Boolean) :
    super(group, target, name, stackSize, inheritThreadLocals) {
    reportIllegalThread()
  }

  private fun reportIllegalThread() {
    // Throwing should be the only option in the future but so far we just warn the users in production
    val throwable = createThrowable()
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw throwable
    } else {
      logWarning(throwable)
    }
  }

  private fun logWarning(throwable: Throwable) {
    try {
      val logger = getLogger()
      logger.warning(ILayoutLog.TAG_THREAD_CREATION, "Do not create Threads in the preview", null, throwable)
    } catch (ignore: Throwable) { }
  }

  private fun getLogger(): ILayoutLog {
    val bridgeClass = Class.forName("com.android.layoutlib.bridge.Bridge")
    val loggerField = bridgeClass.getDeclaredField("sCurrentLog")
    loggerField.isAccessible = true
    val loggerObject = loggerField.get(null)
    return loggerObject as ILayoutLog
  }

  private fun createThrowable(): Throwable {
    val throwable = IllegalStateException(RESTRICTION_MESSAGE)
    val elementsToRemove = 3
    val stacktrace = throwable.stackTrace
    // Remove the stack entries for <init>, reportIllegalThread, createThrowable
    throwable.stackTrace = stacktrace.copyOfRange(elementsToRemove, stacktrace.size)
    return throwable
  }
}