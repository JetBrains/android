/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.libraries;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.filecache.ArtifactCache;
import com.google.idea.blaze.android.filecache.LocalArtifactCache;
import com.google.idea.blaze.android.projectsystem.RenderJarClassFileFinder;
import com.google.idea.blaze.android.sync.aspects.strategy.RenderResolveOutputGroupProvider;
import com.google.idea.blaze.android.sync.importer.BlazeImportUtil;
import com.google.idea.blaze.base.filecache.FileCache;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.common.artifact.BlazeArtifact;
import com.google.idea.blaze.common.artifact.OutputArtifactWithoutDigest;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;

/**
 * Local cache for RenderJars.
 *
 * <p>RenderJARs are used by {@link RenderJarClassFileFinder} to lookup classes for rendering. See
 * {@link RenderJarClassFileFinder} for more information about RenderJARs.
 */
public class RenderJarCache {
  public static RenderJarCache getInstance(Project project) {
    return project.getService(RenderJarCache.class);
  }

  @VisibleForTesting
  public static File getCacheDirForProject(Project project) {
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();

    if (importSettings == null) {
      throw new IllegalArgumentException(
          String.format("Could not get directory for project '%s'", project.getName()));
    }

    return new File(BlazeDataStorage.getProjectDataDir(importSettings), "renderjars");
  }

  private final Project project;
  private final File cacheDir;

  private final ArtifactCache artifactCache;

  public RenderJarCache(Project project) {
    this(
        project,
        getCacheDirForProject(project),
        new LocalArtifactCache(
            project, "Render JAR Cache", getCacheDirForProject(project).toPath()));
  }

  @VisibleForTesting
  public RenderJarCache(Project project, File cacheDir, ArtifactCache artifactCache) {
    this.project = project;
    this.cacheDir = cacheDir;
    this.artifactCache = artifactCache;
  }

  @VisibleForTesting
  public File getCacheDir() {
    return cacheDir;
  }

  private void initialize() {
    if (!RenderJarClassFileFinder.isEnabled()) {
      return;
    }
    artifactCache.initialize();
  }

  private void onSync(
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeProjectData projectData,
      SyncMode syncMode) {
    if (!RenderJarClassFileFinder.isEnabled()) {
      return;
    }
    boolean fullRefresh = syncMode == SyncMode.FULL;
    if (fullRefresh) {
      artifactCache.clearCache();
    }

    if (!RenderResolveOutputGroupProvider.buildOnSync.getValue()) {
      // Do no refresh cache if render jars are not build during syncs
      return;
    }

    boolean removeMissingFiles = syncMode == SyncMode.INCREMENTAL;

    ImmutableList<OutputArtifactWithoutDigest> artifactsToCache =
        getArtifactsToCache(projectViewSet, projectData);

    artifactCache.putAll(artifactsToCache, context, removeMissingFiles);
  }

  /**
   * This method is called after a build and re-downloads the updated artifacts belonging to {@link
   * RenderResolveOutputGroupProvider#RESOLVE_OUTPUT_GROUP} output group.
   */
  private void refresh(BlazeContext context, BlazeBuildOutputs buildOutput) {
    if (!RenderJarClassFileFinder.isEnabled()) {
      return;
    }

    ImmutableList<OutputArtifactWithoutDigest> renderJars =
        buildOutput
            .getOutputGroupArtifacts(
                RenderResolveOutputGroupProvider.RESOLVE_OUTPUT_GROUP::contains)
            .stream()
            .collect(ImmutableList.toImmutableList());
    if (renderJars.isEmpty()) {
      return;
    }

    artifactCache.putAll(renderJars, context, false);
  }

  private ImmutableList<OutputArtifactWithoutDigest> getArtifactsToCache(
      ProjectViewSet projectViewSet, BlazeProjectData projectData) {
    List<ArtifactLocation> renderJars =
        BlazeImportUtil.getSourceTargetsStream(project, projectData, projectViewSet)
            .map(TargetIdeInfo::getAndroidIdeInfo)
            .filter(Objects::nonNull)
            .map(AndroidIdeInfo::getRenderResolveJar)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    ArtifactLocationDecoder decoder = projectData.getArtifactLocationDecoder();
    List<OutputArtifactWithoutDigest> blazeArtifacts =
        decoder.resolveOutputs(renderJars).stream()
            .filter(artifact -> artifact instanceof OutputArtifactWithoutDigest)
            .map(artifact -> (OutputArtifactWithoutDigest) artifact)
            .collect(Collectors.toList());
    return ImmutableList.copyOf(blazeArtifacts);
  }

  /**
   * Returns the RenderJAR corresponding to {@code target} or null if no RenderJAR corresponding to
   * {@code target} exists in cache.
   */
  @Nullable
  public File getCachedJarForBinaryTarget(
      ArtifactLocationDecoder artifactLocationDecoder, TargetIdeInfo target) {
    if (!RenderJarClassFileFinder.isEnabled()) {
      return null;
    }
    AndroidIdeInfo androidIdeInfo = target.getAndroidIdeInfo();
    if (androidIdeInfo == null) {
      return null;
    }
    ArtifactLocation jarArtifactLocation = androidIdeInfo.getRenderResolveJar();
    if (jarArtifactLocation == null) {
      return null;
    }

    BlazeArtifact jarArtifact = artifactLocationDecoder.resolveOutput(jarArtifactLocation);
    if (!(jarArtifact instanceof OutputArtifactWithoutDigest)) {
      Logger.getInstance(RenderJarCache.class)
          .warn("Unexpected render jar that is not an OutputArtifact: " + jarArtifactLocation);
      return null;
    }
    Path jarPath = artifactCache.get((OutputArtifactWithoutDigest) jarArtifact);
    return jarPath == null ? null : jarPath.toFile();
  }

  /** Adapter to map Extension Point Implementation to ProjectService */
  public static final class FileCacheAdapter implements FileCache {
    @Override
    public String getName() {
      return "Render JAR Cache";
    }

    @Override
    public void onSync(
        Project project,
        BlazeContext context,
        ProjectViewSet projectViewSet,
        BlazeProjectData projectData,
        @Nullable BlazeProjectData oldProjectData,
        SyncMode syncMode) {
      getInstance(project).onSync(context, projectViewSet, projectData, syncMode);
    }

    @Override
    public void refreshFiles(
        Project project, BlazeContext context, BlazeBuildOutputs buildOutputs) {
      ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
      BlazeProjectData blazeProjectData =
          BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
      if (projectViewSet == null || blazeProjectData == null) {
        return;
      }
      getInstance(project).refresh(context, buildOutputs);
    }

    @Override
    public void initialize(Project project) {
      getInstance(project).initialize();
    }
  }
}
