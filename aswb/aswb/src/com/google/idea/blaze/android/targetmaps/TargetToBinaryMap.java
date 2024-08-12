/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.targetmaps;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.intellij.openapi.project.Project;
import java.util.Collection;

/** Interface to find android binary targets that depend on given target(s) */
public interface TargetToBinaryMap {
  static TargetToBinaryMap getInstance(Project project) {
    return project.getService(TargetToBinaryMap.class);
  }

  /** Returns a set of android binary targets that depend on any of the given {@code targetKeys} */
  ImmutableSet<TargetKey> getBinariesDependingOn(Collection<TargetKey> targetKeys);

  /** Returns a set of android_binary targets in the projectview */
  ImmutableSet<TargetKey> getSourceBinaryTargets();
}
