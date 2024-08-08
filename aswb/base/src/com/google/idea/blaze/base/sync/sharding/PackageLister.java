/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.sharding;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.async.FutureUtil.FutureResult;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WildcardTargetPattern;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.prefetch.FetchExecutor;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.util.WorkspacePathUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Traverses blaze packages specified by wildcard target patterns, expanding to a set of
 * single-package target patterns.
 */
class PackageLister {

  private PackageLister() {}

  /** The set of blaze packages to prefetch prior to traversing the directory tree. */
  static Set<File> getDirectoriesToPrefetch(
      WorkspacePathResolver pathResolver,
      Collection<WildcardTargetPattern> includes,
      Predicate<WorkspacePath> excluded) {
    Set<WorkspacePath> prefetchPaths = new HashSet<>();
    for (WildcardTargetPattern pattern : includes) {
      WorkspacePath workspacePath = pattern.getBasePackage();
      if (excluded.test(workspacePath)) {
        continue;
      }
      prefetchPaths.add(workspacePath);
    }
    return WorkspacePathUtil.calculateMinimalWorkspacePaths(prefetchPaths)
        .stream()
        .map(pathResolver::resolveToFile)
        .collect(Collectors.toSet());
  }

  /**
   * Expands all-in-package-recursive wildcard targets into all-in-single-package targets by
   * traversing the file system, looking for child blaze packages.
   *
   * <p>Returns null if directory traversal failed or was cancelled.
   */
  @Nullable
  static Map<TargetExpression, List<TargetExpression>> expandPackageTargets(
      BuildSystemProvider provider,
      BlazeContext context,
      WorkspacePathResolver pathResolver,
      Collection<WildcardTargetPattern> wildcardPatterns) {
    List<ListenableFuture<Entry<TargetExpression, List<TargetExpression>>>> futures =
        Lists.newArrayList();
    for (WildcardTargetPattern pattern : wildcardPatterns) {
      if (!pattern.isRecursive() || pattern.toString().startsWith("-")) {
        continue;
      }
      File dir = pathResolver.resolveToFile(pattern.getBasePackage());
      if (!FileOperationProvider.getInstance().isDirectory(dir)) {
        continue;
      }
      futures.add(
          FetchExecutor.EXECUTOR.submit(
              () -> {
                List<TargetExpression> expandedTargets = new ArrayList<>();
                traversePackageRecursively(provider, pathResolver, dir, expandedTargets);
                return Maps.immutableEntry(pattern.originalPattern, expandedTargets);
              }));
    }
    if (futures.isEmpty()) {
      return ImmutableMap.of();
    }
    FutureResult<List<Entry<TargetExpression, List<TargetExpression>>>> result =
        FutureUtil.waitForFuture(context, Futures.allAsList(futures))
            .withProgressMessage("Expanding wildcard target patterns...")
            .timed("ExpandWildcardTargets", EventType.Other)
            .onError("Expanding wildcard target patterns failed")
            .run();
    if (!result.success()) {
      return null;
    }
    return result
        .result()
        .stream()
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (x, y) -> x));
  }

  private static void traversePackageRecursively(
      BuildSystemProvider provider,
      WorkspacePathResolver pathResolver,
      File dir,
      List<TargetExpression> output) {
    WorkspacePath path = pathResolver.getWorkspacePath(dir);
    if (path == null) {
      return;
    }
    if (provider.findBuildFileInDirectory(dir) != null) {
      output.add(TargetExpression.allFromPackageNonRecursive(path));
    }
    FileOperationProvider fileOperationProvider = FileOperationProvider.getInstance();
    File[] children = fileOperationProvider.listFiles(dir);
    if (children == null) {
      return;
    }
    for (File child : children) {
      if (fileOperationProvider.isDirectory(child)) {
        traversePackageRecursively(provider, pathResolver, child, output);
      }
    }
  }
}
