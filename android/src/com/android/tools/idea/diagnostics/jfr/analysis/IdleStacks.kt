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
package com.android.tools.idea.diagnostics.jfr.analysis

class IdleStacks {
  companion object {
    fun isIdle(threadName: String, stacktrace: List<String>): Boolean {
      idlePatterns.forEach { (prefix, topFrameCandidates) ->
        val topFrame = stacktrace[0].substringBefore("(")
        if (threadName.startsWith(prefix) && topFrameCandidates.contains(topFrame)) return true
      }
      return false
    }

    fun isIgnoredThread(threadName: String): Boolean = threadName in listOf("JFR Periodic Tasks")

    private val idlePatterns = listOf(
      "" to listOf("sun.nio.ch.WindowsSelectorImpl\$SubSelector.poll0", "sun.nio.ch.KQueue.poll", "sun.nio.ch.EPoll.wait"),
      "fsnotifier.exe" to listOf("java.lang.ProcessImpl.waitForInterruptibly"),
      "AWT-Windows" to listOf("sun.awt.windows.WToolkit.eventLoop"),
      "BaseDataReader" to listOf("java.io.FileInputStream.readBytes"),
      "Monitor" to listOf("sun.nio.ch.FileDispatcherImpl.read0"),
      "process reaper" to listOf("java.lang.ProcessHandleImpl.waitForProcessExit0"), // ?
      "AWT-XAWT" to listOf("sun.awt.X11.XToolkit.waitForEvents"),
    )
  }
}