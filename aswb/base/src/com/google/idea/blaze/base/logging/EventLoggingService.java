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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.logging.utils.HighlightStats;
import com.google.idea.blaze.base.logging.utils.SyncStats;
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStats;
import com.intellij.openapi.application.ApplicationManager;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Forwards event logs to any {@link EventLogger}s available. This indirection exists so that {@link
 * EventLogger} can have a minimal API surface.
 */
public interface EventLoggingService {

  static EventLoggingService getInstance() {
    EventLoggingService service =
        ApplicationManager.getApplication().getService(EventLoggingService.class);
    return service != null ? service : new NoopEventLoggingService();
  }

  void log(SyncStats syncStats);

  void log(QuerySyncActionStats querySyncStats);

  void logCommand(Class<?> loggingClass, Command command);

  default void logEvent(Class<?> loggingClass, String eventType) {
    logEvent(loggingClass, eventType, /* keyValues= */ ImmutableMap.of());
  }

  default void logEvent(Class<?> loggingClass, String eventType, Map<String, String> keyValues) {
    logEvent(loggingClass, eventType, keyValues, /* durationInNanos= */ null);
  }

  void logEvent(
      Class<?> loggingClass,
      String eventType,
      Map<String, String> keyValues,
      @Nullable Long durationInNanos);

  void logHighlightStats(HighlightStats highlightStats);

  /** Information about an external command that was launched from the IDE. */
  @AutoValue
  abstract class Command {
    public abstract String executable();

    public abstract ImmutableList<String> arguments();

    public abstract Optional<String> subcommandName();

    public abstract Optional<String> workingDirectory();

    public abstract int exitCode();

    public abstract Duration duration();

    public static Builder builder() {
      return new AutoValue_EventLoggingService_Command.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setExecutable(String executable);

      public abstract Builder setArguments(Collection<String> arguments);

      public abstract Builder setSubcommandName(@Nullable String subcommandName);

      public abstract Builder setWorkingDirectory(@Nullable String workingDirectory);

      public abstract Builder setExitCode(int exitCode);

      public abstract Builder setDuration(Duration duration);

      public abstract Command build();
    }
  }
}
