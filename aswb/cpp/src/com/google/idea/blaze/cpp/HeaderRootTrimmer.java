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
package com.google.idea.blaze.cpp;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.ideinfo.CToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.ScopedFunction;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolver;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Collects possible -I, -isystem, -iquote search roots and determines which are actually viable.
 *
 * <p>Namely, some of the roots are optimistically in output directories (genfiles, bin), and exist,
 * but may contain no more than aspect files or .cppmaps (does not actually contain headers). In
 * such cases, there is no reason to actually search those roots, and they won't change until the
 * next build/sync (unlike source directories).
 */
final class HeaderRootTrimmer {

  private static final Logger logger = Logger.getInstance(HeaderRootTrimmer.class);
  // Don't recursively check too many directories, in case the root is just too big.
  // Sometimes genfiles/java is considered a header search root.
  private static final int GEN_HEADER_ROOT_SEARCH_LIMIT = 50;

  static ImmutableSet<File> getValidRoots(
      BlazeContext parentContext,
      BlazeProjectData blazeProjectData,
      ImmutableMap<TargetKey, CToolchainIdeInfo> toolchainLookupMap,
      Predicate<TargetIdeInfo> targetFilter,
      ExecutionRootPathResolver executionRootPathResolver) {
    // Type specification needed to avoid incorrect type inference during command line build.
    return Scope.push(
        parentContext,
        (ScopedFunction<ImmutableSet<File>>)
            context -> {
              context.push(new TimingScope("Resolve header include roots", EventType.Other));
              Set<ExecutionRootPath> paths =
                  collectExecutionRootPaths(
                      blazeProjectData.getTargetMap(), targetFilter, toolchainLookupMap);
              return doCollectHeaderRoots(
                  context, blazeProjectData, paths, executionRootPathResolver);
            });
  }

  private static ImmutableSet<File> doCollectHeaderRoots(
      BlazeContext context,
      BlazeProjectData projectData,
      Set<ExecutionRootPath> rootPaths,
      ExecutionRootPathResolver pathResolver) {
    Set<File> validRoots = Sets.newConcurrentHashSet();
    List<ListenableFuture<File>> futures = Lists.newArrayListWithCapacity(rootPaths.size());
    AtomicInteger genRootsWithHeaders = new AtomicInteger();
    AtomicInteger genRootsWithoutHeaders = new AtomicInteger();
    for (ExecutionRootPath path : rootPaths) {
      futures.add(
          submit(
              () -> {
                ImmutableList<File> possibleDirectories =
                    pathResolver.resolveToIncludeDirectories(path);
                if (possibleDirectories.isEmpty()) {
                  logger.info(String.format("Couldn't resolve include root: %s", path));
                }
                for (File file : possibleDirectories) {
                  VirtualFile vf = VfsUtils.resolveVirtualFile(file, /* refreshIfNeeded= */ true);
                  if (vf != null) {
                    // Check gen directories to see if they actually contain headers and not just
                    // other random generated files (like .s, .cc, or module maps).
                    // Also checks bin directories to see if they actually contain headers vs
                    // just aspect files.
                    if (!isOutputArtifact(projectData.getBlazeInfo(), path)) {
                      validRoots.add(file);
                    } else if (genRootMayContainHeaders(vf)) {
                      genRootsWithHeaders.incrementAndGet();
                      validRoots.add(file);
                    } else {
                      genRootsWithoutHeaders.incrementAndGet();
                    }
                  } else if (!isOutputArtifact(projectData.getBlazeInfo(), path)
                      && FileOperationProvider.getInstance().exists(file)) {
                    // If it's not a blaze output file, we expect it to always resolve.
                    logger.info(String.format("Unresolved header root %s", file.getAbsolutePath()));
                  }
                }
                return null;
              }));
    }
    try {
      Futures.allAsList(futures).get();
      ImmutableSet<File> result = ImmutableSet.copyOf(validRoots);
      logger.info(
          String.format(
              "CollectHeaderRoots: %s roots, (%s, %s) genroots with/without headers",
              result.size(), genRootsWithHeaders.get(), genRootsWithoutHeaders.get()));
      return result;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      context.setCancelled();
    } catch (ExecutionException e) {
      IssueOutput.error("Error resolving header include roots: " + e).submit(context);
      logger.error("Error resolving header include roots", e);
    }
    return ImmutableSet.of();
  }

  private static boolean genRootMayContainHeaders(VirtualFile directory) {
    int totalDirectoriesChecked = 0;
    Queue<VirtualFile> worklist = new ArrayDeque<>();
    worklist.add(directory);
    while (!worklist.isEmpty()) {
      totalDirectoriesChecked++;
      if (totalDirectoriesChecked > GEN_HEADER_ROOT_SEARCH_LIMIT) {
        return true;
      }
      VirtualFile dir = worklist.poll();
      for (VirtualFile child : dir.getChildren()) {
        if (child.isDirectory()) {
          worklist.add(child);
          continue;
        }
        String fileExtension = child.getExtension();
        if (Strings.isNullOrEmpty(fileExtension)) {
          // Conservatively allow extension-less headers (though hopefully rare for generated srcs
          // vs the standard library). Could count extension-less binaries in bin/ directory.
          return true;
        }
        if (CFileExtensions.HEADER_EXTENSIONS.contains(fileExtension)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isOutputArtifact(BlazeInfo blazeInfo, ExecutionRootPath path) {
    return ExecutionRootPath.isAncestor(blazeInfo.getBlazeGenfiles(), path, false)
        || ExecutionRootPath.isAncestor(blazeInfo.getBlazeBin(), path, false);
  }

  private static Set<ExecutionRootPath> collectExecutionRootPaths(
      TargetMap targetMap,
      Predicate<TargetIdeInfo> targetFilter,
      ImmutableMap<TargetKey, CToolchainIdeInfo> toolchainLookupMap) {
    Set<ExecutionRootPath> paths = Sets.newHashSet();
    for (TargetIdeInfo target : targetMap.targets()) {
      if (target.getcIdeInfo() != null && targetFilter.test(target)) {
        paths.addAll(target.getcIdeInfo().getTransitiveSystemIncludeDirectories());
        paths.addAll(target.getcIdeInfo().getTransitiveIncludeDirectories());
        paths.addAll(target.getcIdeInfo().getTransitiveQuoteIncludeDirectories());
      }
    }
    Set<CToolchainIdeInfo> toolchains = new LinkedHashSet<>(toolchainLookupMap.values());
    for (CToolchainIdeInfo toolchain : toolchains) {
      paths.addAll(toolchain.getBuiltInIncludeDirectories());
    }
    return paths;
  }

  static <T> ListenableFuture<T> submit(Callable<T> callable) {
    return BlazeExecutor.getInstance().submit(callable);
  }
}
