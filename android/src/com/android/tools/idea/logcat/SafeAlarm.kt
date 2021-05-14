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
package com.android.tools.idea.logcat

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import com.intellij.util.Alarm.ThreadToUse

/**
 * Delegates to an Alarm but adds synchronization so we can safely perform certain operations:
 *  - Safely call [addRequest] after alarm was disposed.
 *  - Safely add a request only the alarm is empty.
 */
class SafeAlarm(threadToUse: ThreadToUse, parentDisposable: Disposable) : Disposable {
  private val alarm: Alarm = Alarm(threadToUse, this)

  init {
    Disposer.register(parentDisposable, this)
  }

  @Synchronized
  override fun dispose() {
    alarm.dispose()
  }

  @Synchronized
  fun addRequestIfNotEmpty(request: Runnable, delayMillis: Long) {
    if (!alarm.isEmpty) {
      return
    }
    addRequest(request, delayMillis)
  }

  @Synchronized
  fun addRequest(request: Runnable, delayMillis: Long) {
    if (alarm.isDisposed) {
      return
    }
    alarm.addRequest(request, delayMillis)
  }
}