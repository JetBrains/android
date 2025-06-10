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
package com.google.idea.blaze.common

import java.nio.file.Path

interface TargetTree {
  /**
   * Returns all targets in the target tree.
   */
  fun getTargets(): Collection<Label>

  /**
   * Returns targets directly under the tree root package.
   */
  fun getDirectTargets(packagePath: Path): Collection<Label>

  /**
   * Returns targets of a subtree rooted at the given path.
   */
  fun getSubpackages(pkg: Path): Collection<Label>

  /**
   * The number of synced supported targets.
   *
   * It should only be used for stats logging as it may be inaccurate.
   */
  val targetCountForStatsOnly: Int

  companion object {
    @JvmStatic
    fun create(targets: Collection<Label>): TargetTree {
      return TargetTreeImpl.create(targets)
    }
  }
}
