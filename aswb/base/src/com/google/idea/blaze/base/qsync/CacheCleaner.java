/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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

import com.google.idea.blaze.common.artifact.BuildArtifactCache;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.common.experiments.IntExperiment;
import com.intellij.ide.IdleTracker;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;

/** Manages cleaning of the cache at an appropriate time. */
public class CacheCleaner implements BuildArtifactCache.CleanRequest {

  private final IntExperiment TARGET_CACHE_SIZE_MB =
      new IntExperiment("build.cache.target.size.mb", 1024);
  private final IntExperiment CACHE_KEEP_ARTIFACTS_MIN_HOURS =
      new IntExperiment("build.cache.keep.artifacts.min.hours", 24);

  private static final long MB_IN_BYTES = 1024L * 1024L;

  private final Logger logger = Logger.getInstance(CacheCleaner.class);

  private final Project project;
  private final QuerySyncManager querySyncManager;
  private final AtomicReference<AccessToken> activeCleanRequest = new AtomicReference<>();

  CacheCleaner(Project project, QuerySyncManager qsm) {
    this.project = project;
    this.querySyncManager = qsm;
  }

  @Override
  public void request() {
    logger.info("Requesting cache clean");
    AccessToken newRequest =
        IdleTracker.getInstance()
            .addIdleListener((int) Duration.ofMinutes(1).toMillis(), this::doClean);
    AccessToken last = activeCleanRequest.getAndSet(newRequest);
    if (last != null) {
      // cancel the last request if there was one, to delay the clean by another minute
      last.finish();
    }
  }

  @Override
  public void cancel() {
    AccessToken last = activeCleanRequest.getAndSet(null);
    if (last != null) {
      last.finish();
    }
  }

  /** Clean the cache now. For use by internal actions only. */
  public void cleanNow() {
    doClean();
  }

  private void doClean() {
    activeCleanRequest.set(null);
    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(project, "Cleaning build cache") {
              @Override
              public void run(@NotNull ProgressIndicator progressIndicator) {
                BuildArtifactCache cache =
                    querySyncManager
                        .getLoadedProject()
                        .map(QuerySyncProject::getBuildArtifactCache)
                        .orElse(null);
                if (cache == null) {
                  return;
                }
                try {
                  logger.info("Cleaning build cache");
                  cache.clean(
                      MB_IN_BYTES * TARGET_CACHE_SIZE_MB.getValue(),
                      Duration.ofHours(CACHE_KEEP_ARTIFACTS_MIN_HOURS.getValue()));
                } catch (BuildException e) {
                  onThrowable(e);
                }
              }
            });
  }
}
