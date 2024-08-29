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
package com.android.tools.idea.gradle.project.build.output.tomlParser

import com.intellij.build.output.BuildOutputInstantReader

class ResettableReader(val reader: BuildOutputInstantReader) : BuildOutputInstantReader {
  private var linesRead = 0

  override fun getParentEventId(): Any = reader.parentEventId

  override fun readLine(): String? =
    reader.readLine().also { linesRead += 1 }

  fun resetPosition() {
    reader.pushBack(linesRead)
    linesRead = 0
  }

  override fun pushBack() {
    reader.pushBack()
    linesRead -= 1
  }

  override fun pushBack(numberOfLines: Int) {
    reader.pushBack(numberOfLines)
    linesRead -= numberOfLines
  }
}