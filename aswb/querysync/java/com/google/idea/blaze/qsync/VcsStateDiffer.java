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
package com.google.idea.blaze.qsync;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.common.vcs.VcsState;
import com.google.idea.blaze.exception.BuildException;
import java.nio.file.Path;
import java.util.Optional;

/** Provides diffs between two VCS states. */
public interface VcsStateDiffer {

  /**
   * No differ - i.e. one that always returns {@link Optional#empty()} indicating that the VCS does
   * not support providing diffs between two VCS states.
   */
  VcsStateDiffer NONE = (recent, earlier) -> Optional.empty();

  /**
   * Finds which files changes between two points in time.
   *
   * @param recent VCS state at a recent point in time.
   * @param earlier VCS state at an earlier point in time.
   * @return The set of files that actually changed between {@code recent} and {@code earlier}, or
   *     empty of this cannot be calculated, as workspace relative paths.
   */
  Optional<ImmutableSet<Path>> getFilesChangedBetween(VcsState recent, VcsState earlier)
      throws BuildException;
}
