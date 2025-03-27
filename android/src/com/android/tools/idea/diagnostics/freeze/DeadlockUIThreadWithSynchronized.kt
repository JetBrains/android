/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.freeze

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import java.util.concurrent.Semaphore

class DeadlockUIThreadWithSynchronized : AnAction(), DumbAware {

  private val lock1 = Any()
  private val lock2 = Any()
  private val semaphore1 = Semaphore(0)
  private val semaphore2 = Semaphore(0)

  private fun blockingThread() {
    synchronized(lock2) {
      semaphore2.release()
      semaphore1.tryAcquire()
      synchronized(lock1) {
        println("Deadlock before reaching this line.")
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    ApplicationManager.getApplication().executeOnPooledThread(this::blockingThread)
    synchronized(lock1) {
      semaphore2.acquire()
      semaphore1.release()
      synchronized(lock2) {
        println("Deadlock before reaching this line.")
      }
    }
  }
}