/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.targetfinder;

import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Future;
import javax.annotation.Nullable;

/** Finds information about targets matching a given label. */
public interface TargetFinder {

  ExtensionPointName<TargetFinder> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.TargetFinder");

  /**
   * Iterates through all {@link TargetFinder}s, returning a {@link Future} representing the first
   * non-null result, prioritizing any which are immediately available.
   *
   * <p>Future returns null if this no non-null result was found.
   */
  static ListenableFuture<TargetInfo> findTargetInfoFuture(Project project, Label label) {
    Iterable<Future<TargetInfo>> futures =
        Iterables.transform(
            Arrays.asList(EP_NAME.getExtensions()), f -> f.findTarget(project, label));
    return FuturesUtil.getFirstFutureSatisfyingPredicate(futures, Objects::nonNull);
  }

  /**
   * Iterates through all {@link TargetFinder}s, returning the first immediately available, non-null
   * result.
   */
  @Nullable
  static TargetInfo findTargetInfo(Project project, Label label) {
    ListenableFuture<TargetInfo> future = findTargetInfoFuture(project, label);
    return future.isDone() ? FuturesUtil.getIgnoringErrors(future) : null;
  }

  /** Returns a future for a {@link TargetInfo} corresponding to the given blaze label. */
  Future<TargetInfo> findTarget(Project project, Label label);
}
