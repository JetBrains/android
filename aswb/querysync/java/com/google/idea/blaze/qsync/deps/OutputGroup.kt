/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.deps

/** Represents an output group produced by the `build_dependencies.bzl` aspect.  */
enum class OutputGroup(
  val outputGroupName: String,

  /**
   * Indicates whether artifacts need to be added to the artifact tracker.
   */
  val usedBySymbolResolution: Boolean = false
) {
  JARS("qs_jars", usedBySymbolResolution = true),
  TRANSITIVE_RUNTIME_JARS("qs_transitive_runtime_jars"),
  AARS("qs_aars", usedBySymbolResolution = true),
  GENSRCS("qs_gensrcs", usedBySymbolResolution = true),
  ARTIFACT_INFO_FILE("qs_info"),
  JDEPS("qs_jdeps"),
  CC_HEADERS("qs_cc_headers", usedBySymbolResolution = true),
  CC_INFO_FILE("qs_cc_info");
}
