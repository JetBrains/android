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
package com.android.tools.idea.instrumentation.threading

import com.android.annotations.concurrency.UiThread
import com.android.annotations.concurrency.WorkerThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class TriggerSampleThreadingViolationAction() : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    workerThreadMethod()
    uiThreadMethod()
  }

  @WorkerThread
  fun workerThreadMethod() {
    println("workerThreadMethod was called")
  }

  @UiThread
  fun uiThreadMethod() {
    println("uiThreadMethod was called")
  }
}