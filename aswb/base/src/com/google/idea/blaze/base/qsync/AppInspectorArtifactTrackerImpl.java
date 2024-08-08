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
package com.google.idea.blaze.base.qsync;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.exception.BuildException;
import java.nio.file.Path;
import java.util.Set;

/** A local cache of built app inspector artifacts. */
public class AppInspectorArtifactTrackerImpl implements AppInspectorArtifactTracker {
  // TODO(b/323346056) implement this!

  @Override
  public ImmutableSet<Path> update(
      Set<Label> targets, AppInspectorInfo appInspectorInfo, BlazeContext context)
      throws BuildException {
    return ImmutableSet.of();
  }
}
