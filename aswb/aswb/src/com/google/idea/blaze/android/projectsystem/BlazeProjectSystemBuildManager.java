/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.projectsystem;

import static java.lang.Math.max;

import com.android.annotations.concurrency.UiThread;
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager;
import com.google.idea.blaze.base.build.BlazeBuildListener;
import com.google.idea.blaze.base.build.BlazeBuildService;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.Topic;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;

/** Blaze implementation of {@link ProjectSystemBuildManager} */
public class BlazeProjectSystemBuildManager implements ProjectSystemBuildManager {
  private static final Topic<ProjectSystemBuildManager.BuildListener> PROJECT_SYSTEM_BUILD_TOPIC =
      new Topic<>("Blaze Project Build", ProjectSystemBuildManager.BuildListener.class);

  private final Project project;

  BlazeProjectSystemBuildManager(Project project) {
    this.project = project;
  }

  @Override
  public void compileProject() {
    BlazeBuildService.getInstance(project).buildProject();
  }

  @Override
  public void addBuildListener(
      Disposable parentDisposable, ProjectSystemBuildManager.BuildListener buildListener) {
    project
        .getMessageBus()
        .connect(parentDisposable)
        .subscribe(PROJECT_SYSTEM_BUILD_TOPIC, buildListener);
  }

  @NotNull
  @Override
  public BuildResult getLastBuildResult() {
    return LastBuildResultCache.getInstance(project).getLastBuildResult();
  }

  @UiThread
  // @Override #api221
  public boolean isBuilding() {
    return BlazeBuildCounter.getInstance(project).isBuilding();
  }

  @Service
  static final class BlazeBuildCounter {
    private final AtomicInteger buildCount = new AtomicInteger(0);

    public BlazeBuildCounter(Project project) {}

    public void onBuildStarted() {
      buildCount.incrementAndGet();
    }

    public void onBuildCompleted() {
      buildCount.updateAndGet(i -> max(i - 1, 0));
    }

    public boolean isBuilding() {
      return buildCount.get() > 0;
    }

    public static BlazeBuildCounter getInstance(Project project) {
      return project.getService(BlazeBuildCounter.class);
    }
  }

  /**
   * Class to publish BlazeBuildListener callbacks to {@link
   * BlazeProjectSystemBuildManager#PROJECT_SYSTEM_BUILD_TOPIC}
   */
  static final class BuildCallbackPublisher implements BlazeBuildListener {
    @Override
    public void buildStarting(Project project) {
      BlazeBuildCounter.getInstance(project).onBuildStarted();
      project
          .getMessageBus()
          .syncPublisher(PROJECT_SYSTEM_BUILD_TOPIC)
          .buildStarted(
              BuildMode.COMPILE_OR_ASSEMBLE); // Blaze build currently only supports compilation
    }

    @Override
    public void buildCompleted(Project project, BlazeBuildOutputs buildOutputs) {
      LastBuildResultCache lastBuildResultCache = LastBuildResultCache.getInstance(project);
      BuildResult projectSystemBuildResult =
          lastBuildResultCache.updateBuildResult(buildOutputs.buildResult);

      // BlazeBuildListener does not have a concept of `beforeBuildCompleted` so we call both
      // `beforeBuildCompleted` and `buildCompleted` in required order here.
      project
          .getMessageBus()
          .syncPublisher(PROJECT_SYSTEM_BUILD_TOPIC)
          .beforeBuildCompleted(projectSystemBuildResult);
      BlazeBuildCounter.getInstance(project).onBuildCompleted();
      project
          .getMessageBus()
          .syncPublisher(PROJECT_SYSTEM_BUILD_TOPIC)
          .buildCompleted(projectSystemBuildResult);
    }
  }

  /** Caches the Build result from the most recent build */
  static final class LastBuildResultCache {
    private static LastBuildResultCache getInstance(Project project) {
      return project.getService(LastBuildResultCache.class);
    }

    private BuildResult lastBuildResult = BuildResult.createUnknownBuildResult();

    private BuildResult updateBuildResult(
        com.google.idea.blaze.base.sync.aspects.BuildResult buildResult) {
      lastBuildResult = new BuildResult(BuildMode.COMPILE_OR_ASSEMBLE, mapBuildStatus(buildResult));
      return lastBuildResult;
    }

    private BuildResult getLastBuildResult() {
      return lastBuildResult;
    }

    private static BuildStatus mapBuildStatus(
        com.google.idea.blaze.base.sync.aspects.BuildResult buildResult) {
      switch (buildResult.status) {
        case SUCCESS:
          return BuildStatus.SUCCESS;
        case BUILD_ERROR:
        case FATAL_ERROR:
          return BuildStatus.FAILED;
      }
      return BuildStatus.UNKNOWN;
    }
  }
}

