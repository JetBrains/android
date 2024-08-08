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
package com.google.idea.blaze.base.logging;

import com.google.idea.blaze.base.logging.utils.HighlightStats;
import com.google.idea.blaze.base.logging.utils.SyncStats;
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStats;
import java.util.Map;
import javax.annotation.Nullable;

/** An {@link EventLoggingService} that does nothing, used in case there isn't one registered. */
public final class NoopEventLoggingService implements EventLoggingService {

  @Override
  public void log(SyncStats syncStats) {}

  @Override
  public void log(QuerySyncActionStats syncStats) {}

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
