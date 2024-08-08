/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.logging.utils.HighlightStats;
import com.google.idea.blaze.base.logging.utils.SyncStats;
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStats;
import com.google.idea.testing.ServiceHelper;
import com.intellij.openapi.Disposable;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Provides a {@link EventLoggingService} for integration tests. */
public class MockEventLoggingService implements EventLoggingService {

  private final List<SyncStats> syncStats = Lists.newArrayList();
  private final List<QuerySyncActionStats> querySyncStats = Lists.newArrayList();

  public MockEventLoggingService(Disposable parentDisposable) {
    ServiceHelper.registerApplicationService(EventLoggingService.class, this, parentDisposable);
  }

  public ImmutableList<SyncStats> getSyncStats() {
    return ImmutableList.copyOf(syncStats);
  }

  public ImmutableList<QuerySyncActionStats> getQuerySyncStats() {
    return ImmutableList.copyOf(querySyncStats);
  }

  @Override
  public void log(SyncStats stats) {
    syncStats.add(stats);
  }

  @Override
  public void log(QuerySyncActionStats stats) {
    querySyncStats.add(stats);
  }

  @Override
  public void logCommand(Class<?> loggingClass, Command command) {}

  @Override
  public void logEvent(
      Class<?> loggingClass,
      String eventType,
      Map<String, String> keyValues,
      @Nullable Long durationInNanos) {}

  @Override
  public void logHighlightStats(HighlightStats highlightStats) {}
}
