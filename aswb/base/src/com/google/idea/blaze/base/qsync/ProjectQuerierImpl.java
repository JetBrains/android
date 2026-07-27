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

import com.google.idea.blaze.base.logging.utils.querysync.SyncQueryStats;
import com.google.idea.blaze.base.logging.utils.querysync.SyncQueryStatsScope;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.FullProjectUpdate;
import com.google.idea.blaze.qsync.ProjectRefresher;
import com.google.idea.blaze.qsync.RefreshOperation;
import com.google.idea.blaze.qsync.RefreshParameters;
import com.google.idea.blaze.qsync.project.PostQuerySyncData;
import com.google.idea.blaze.qsync.query.QuerySpec;
import com.google.idea.blaze.qsync.query.QuerySummary;
import java.util.Optional;

/** An object that knows how to */
public class ProjectQuerierImpl implements ProjectQuerier {

  private final QueryRunner queryRunner;
  private final ProjectRefresher projectRefresher;

  public ProjectQuerierImpl(QueryRunner queryRunner, ProjectRefresher projectRefresher) {
    this.queryRunner = queryRunner;
    this.projectRefresher = projectRefresher;
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
  public PostQuerySyncData update(RefreshParameters refreshParameters, BlazeContext context)
      throws BuildException {

    RefreshOperation refresh = projectRefresher.startPartialRefresh(refreshParameters, context);

    // We set the sync mode here because SyncQueryStatsScope is part of the `base` plugin,
    // which cannot be depended upon by the core `qsync` library where ProjectRefresher lives.
    SyncQueryStatsScope.fromContext(context)
        .ifPresent(
            stats ->
                stats.setSyncMode(
                    refresh instanceof FullProjectUpdate
                        ? SyncQueryStats.SyncMode.FULL
                        : SyncQueryStats.SyncMode.DELTA));

    return executeRefresh(refresh, context);
  }

  /** Executes the refresh operation and returns the resulting sync data. */
  private PostQuerySyncData executeRefresh(RefreshOperation refresh, BlazeContext context)
      throws BuildException {
    Optional<QuerySpec> spec = refresh.getQuerySpec();
    QuerySummary querySummary =
        spec.isPresent() ? queryRunner.runQuery(spec.get(), context) : QuerySummary.EMPTY;
    return refresh.createPostQuerySyncData(querySummary);
  }
}
