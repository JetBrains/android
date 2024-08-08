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
package com.google.idea.blaze.base.qsync;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.common.artifact.OutputArtifact;

/** A data class that collecting and converting render jar artifacts. */
@AutoValue
public abstract class AppInspectorInfo {

  @VisibleForTesting public static final AppInspectorInfo EMPTY = create(ImmutableList.of(), 0);

  public abstract ImmutableList<OutputArtifact> getJars();

  public abstract int getExitCode();

  public boolean isEmpty() {
    return getJars().isEmpty();
  }

  public static AppInspectorInfo create(ImmutableList<OutputArtifact> jars, int exitCode) {
    return new AutoValue_AppInspectorInfo(jars, exitCode);
  }
}
