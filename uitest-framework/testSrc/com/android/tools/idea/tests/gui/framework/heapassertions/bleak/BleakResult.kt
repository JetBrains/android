/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.heapassertions.bleak

class BleakResult(leakInfos: List<LeakInfo> = listOf(), private val disposerInfo: Map<DisposerInfo.Key, Int> = mapOf()) {
  private val leaksAndKnownIssues = leakInfos.filterNot { it.whitelisted }.partition { it.isKnownIssue } // [known issues] + [leaks]
  private val actualLeaks = leaksAndKnownIssues.second

  val knownIssues = leaksAndKnownIssues.first
  val success = actualLeaks.isEmpty() && disposerInfo.isEmpty()
  val errorMessage = buildString {
      if (disposerInfo.isNotEmpty()) {
        appendln("Disposer Info:")
        disposerInfo.forEach {
          append("\nDisposable of type ${it.key.disposable.javaClass.name} has an increasing number (${it.value}) of children of type ${it.key.klass.name}")
        }
        append("\n------------------------------\n")
      }
      append(actualLeaks.joinToString(separator = "\n------------------------------\n"))
    }.replace("sun.reflect", "sun.relfect") // Ant filters out lines from exceptions that contain "sun.reflect", among other things.
}
