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
package com.google.idea.blaze.qsync;

import com.google.idea.blaze.qsync.project.PostQuerySyncData;
import com.google.idea.blaze.qsync.query.QuerySpec;
import com.google.idea.blaze.qsync.query.QuerySummary;
import java.io.IOException;
import java.util.Optional;

/**
 * Represents an operation that is refreshing a {@link QuerySyncProjectSnapshot}.
 *
 * <p>To use this interface:
 *
 * <ol>
 *   <li>Acquire an implementation, e.g. from {@link ProjectRefresher}
 *   <li>Run a {@code query} invocation based on the spec from {@link #getQuerySpec()}
 *   <li>To get the updated {@link PostQuerySyncData} pass the results of that query to {@link
 *       #createPostQuerySyncData(QuerySummary)}.
 * </ol>
 */
public interface RefreshOperation {

  /** Returns the spec of the query to be run for this strategy. */
  Optional<QuerySpec> getQuerySpec() throws IOException;

  /** Creates the new project snapshot. */
  PostQuerySyncData createPostQuerySyncData(QuerySummary output);
}
