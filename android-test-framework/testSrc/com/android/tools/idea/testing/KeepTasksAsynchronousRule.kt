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

import org.junit.runner.Description

/**
 * Prevents [com.intellij.openapi.progress.Task.Backgroundable] from running synchronously in tests.
 */
class KeepTasksAsynchronousRule(private val overrideKeepTasksAsynchronous: Boolean) : NamedExternalResource() {
  private var originalIntellijProgressTaskIgnoreHeadlessProperty: String? = null

  override fun before(description: Description) {
    originalIntellijProgressTaskIgnoreHeadlessProperty = System.getProperty(ignoreHeadlessPropertyName)
    setValue(overrideKeepTasksAsynchronous)
  }

  override fun after(description: Description) {
    val originalValue = originalIntellijProgressTaskIgnoreHeadlessProperty
    if (originalValue == null) {
      System.clearProperty(ignoreHeadlessPropertyName)
    } else {
      System.setProperty(ignoreHeadlessPropertyName, originalValue)
    }
  }

  fun keepTasksAsynchronous() {
    setValue(true)
  }

  fun runTasksSynchronously() {
    setValue(false)
  }

  private fun setValue(overrideKeepTasksAsynchronous: Boolean): String? = System.setProperty(
    ignoreHeadlessPropertyName,
    overrideKeepTasksAsynchronous.toString()
  )
}

private const val ignoreHeadlessPropertyName = "intellij.progress.task.ignoreHeadless"
