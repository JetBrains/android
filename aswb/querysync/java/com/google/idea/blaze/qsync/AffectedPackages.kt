/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync

import java.nio.file.Path

/** Encapsulates build packages that are affected by changes to files in the project view, and logic to calculate that. */
data class AffectedPackages(val modifiedPackages: Set<Path> = emptySet(), val deletedPackages: Set<Path> = emptySet()) {
  fun isEmpty(): Boolean = modifiedPackages.isEmpty() && deletedPackages.isEmpty()

  companion object {
    @JvmField val EMPTY = AffectedPackages()
  }
}
