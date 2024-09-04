/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.dependencies;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.run.targetfinder.FuturesUtil;
import com.intellij.openapi.project.Project;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * A helper class to complement {@link SourceToTargetProvider}. Separated from that class to keep
 * that shared API minimal.
 */
public final class SourceToTargetHelper {

  private SourceToTargetHelper() {}

  /**
   * Returns the blaze targets provided by the first available {@link SourceToTargetProvider} able
   * to handle the given source file, prioritizing any which are immediately available.
   *
   * <p>Future returns null if no provider was able to handle the given source file.
   */
  static ListenableFuture<List<TargetInfo>> findTargetsBuildingSourceFile(
      Project project, String workspaceRelativePath) {
    Iterable<Future<List<TargetInfo>>> futures =
        Arrays.stream(SourceToTargetProvider.EP_NAME.getExtensions())
            .map(f -> f.getTargetsBuildingSourceFile(project, workspaceRelativePath))
            .collect(Collectors.toList());
    ListenableFuture<List<TargetInfo>> future =
        FuturesUtil.getFirstFutureSatisfyingPredicate(futures, Objects::nonNull);
    return Futures.transform(
        future,
        list -> list == null ? null : SourceToTargetFilteringStrategy.filterTargets(list),
        MoreExecutors.directExecutor());
  }
}
