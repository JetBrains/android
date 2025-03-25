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
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class DeadlockUIThreadWithLocksTimeout : AnAction(), DumbAware {

  private val lock1 = ReentrantLock()
  private val lock2 = ReentrantLock()
  private val semaphore1 = Semaphore(0)
  private val semaphore2 = Semaphore(0)

  private fun blockingThread() {
    lock2.withLock {
      semaphore2.release()
      semaphore1.acquire()
      lock1.withLock {
        println("Deadlock before reaching this line.")
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    ApplicationManager.getApplication().executeOnPooledThread(this::blockingThread)
    lock1.withLock {
      semaphore2.acquire()
      semaphore1.release()
      lock2.tryLock(10, TimeUnit.SECONDS)
      println("Deadlock before reaching this line.")
      lock2.unlock()
    }
  }
}