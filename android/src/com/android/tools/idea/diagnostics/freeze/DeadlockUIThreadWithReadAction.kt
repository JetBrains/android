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
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.DumbAware
import java.util.concurrent.Semaphore

class DeadlockUIThreadWithReadAction : AnAction(), DumbAware {

  private val semaphore1 = Semaphore(0)
  private val semaphore2 = Semaphore(0)
  private val semaphore3 = Semaphore(0)

  private fun blockingThread() {
    runReadAction {
      // let writer start acquiring write lock
      semaphore2.release()
      // wait for a different read action to finish
      semaphore3.acquire()
    }
  }

  private fun blockingThread2() {
    // wait for the first read action to run and writer to start write action
    semaphore1.acquire()
    // should give writer plenty of time to start acquiring write lock
    Thread.sleep(1000)
    runReadAction {
      println("Read action: Deadlock before reaching this line.")
      semaphore3.release()
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    ApplicationManager.getApplication().executeOnPooledThread(this::blockingThread)
    ApplicationManager.getApplication().executeOnPooledThread(this::blockingThread2)
    // Wait for first reader
    semaphore2.acquire()
    // Release second reader (with a 1 second delay)
    semaphore1.release()
    // Will wait for first reader to leave
    runWriteAction {
      println("Deadlock before reaching this line.")
    }
  }
}