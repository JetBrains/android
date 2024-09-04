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
package com.google.idea.blaze.base.logging.utils.querysync;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStats.Result;
import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.google.idea.blaze.common.TimeSource;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import javax.annotation.Nullable;

/** Stores @{QuerySyncActionStats} so that it can be logged by the BlazeContext creator owner. */
public class QuerySyncActionStatsScope implements BlazeScope {
  private final QuerySyncActionStats.Builder builder;
  private final ProjectInfoStats.Builder projectInfoStatsBuilder;
  private final DependenciesInfoStats.Builder dependenciesInfoStatsBuilder;
  private final TimeSource timeSource;

  public static QuerySyncActionStatsScope create(
      Class<?> actionClass, @Nullable AnActionEvent event) {
    return createForPaths(actionClass, event, ImmutableSet.of(), () -> Instant.now());
  }

  public static QuerySyncActionStatsScope createForFile(
      Class<?> actionClass, @Nullable AnActionEvent event, VirtualFile requestFile) {
    return createForFiles(actionClass, event, ImmutableSet.of(requestFile));
  }

  public static QuerySyncActionStatsScope createForFiles(
      Class<?> actionClass,
      @Nullable AnActionEvent event,
      ImmutableCollection<VirtualFile> requestFiles) {
    return createForFiles(actionClass, event, requestFiles, () -> Instant.now());
  }

  public static QuerySyncActionStatsScope createForFiles(
      Class<?> actionClass,
      @Nullable AnActionEvent event,
      ImmutableCollection<VirtualFile> requestFiles,
      TimeSource timeSource) {
    return createForPaths(
        actionClass,
        event,
        requestFiles.stream().map(VirtualFile::toNioPath).collect(toImmutableSet()),
        timeSource);
  }

  public static QuerySyncActionStatsScope createForPaths(
      Class<?> actionClass, @Nullable AnActionEvent event, ImmutableCollection<Path> requestFiles) {
    return createForPaths(actionClass, event, requestFiles, () -> Instant.now());
  }

  public static QuerySyncActionStatsScope createForPaths(
      Class<?> actionClass,
      @Nullable AnActionEvent event,
      ImmutableCollection<Path> requestFiles,
      TimeSource timeSource) {
    return new QuerySyncActionStatsScope(actionClass, event, requestFiles, timeSource);
  }

  @VisibleForTesting
  public QuerySyncActionStatsScope(
      Class<?> actionClass,
      @Nullable AnActionEvent event,
      ImmutableCollection<Path> requestFiles,
      TimeSource timeSource) {
    builder =
        QuerySyncActionStats.builder()
            .handleActionClass(actionClass)
            .handleActionEvent(event)
            .setRequestedFiles(ImmutableSet.copyOf(requestFiles))
            .setBuildWorkingSetEnabled(QuerySyncSettings.getInstance().buildWorkingSet());
    this.timeSource = timeSource;
    projectInfoStatsBuilder = ProjectInfoStats.builder();
    dependenciesInfoStatsBuilder = DependenciesInfoStats.builder();
  }

  public QuerySyncActionStats.Builder getBuilder() {
    return builder;
  }

  public ProjectInfoStats.Builder getProjectInfoStatsBuilder() {
    return projectInfoStatsBuilder;
  }

  public DependenciesInfoStats.Builder getDependenciesInfoStatsBuilder() {
    return dependenciesInfoStatsBuilder;
  }

  public static Optional<QuerySyncActionStats.Builder> fromContext(BlazeContext context) {
    return Optional.ofNullable(context.getScope(QuerySyncActionStatsScope.class))
        .map(QuerySyncActionStatsScope::getBuilder);
  }

  @Override
  public void onScopeBegin(BlazeContext context) {
    builder.setStartTime(timeSource.now());
  }

  private Result getSyncResult(BlazeContext context) {
    if (context.isCancelled()) {
      return Result.CANCELLED;
    }
    if (context.hasErrors()) {
      return Result.FAILURE;
    }
    if (context.hasWarnings()) {
      return Result.SUCCESS_WITH_WARNING;
    }
    return Result.SUCCESS;
  }

  /** Called when the context scope is ending. */
  @Override
  public void onScopeEnd(BlazeContext context) {
    fromContext(context)
        .ifPresent(
            builder ->
                EventLoggingService.getInstance()
                    .log(
                        builder
                            .setDependenciesInfo(dependenciesInfoStatsBuilder.build())
                            .setProjectInfo(projectInfoStatsBuilder.build())
                            .setTotalClockTime(Duration.between(builder.startTime(), Instant.now()))
                            .setResult(getSyncResult(context))
                            .build()));
  }
}
