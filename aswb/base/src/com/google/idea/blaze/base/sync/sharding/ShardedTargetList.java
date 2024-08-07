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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.logging.utils.ShardStats;
import com.google.idea.blaze.base.logging.utils.ShardStats.ShardingApproach;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.common.experiments.IntExperiment;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.util.List;
import java.util.function.Function;

/** Partitioned list of blaze targets. */
public class ShardedTargetList {

  /** Number of concurrent syncs which can be run in parallel remotely. */
  public static final IntExperiment remoteConcurrentSyncs =
      new IntExperiment("number.concurrent.remote.syncs.2", 10);

  @VisibleForTesting
  final ImmutableList<? extends ImmutableList<? extends TargetExpression>> shardedTargets;

  final ShardStats shardStats;

  public ShardedTargetList(
      ImmutableList<? extends ImmutableList<? extends TargetExpression>> shardedTargets,
      ShardingApproach shardingApproach,
      int suggestedSize) {
    this.shardedTargets = shardedTargets;
    this.shardStats =
        ShardStats.create(
            suggestedSize,
            shardedTargets.stream().map(List::size).collect(toImmutableList()),
            shardingApproach);
  }

  public boolean isEmpty() {
    return shardedTargets.stream().flatMap(List::stream).findFirst().orElse(null) == null;
  }

  public int shardCount() {
    return shardedTargets.size();
  }

  public int getTotalTargets() {
    return shardedTargets.stream().mapToInt(List::size).sum();
  }

  public ShardStats shardStats() {
    return shardStats;
  }

  /**
   * Runs the provided blaze invocation on each target list shard, returning the combined {@link
   * BuildResult}. If running serially, attempts to work around out of memory errors caused by lack
   * of blaze garbage collection where possible.
   */
  public BuildResult runShardedCommand(
      Project project,
      BlazeContext context,
      Function<Integer, String> progressMessage,
      Function<List<? extends TargetExpression>, BuildResult> invocation,
      BuildInvoker binary,
      boolean invokeParallel) {
    if (isEmpty()) {
      return BuildResult.SUCCESS;
    }
    if (shardedTargets.size() == 1) {
      return invocation.apply(shardedTargets.get(0));
    }
    if (binary.supportsParallelism() && invokeParallel) {
      return runInParallel(project, context, invocation);
    }
    int progress = 0;
    BuildResult output = null;
    for (int i = 0; i < shardedTargets.size(); i++, progress++) {
      context.output(new StatusOutput(progressMessage.apply(i + 1)));
      BuildResult result = invocation.apply(shardedTargets.get(i));
      if (result.outOfMemory() && progress > 0) {
        // re-try now that blaze server has restarted
        progress = 0;
        IssueOutput.warn(retryOnOomMessage(project, i)).submit(context);
        result = invocation.apply(shardedTargets.get(i));
      }
      output = output == null ? result : BuildResult.combine(output, result);
      if (output.status == BuildResult.Status.FATAL_ERROR) {
        return output;
      }
    }
    return output;
  }

  @SuppressWarnings("Interruption")
  private BuildResult runInParallel(
      Project project,
      BlazeContext context,
      Function<List<? extends TargetExpression>, BuildResult> invocation) {
    // new executor for each sync, so we get an up-to-date experiment value. This is fine, because
    // it's just a view of the single application pool executor. Doesn't need to be shutdown for the
    // same reason
    ListeningExecutorService executor =
        MoreExecutors.listeningDecorator(
            AppExecutorUtil.createBoundedApplicationPoolExecutor(
                "RemoteBlazeExecutor", remoteConcurrentSyncs.getValue()));

    ListenableFuture<List<BuildResult>> future =
        Futures.allAsList(
            Streams.mapWithIndex(
                    shardedTargets.stream(), (s, i) -> executor.submit(() -> invocation.apply(s)))
                .collect(toImmutableList()));

    context.addCancellationHandler(() -> future.cancel(true));

    String buildSystem = Blaze.buildSystemName(project);
    List<BuildResult> results =
        FutureUtil.waitForFuture(context, future)
            .onError(String.format("%s build failed", buildSystem))
            .run()
            .result();
    if (results == null) {
      return BuildResult.FATAL_ERROR;
    }
    return results.stream().reduce(BuildResult::combine).orElse(BuildResult.FATAL_ERROR);
  }

  private String retryOnOomMessage(Project project, int shardIndex) {
    String buildSystem = Blaze.buildSystemName(project);
    return String.format(
        "%s server ran out of memory on shard %s of %s. This is generally caused by %s garbage "
            + "collection bugs. Attempting to workaround by resuming with a clean %s server.",
        buildSystem, shardIndex + 1, shardedTargets.size(), buildSystem, buildSystem);
  }
}
