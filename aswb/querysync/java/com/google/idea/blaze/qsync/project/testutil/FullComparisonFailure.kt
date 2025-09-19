/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.qsync.project.testutil

import org.junit.ComparisonFailure

/**
 * A comparison failure assertion error that preserves full actual and expected data to allow
 * copy/pasting.
 */
class FullComparisonFailure(expected: String, actual: String) :
  ComparisonFailure("Values differ: ", expected.trimIndent(), actual.trim()) {
  override val message: String
    get() = buildString {
      appendLine(
        "expected:<"
      ) // Note: These lines are a pattern recognised as a comparison failure.
      appendLine(expected.prependIndent("    "))
      appendLine("> but was:<")
      appendLine(actual.prependIndent("    "))
      append(">") // Note: No new line to match the pattern.
    }
}

fun compareFormattedStrings(actual: String, expected: String) {
  if (actual.trim() != expected.trimIndent()) {
    throw FullComparisonFailure(expected = expected, actual = actual)
  }
}
