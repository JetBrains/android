/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.EditorTestUtil

/**
 * Marker used for caret position by [com.intellij.testFramework.EditorTestUtil.extractCaretAndSelectionMarkers]. This top-level value is
 * meant to be used in a Kotlin string template to stand out from the surrounding XML.
 */
const val caret = EditorTestUtil.CARET_TAG

/**
 * Helper function for constructing strings understood by [com.intellij.testFramework.ExpectedHighlightingData].
 *
 * Meant to be used in a Kotlin string template to stand out from the surrounding XML.
 */
infix fun String.highlightedAs(level: HighlightSeverity): String {
  // See com.intellij.testFramework.ExpectedHighlightingData
  val marker = when (level) {
    HighlightSeverity.ERROR -> "error"
    HighlightSeverity.WARNING -> "warning"
    else -> error("Don't know how to handle $this.")
  }

  return "<$marker>$this</$marker>"
}
