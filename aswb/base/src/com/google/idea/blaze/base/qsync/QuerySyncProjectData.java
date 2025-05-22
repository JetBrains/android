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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.bazel.BazelVersion;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.RemoteOutputArtifacts;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.google.idea.blaze.qsync.project.ProjectTarget;
import com.google.idea.blaze.qsync.project.QuerySyncLanguage;
import com.intellij.openapi.diagnostic.Logger;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/** Implementation of {@link BlazeProjectData} specific to querysync. */
public class QuerySyncProjectData implements BlazeProjectData {

  private static final Logger logger = Logger.getInstance(QuerySyncProjectData.class);

  private final WorkspacePathResolver workspacePathResolver;
  private final Optional<QuerySyncProjectSnapshot> blazeProject;

  /**
   * Static language settings are those derived from the {@code .blazeproject} file. The dynamic
   * ones (those derived based on the project structure and dependency builds) are added to this
   * later.
   */
  private final WorkspaceLanguageSettings staticLanguageSettings;

  QuerySyncProjectData(
      WorkspacePathResolver workspacePathResolver,
      WorkspaceLanguageSettings workspaceLanguageSettings) {
    this(Optional.empty(), workspacePathResolver, workspaceLanguageSettings);
  }

  private QuerySyncProjectData(
      Optional<QuerySyncProjectSnapshot> projectSnapshot,
      WorkspacePathResolver workspacePathResolver,
      WorkspaceLanguageSettings workspaceLanguageSettings) {
    this.blazeProject = projectSnapshot;
    this.workspacePathResolver = workspacePathResolver;
    this.staticLanguageSettings = workspaceLanguageSettings;
  }

  public QuerySyncProjectData withSnapshot(QuerySyncProjectSnapshot newSnapshot) {
    return new QuerySyncProjectData(
        Optional.of(newSnapshot), workspacePathResolver, staticLanguageSettings);
  }

  @Nullable
  @Override
  public ProjectTarget getBuildTarget(Label label) {
    return blazeProject
        .map(it -> it.graph().getProjectTarget(com.google.idea.blaze.common.Label.of(label.toString())))
        .orElse(null);
  }

  /**
   * Returns all in project targets that depend on the source file at {@code sourcePath} via an
   * in-project dependency chain.
   *
   * <p>If project target A depends on external target B, and external target B depends on project
   * target C, target A is *not* included in {@code getReverseDeps} for a source file in target C.
   */
  public Collection<ProjectTarget> getReverseDeps(Path sourcePath) {
    return blazeProject
        .map(QuerySyncProjectSnapshot::graph)
        .map(graph -> graph.getReverseDepsForSource(sourcePath))
        .orElse(ImmutableList.of());
  }

  @Override
  public ImmutableList<Label> targets() {
    if (blazeProject.isPresent()) {
      return blazeProject.get().getAllTargets().stream()
          .map(com.google.idea.blaze.base.model.primitives.Label::create)
          .collect(ImmutableList.toImmutableList());
    }
    return ImmutableList.of();
  }

  @Override
  public WorkspacePathResolver getWorkspacePathResolver() {
    return workspacePathResolver;
  }

  @Override
  public WorkspaceLanguageSettings getWorkspaceLanguageSettings() {

    ImmutableSet<LanguageClass> projectLanguages =
        blazeProject
            .map(p -> p.project().getActiveLanguagesList())
            .map(QuerySyncLanguage::fromProtoList)
            .map(LanguageClasses::fromQuerySync)
            .orElse(ImmutableSet.of());

    return new WorkspaceLanguageSettings(
        staticLanguageSettings.getWorkspaceType(),
        ImmutableSet.<LanguageClass>builder()
            .addAll(projectLanguages)
            .addAll(staticLanguageSettings.getActiveLanguages())
            .build());
  }

  @Override
  public TargetMap getTargetMap() {
    throw new NotSupportedWithQuerySyncException("getTargetMap");
  }

  @Override
  public BlazeInfo getBlazeInfo() {
    throw new NotSupportedWithQuerySyncException("getBlazeInfo");
  }

  @Override
  public BlazeVersionData getBlazeVersionData() {
    // TODO(mathewi) Investigate uses of this, and remove them if necessary. BlazeVersionData
    //  assumes that the base VCS revision is a decimal integer, which may not be true.
    logger.warn("Usage of legacy getBlazeVersionData");
    BlazeVersionData.Builder data = BlazeVersionData.builder();
    blazeProject
        .flatMap(p -> p.queryData().vcsState())
        .map(q -> q.upstreamRevision)
        .ifPresent(
            revision -> {
              try {
                data.setClientCl(Long.parseLong(revision));
              } catch (NumberFormatException e) {
                logger.warn(e);
              }
            });
    blazeProject
        .flatMap(p -> p.queryData().bazelVersion())
        .ifPresent(version -> data.setBazelVersion(BazelVersion.parseVersion(version)));

    return data.build();
  }

  @Override
  public ArtifactLocationDecoder getArtifactLocationDecoder() {
    throw new NotSupportedWithQuerySyncException("getArtifactLocationDecoder");
  }

  @Override
  public RemoteOutputArtifacts getRemoteOutputs() {
    throw new NotSupportedWithQuerySyncException("getRemoteOutputs");
  }

  @Override
  public SyncState getSyncState() {
    throw new NotSupportedWithQuerySyncException("getSyncState");
  }

  @Override
  public boolean isQuerySync() {
    return true;
  }
}
