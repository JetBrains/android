/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.logging.utils.querysync.SyncQueryStats;
import com.google.idea.blaze.base.logging.utils.querysync.SyncQueryStatsScope;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.vcs.BlazeVcsHandlerProvider.BlazeVcsHandler;
import com.google.idea.blaze.common.vcs.VcsState;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.FullProjectUpdate;
import com.google.idea.blaze.qsync.ProjectRefresher;
import com.google.idea.blaze.qsync.RefreshOperation;
import com.google.idea.blaze.qsync.project.PostQuerySyncData;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.query.QuerySpec;
import com.google.idea.blaze.qsync.query.QuerySummary;
import com.intellij.openapi.diagnostic.Logger;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/** An object that knows how to */
public class ProjectQuerierImpl implements ProjectQuerier {

  private static final Logger logger = Logger.getInstance(ProjectQuerierImpl.class);

  private final QueryRunner queryRunner;
  private final ProjectRefresher projectRefresher;
  private final Optional<BlazeVcsHandler> vcsHandler;
  private final BazelVersionHandler bazelVersionProvider;

  public ProjectQuerierImpl(
      QueryRunner queryRunner,
      ProjectRefresher projectRefresher,
      Optional<BlazeVcsHandler> vcsHandler,
      BazelVersionHandler bazelVersionProvider) {
    this.queryRunner = queryRunner;
    this.projectRefresher = projectRefresher;
    this.vcsHandler = vcsHandler;
    this.bazelVersionProvider = bazelVersionProvider;
  }

  public Optional<VcsState> getVcsState(BlazeContext context) {
    Optional<ListenableFuture<VcsState>> stateFuture =
        vcsHandler.flatMap(h -> h.getVcsState(context, BlazeExecutor.getInstance().getExecutor()));
    if (stateFuture.isEmpty()) {
      return Optional.empty();
    }
    try {return Optional.of(Uninterruptibles.getUninterruptibly(stateFuture.get()));
    } catch (ExecutionException e) {
      // We can continue without the VCS state, but it means that when we update later on
      // we have to re-run the entire query rather than performing an minimal update.
      context.handleExceptionAsWarning(
          "WARNING: Could not get VCS state, future updates may be suboptimal", e.getCause());
      return Optional.empty();
    }
  }

  /**
   * Performs a delta query to update the state based on the state from the last query run, if
   * possible. The project view is not reloaded.
   *
   * <p>There are various cases when we will fall back, including:
   *
   * <ul>
   *   <li>if the VCS state is not available for any reason
   *   <li>if the upstream revision has changed
   * </ul>
   */
  @Override
  public PostQuerySyncData update(
      ProjectDefinition currentProjectDef, PostQuerySyncData previousState, BlazeContext context)
      throws BuildException {

    Optional<VcsState> vcsState = getVcsState(context);
    logger.info(
        String.format(
            "Starting partial query update; upstream rev=%s; snapshot path=%s",
            vcsState.map(s -> s.upstreamRevision).orElse("<unknown>"),
            vcsState.flatMap(s -> s.workspaceSnapshotPath).map(Object::toString).orElse("<none>")));

    Optional<String> bazelVersion = Optional.empty();
    try {
      bazelVersion = bazelVersionProvider.getBazelVersion(context);
    } catch (BuildException e) {
      context.handleExceptionAsWarning("Could not get bazel version", e);
    }

    RefreshOperation refresh =
        projectRefresher.startPartialRefresh(
            context, previousState, vcsState, bazelVersion, currentProjectDef);

    // We set the sync mode here because SyncQueryStatsScope is part of the `base` plugin,
    // which cannot be depended upon by the core `qsync` library where ProjectRefresher lives.
    SyncQueryStatsScope.fromContext(context)
        .ifPresent(
            stats ->
                stats.setSyncMode(
                    refresh instanceof FullProjectUpdate
                        ? SyncQueryStats.SyncMode.FULL
                        : SyncQueryStats.SyncMode.DELTA));

    Optional<QuerySpec> spec = refresh.getQuerySpec();
    QuerySummary querySummary =
        spec.isPresent() ? queryRunner.runQuery(spec.get(), context) : QuerySummary.EMPTY;
    return refresh.createPostQuerySyncData(querySummary);
  }
}
