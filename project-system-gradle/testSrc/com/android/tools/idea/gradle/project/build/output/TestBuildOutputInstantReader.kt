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
package com.android.tools.idea.gradle.project.build.output

import com.google.common.base.Splitter
import com.intellij.build.output.BuildOutputInstantReader

/**
 * A simple [BuildOutputInstantReader] useful for build output parsing tests.
 *
 * This reader simply takes an input and splits it around any newlines, omitting empty strings,
 * which mimics the behavior of [BuildOutputInstantReaderImpl]
 */
class TestBuildOutputInstantReader(
  private val lines: List<String>,
  private val parentEventId: String = "Dummy Id") : BuildOutputInstantReader {
  constructor(input: String) : this(Splitter.on("\n").omitEmptyStrings().split(input).toList())

  var currentIndex: Int = -1
    private set

  override fun getParentEventId() = parentEventId

  override fun readLine(): String? {
    currentIndex++
    return if (currentIndex >= lines.size) {
      null
    }
    else lines[currentIndex]
  }

  override fun pushBack() {
    pushBack(1)
  }

  override fun pushBack(numberOfLines: Int) {
    currentIndex -= numberOfLines
  }
}
