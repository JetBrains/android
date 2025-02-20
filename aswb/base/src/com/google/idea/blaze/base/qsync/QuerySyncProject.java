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
package com.google.idea.blaze.base.qsync;

import static com.google.idea.blaze.base.qsync.DependencyTracker.DependencyBuildRequest.multiTarget;
import static com.google.idea.blaze.base.qsync.DependencyTracker.DependencyBuildRequest.wholeProject;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteSource;
import com.google.common.io.MoreFiles;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.logging.utils.querysync.BuildDepsStatsScope;
import com.google.idea.blaze.base.logging.utils.querysync.SyncQueryStatsScope;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.qsync.artifacts.ProjectArtifactStore;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.google.idea.blaze.base.util.SaveUtil;
import com.google.idea.blaze.common.AtomicFileWriter;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.common.artifact.BuildArtifactCache;
import com.google.idea.blaze.common.vcs.VcsState;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.ProjectProtoTransform;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.google.idea.blaze.qsync.SnapshotBuilder;
import com.google.idea.blaze.qsync.SnapshotHolder;
import com.google.idea.blaze.qsync.deps.ArtifactTracker;
import com.google.idea.blaze.qsync.project.PostQuerySyncData;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.SnapshotDeserializer;
import com.google.idea.blaze.qsync.project.SnapshotSerializer;
import com.google.idea.blaze.qsync.project.TargetsToBuild;
import com.google.protobuf.CodedOutputStream;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Encapsulates a loaded querysync project and it's dependencies.
 *
 * <p>This class also maintains a {@link QuerySyncProjectData} instance whose job is to expose
 * project state to the rest of the plugin and IDE.
 */
public class QuerySyncProject {

  private static final Logger logger = Logger.getInstance(QuerySyncProject.class);

  private final Path snapshotFilePath;
  private final Project project;
  private final SnapshotHolder snapshotHolder;
  private final BlazeImportSettings importSettings;
  private final WorkspaceRoot workspaceRoot;
  private final ArtifactTracker<?> artifactTracker;
  private final BuildArtifactCache buildArtifactCache;
  private final ProjectArtifactStore artifactStore;
  private final RenderJarArtifactTracker renderJarArtifactTracker;
  private final AppInspectorArtifactTracker appInspectorArtifactTracker;
  private final DependencyTracker dependencyTracker;
  private final RenderJarTracker renderJarTracker;
  private final AppInspectorTracker appInspectorTracker;
  private final ProjectQuerier projectQuerier;
  private final SnapshotBuilder snapshotBuilder;
  private final ProjectDefinition projectDefinition;
  private final ProjectViewSet projectViewSet;
  // TODO(mathewi) only one of these two should strictly be necessary:
  private final WorkspacePathResolver workspacePathResolver;
  private final ProjectPath.Resolver projectPathResolver;
  private final QuerySyncSourceToTargetMap sourceToTargetMap;

  private final BuildSystem buildSystem;
  private final ProjectProtoTransform.Registry projectProtoTransforms;

  private volatile QuerySyncProjectData projectData;

  public QuerySyncProject(
      Project project,
      Path snapshotFilePath,
      SnapshotHolder snapshotHolder,
      BlazeImportSettings importSettings,
      WorkspaceRoot workspaceRoot,
      ArtifactTracker<?> artifactTracker,
      BuildArtifactCache buildArtifactCache,
      ProjectArtifactStore artifactStore,
      RenderJarArtifactTracker renderJarArtifactTracker,
      AppInspectorArtifactTracker appInspectorArtifactTracker,
      DependencyTracker dependencyTracker,
      RenderJarTracker renderJarTracker,
      AppInspectorTracker appInspectorTracker,
      ProjectQuerier projectQuerier,
      SnapshotBuilder snapshotBuilder,
      ProjectDefinition projectDefinition,
      ProjectViewSet projectViewSet,
      WorkspacePathResolver workspacePathResolver,
      ProjectPath.Resolver projectPathResolver,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      QuerySyncSourceToTargetMap sourceToTargetMap,
      BuildSystem buildSystem,
      ProjectProtoTransform.Registry projectProtoTransforms) {
    this.project = project;
    this.snapshotFilePath = snapshotFilePath;
    this.snapshotHolder = snapshotHolder;
    this.importSettings = importSettings;
    this.workspaceRoot = workspaceRoot;
    this.artifactTracker = artifactTracker;
    this.buildArtifactCache = buildArtifactCache;
    this.artifactStore = artifactStore;
    this.renderJarArtifactTracker = renderJarArtifactTracker;
    this.appInspectorArtifactTracker = appInspectorArtifactTracker;
    this.dependencyTracker = dependencyTracker;
    this.renderJarTracker = renderJarTracker;
    this.appInspectorTracker = appInspectorTracker;
    this.projectQuerier = projectQuerier;
    this.snapshotBuilder = snapshotBuilder;
    this.projectDefinition = projectDefinition;
    this.projectViewSet = projectViewSet;
    this.workspacePathResolver = workspacePathResolver;
    this.projectPathResolver = projectPathResolver;
    this.sourceToTargetMap = sourceToTargetMap;
    this.buildSystem = buildSystem;
    this.projectProtoTransforms = projectProtoTransforms;
    projectData = new QuerySyncProjectData(workspacePathResolver, workspaceLanguageSettings);
  }

  public Project getIdeProject() {
    return project;
  }

  public BlazeImportSettings getImportSettings() {
    return importSettings;
  }

  public ProjectViewSet getProjectViewSet() {
    return projectViewSet;
  }

  public WorkspaceRoot getWorkspaceRoot() {
    return workspaceRoot;
  }

  public SnapshotHolder getSnapshotHolder() {
    return snapshotHolder;
  }

  public QuerySyncProjectData getProjectData() {
    return projectData;
  }

  public WorkspacePathResolver getWorkspacePathResolver() {
    return workspacePathResolver;
  }

  public ProjectPath.Resolver getProjectPathResolver() {
    return projectPathResolver;
  }

  public BuildArtifactCache getBuildArtifactCache() {
    return buildArtifactCache;
  }

  public ProjectArtifactStore getArtifactStore() {
    return artifactStore;
  }

  public ArtifactTracker<?> getArtifactTracker() {
    return artifactTracker;
  }

  public RenderJarArtifactTracker getRenderJarArtifactTracker() {
    return renderJarArtifactTracker;
  }

  public AppInspectorArtifactTracker getAppInspectorArtifactTracker() {
    return appInspectorArtifactTracker;
  }

  public SourceToTargetMap getSourceToTargetMap() {
    return sourceToTargetMap;
  }

  public ProjectDefinition getProjectDefinition() {
    return projectDefinition;
  }

  public BuildSystem getBuildSystem() {
    return buildSystem;
  }

  public void fullSync(BlazeContext context) throws BuildException {
    sync(context, Optional.empty());
  }

  public void deltaSync(BlazeContext context) throws BuildException {
    syncWithCurrentSnapshot(context);
  }

  private void syncWithCurrentSnapshot(BlazeContext context) throws BuildException {
    sync(context, snapshotHolder.getCurrent().map(QuerySyncProjectSnapshot::queryData));
  }

  public void sync(BlazeContext parentContext, Optional<PostQuerySyncData> lastQuery)
      throws BuildException {
    try (BlazeContext context = BlazeContext.create(parentContext)) {
      context.push(new SyncQueryStatsScope());
      try {
        for (SyncListener syncListener : SyncListener.EP_NAME.getExtensionList()) {
          syncListener.onQuerySyncStart(project, context);
        }

        SaveUtil.saveAllFiles();
        PostQuerySyncData postQuerySyncData =
            lastQuery.isEmpty()
                ? projectQuerier.fullQuery(projectDefinition, context)
                : projectQuerier.update(projectDefinition, lastQuery.get(), context);
        updateProjectSnapshot(context, postQuerySyncData);

        // TODO: Revisit SyncListeners once we switch fully to qsync
        for (SyncListener syncListener : SyncListener.EP_NAME.getExtensions()) {
          // A callback shared between the old and query sync implementations.
          syncListener.onSyncComplete(
              project,
              context,
              importSettings,
              projectViewSet,
              ImmutableSet.of(),
              projectData,
              SyncMode.FULL,
              SyncResult.SUCCESS);
        }
      } catch (IOException e) {
        throw new BuildException(e);
      } finally {
        for (SyncListener syncListener : SyncListener.EP_NAME.getExtensions()) {
          // A query sync specific callback.
          syncListener.afterQuerySync(project, context);
        }
      }
    }
  }

  /**
   * Returns the list of project targets related to the given workspace file.
   *
   * @param context Context
   * @param workspaceRelativePath Workspace relative file path to find targets for. This may be a
   *     source file, directory or BUILD file.
   * @return Corresponding project targets. For a source file, this is the targets that build that
   *     file. For a BUILD file, it's the set or targets defined in that file. For a directory, it's
   *     the set of all targets defined in all build packages within the directory (recursively).
   */
  public TargetsToBuild getProjectTargets(BlazeContext context, Path workspaceRelativePath) {
    return snapshotHolder
        .getCurrent()
        .map(snapshot -> snapshot.graph().getProjectTargets(context, workspaceRelativePath))
        .orElse(TargetsToBuild.NONE);
  }

  /** Returns the set of targets with direct dependencies on {@code targets}. */
  public ImmutableSet<Label> getTargetsDependingOn(Set<Label> targets) {
    QuerySyncProjectSnapshot snapshot = snapshotHolder.getCurrent().orElseThrow();
    return snapshot.graph().getSameLanguageTargetsDependingOn(targets);
  }

  /** Returns workspace-relative paths of modified files, according to the VCS */
  public ImmutableSet<Path> getWorkingSet(BlazeContext context) throws BuildException {
    SaveUtil.saveAllFiles();
    VcsState vcsState;
    Optional<VcsState> computed = projectQuerier.getVcsState(context);
    if (computed.isPresent()) {
      vcsState = computed.get();
    } else {
      context.output(new PrintOutput("Failed to compute working set. Falling back on sync data"));
      QuerySyncProjectSnapshot snapshot = snapshotHolder.getCurrent().orElseThrow();
      vcsState =
          snapshot
              .queryData()
              .vcsState()
              .orElseThrow(
                  () -> new BuildException("No VCS state, cannot calculate affected targets"));
    }
    return vcsState.modifiedFiles();
  }

  public void cleanDependencies(BlazeContext context) throws BuildException {
    try {
      artifactTracker.clear();
    } catch (IOException e) {
      throw new BuildException("Failed to clear dependency info", e);
    }
    updateProjectSnapshot(context, snapshotHolder.getCurrent().orElseThrow().queryData());
  }

  public void resetQuerySyncState(BlazeContext context) throws BuildException {
    invalidateQuerySyncState(context);
    fullSync(context);
  }

  public void invalidateQuerySyncState(BlazeContext context) throws BuildException {
    try {
      artifactTracker.clear();
    } catch (IOException e) {
      throw new BuildException("Failed to clear dependency info", e);
    }
    onNewSnapshot(context, QuerySyncProjectSnapshot.EMPTY);
  }

  public void build(BlazeContext parentContext, DependencyTracker.DependencyBuildRequest request)
      throws IOException, BuildException {
    try (BlazeContext context = BlazeContext.create(parentContext)) {
      context.push(new BuildDepsStatsScope());
      if (getDependencyTracker().buildDependenciesForTargets(context, request)) {
        updateProjectSnapshot(context, snapshotHolder.getCurrent().orElseThrow().queryData());
      }
    }
  }

  private void updateProjectSnapshot(BlazeContext context, PostQuerySyncData queryData)
    throws BuildException {
    QuerySyncProjectSnapshot newSnapshot =
      snapshotBuilder.createBlazeProjectSnapshot(
        context,
        queryData,
        artifactTracker.getStateSnapshot(),
        projectProtoTransforms.getComposedTransform());
    onNewSnapshot(context, newSnapshot);
  }

  public void buildRenderJar(BlazeContext parentContext, List<Path> wps)
      throws IOException, BuildException {
    try (BlazeContext context = BlazeContext.create(parentContext)) {
      context.push(new BuildDepsStatsScope());
      renderJarTracker.buildRenderJarForFile(context, wps);
    }
  }

  public ImmutableCollection<Path> buildAppInspector(
      BlazeContext parentContext, Label inspector) throws IOException, BuildException {
    try (BlazeContext context = BlazeContext.create(parentContext)) {
      context.push(new BuildDepsStatsScope());
      return appInspectorTracker.buildAppInspector(context, inspector);
    }
  }

  public DependencyTracker getDependencyTracker() {
    return dependencyTracker;
  }

  public void enableAnalysis(BlazeContext context, Set<Label> projectTargets)
      throws BuildException {
    try {
      context.output(
          PrintOutput.output(
              "Building dependencies for:\n  " + Joiner.on("\n  ").join(projectTargets)));
      build(context, multiTarget(projectTargets));
    } catch (IOException e) {
      throw new BuildException("Failed to build dependencies", e);
    }
  }

  public void enableAnalysis(BlazeContext context) throws BuildException {
    try {
      context.output(PrintOutput.output("Building dependencies for project"));
      build(context, wholeProject());
    } catch (IOException e) {
      throw new BuildException("Failed to build dependencies", e);
    }
  }

  public boolean canEnableAnalysisFor(Path workspacePath) {
    return !getProjectTargets(BlazeContext.create(), workspacePath).isEmpty();
  }

  public void enableRenderJar(BlazeContext context, PsiFile psiFile, Set<Label> targets)
      throws BuildException {
    try {
      // Building render jar also requires building dependencies and resolving/analysis
      // (b/309154453#comment5), so we invoke both actions
      // TODO(b/336628891): Combine both aspects (build render jars, build dependencies) into a
      // single build
      enableAnalysis(context, targets);
      Path path = Paths.get(psiFile.getVirtualFile().getPath());
      String rel = workspaceRoot.path().relativize(path).toString();
      buildRenderJar(context, ImmutableList.of(WorkspacePath.createIfValid(rel).asPath()));
    } catch (IOException e) {
      throw new BuildException("Failed to build render jar", e);
    }
  }

  public boolean isReadyForAnalysis(Path path) {
    if (path == null || !path.startsWith(workspaceRoot.path())) {
      // Not in the workspace.
      // p == null can occur if the file is a zip entry.
      return true;
    }

    Set<Label> pendingTargets =
        snapshotHolder
            .getCurrent()
            .map(s -> s.getPendingTargets(workspaceRoot.relativize(path)))
            .orElse(ImmutableSet.of());
    return pendingTargets.isEmpty();
  }

  public Optional<PostQuerySyncData> readSnapshotFromDisk(BlazeContext context) throws IOException {
    File f = snapshotFilePath.toFile();
    if (!f.exists()) {
      return Optional.empty();
    }
    try (InputStream in = new GZIPInputStream(new FileInputStream(f))) {
      return new SnapshotDeserializer()
          .readFrom(in, context)
          .map(SnapshotDeserializer::getSyncData);
    }
  }

  /** Returns true if {@code absolutePath} is in a project include */
  public boolean containsPath(Path absolutePath) {
    if (!workspaceRoot.isInWorkspace(absolutePath.toFile())) {
      return false;
    }
    Path workspaceRelative = workspaceRoot.path().relativize(absolutePath);
    return projectDefinition.isIncluded(workspaceRelative);
  }

  /**
   * Returns true if {@code absolutePath} is specified in a project exclude.
   *
   * <p>A path not added or excluded the project definition will return false for both {@code
   * containsPath} and {@code explicitlyExcludesPath}
   */
  public boolean explicitlyExcludesPath(Path absolutePath) {
    if (!workspaceRoot.isInWorkspace(absolutePath.toFile())) {
      return false;
    }
    Path workspaceRelative = workspaceRoot.path().relativize(absolutePath);
    return projectDefinition.isExcluded(workspaceRelative);
  }

  /**
   * Returns true if the file is in the project and has been added to the workspace since the last
   * IDE sync operation (Sync Project with BUILD files), return false otherwise, or an empty {@link
   * Optional} if this information cannot be determined.
   *
   * <p>Newly added files are determined by the following conditions:
   * <li>They are in a project source root
   * <li>They don't exist as a known source file for a target.
   * <li>They don't exist at the vcs snapshot at the most recent sync
   */
  public Optional<Boolean> projectFileAddedSinceSync(Path absolutePath) {
    if (!workspaceRoot.isInWorkspace(absolutePath.toFile())) {
      return Optional.of(false);
    }

    if (!containsPath(absolutePath)) {
      return Optional.of(false);
    }

    // Check known source files.
    Path workspaceRelative = workspaceRoot.path().relativize(absolutePath);
    if (snapshotHolder
        .getCurrent()
        .map(s -> s.graph().sourceFileToLabel(workspaceRelative).isPresent())
        .orElse(false)) {
      return Optional.of(false);
    }

    Optional<Path> snapshotPath =
        snapshotHolder
            .getCurrent()
            .flatMap(s -> s.queryData().vcsState())
            .flatMap(s -> s.workspaceSnapshotPath);

    return snapshotPath.map(path -> !path.resolve(workspaceRelative).toFile().exists());
  }

  private void writeToDisk(QuerySyncProjectSnapshot snapshot) throws IOException {
    try (AtomicFileWriter writer = AtomicFileWriter.create(snapshotFilePath)) {
      try (OutputStream zip = new GZIPOutputStream(writer.getOutputStream())) {
        final var message = new SnapshotSerializer().visit(snapshot.queryData()).toProto();
        CodedOutputStream codedOutput = CodedOutputStream.newInstance(zip, 1024 * 1024);
        message.writeTo(codedOutput);
        codedOutput.flush();
      }
      writer.onWriteComplete();
    }
  }

  private void onNewSnapshot(BlazeContext context, QuerySyncProjectSnapshot newSnapshot)
      throws BuildException {
    // update the artifact store for the new snapshot
    ProjectArtifactStore.UpdateResult result = artifactStore.update(context, newSnapshot);
    if (!result.incompleteTargets().isEmpty()) {
      final int limit = 20;
      logger.warn(
          String.format(
              "%d project deps had missing artifacts:\n  %s",
              result.incompleteTargets().size(),
              result.incompleteTargets().stream()
                  .limit(limit)
                  .map(Objects::toString)
                  .collect(Collectors.joining("\n  "))));
      if (result.incompleteTargets().size() > limit) {
        logger.warn(String.format("  (and %d more)", result.incompleteTargets().size() - limit));
      }
    }
    // update the snapshot with any missing artifacts:
    newSnapshot = newSnapshot.toBuilder().incompleteTargets(result.incompleteTargets()).build();

    snapshotHolder.setCurrent(context, newSnapshot);
    projectData = projectData.withSnapshot(newSnapshot);
    try {
      writeToDisk(newSnapshot);
    } catch (IOException e) {
      throw new BuildException("Failed to write snapshot to disk", e);
    }
  }

  public ImmutableMap<String, ByteSource> getBugreportFiles() {
    return ImmutableMap.<String, ByteSource>builder()
        .put(snapshotFilePath.getFileName().toString(), MoreFiles.asByteSource(snapshotFilePath))
        .putAll(artifactTracker.getBugreportFiles())
        .putAll(snapshotHolder.getBugreportFiles())
        .putAll(artifactStore.getBugreportFiles())
        .putAll(buildArtifactCache.getBugreportFiles())
        .build();
  }

  // TODO: b/397649793 - Remove this method when fixed.
  public boolean dependsOnAnyOf_DO_NOT_USE_BROKEN(Label target, ImmutableSet<Label> deps) {
    return snapshotHolder
      .getCurrent()
      .map(QuerySyncProjectSnapshot::graph)
      .map(graph -> graph.dependsOnAnyOf_DO_NOT_USE_BROKEN(target, deps))
      .orElse(false);
  }
}
