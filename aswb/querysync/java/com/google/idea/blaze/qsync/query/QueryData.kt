/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.query

import com.google.idea.blaze.common.Label

/** Contains records for storing query summary data, as an alternative to protos.  */
interface QueryData {
  /**
   * Native representation of a the parts we use from [ ].
   */
  @JvmRecord
  data class Rule(
    val label: Label,
    val ruleClass: String,
    val sources: List<Label>,
    val deps: List<Label>,
    val idlSources: List<Label>,
    val runtimeDeps: List<Label>,
    val resourceFiles: List<Label>,
    val manifest: Label?,
    val testApp: String,
    val instruments: String,
    val customPackage: String,
    val hdrs: List<Label>,
    val copts: List<String>,
    val tags: List<String>,
    val mainClass: String,
    val testRule: Label?
  ) {
    companion object {
      fun createForTests(label: Label): Rule {
        return Rule(
          label = label,
          ruleClass = "",
          sources = emptyList(),
          deps = emptyList(),
          idlSources = emptyList(),
          runtimeDeps = emptyList(),
          resourceFiles = emptyList(),
          manifest = null,
          testApp = "",
          instruments = "",
          customPackage = "",
          hdrs = emptyList(),
          copts = emptyList(),
          tags = emptyList(),
          mainClass = "",
          testRule = null
        )
      }
    }
  }

  @JvmRecord
  data class SourceFile(val label: Label, val subincliudes: Collection<Label>)
}
