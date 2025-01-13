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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.Glob;
import com.google.idea.blaze.base.projectview.section.sections.TestSourceSection;
import com.google.idea.blaze.base.qsync.artifacts.GeneratedSourcesStripper;
import com.google.idea.blaze.base.qsync.artifacts.ProjectArtifactStore;
import com.google.idea.blaze.base.qsync.cache.ArtifactFetchers;
import com.google.idea.blaze.base.qsync.cc.CcProjectProtoTransform;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.base.vcs.BlazeVcsHandlerProvider;
import com.google.idea.blaze.base.vcs.BlazeVcsHandlerProvider.BlazeVcsHandler;
import com.google.idea.blaze.common.artifact.ArtifactFetcher;
import com.google.idea.blaze.common.artifact.BuildArtifactCache;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.DependenciesProjectProtoUpdater;
import com.google.idea.blaze.qsync.ProjectProtoTransform;
import com.google.idea.blaze.qsync.ProjectProtoTransform.Registry;
import com.google.idea.blaze.qsync.ProjectRefresher;
import com.google.idea.blaze.qsync.SnapshotBuilder;
import com.google.idea.blaze.qsync.SnapshotHolder;
import com.google.idea.blaze.qsync.VcsStateDiffer;
import com.google.idea.blaze.qsync.deps.ArtifactDirectories;
import com.google.idea.blaze.qsync.deps.ArtifactTracker;
import com.google.idea.blaze.qsync.deps.NewArtifactTracker;
import com.google.idea.blaze.qsync.java.JavaArtifactMetadata;
import com.google.idea.blaze.qsync.java.PackageStatementParser;
import com.google.idea.blaze.qsync.java.ParallelPackageReader;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.query.QuerySpec.QueryStrategy;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.SimpleModificationTracker;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * Loads a project, either from saved state or from a {@code .blazeproject} file, yielding a {@link
 * QuerySyncProject} instance.
 *
 * <p>This class also manages injection of external (to querysync) dependencies.
 */
public class ProjectLoaderImpl implements ProjectLoader {

  public final static BoolExperiment enableExperimentalQuery = new BoolExperiment("query.sync.experimental.query", false);
  public final static BoolExperiment runQueryInWorkspace = new BoolExperiment("query.sync.run.query.in.workspace", true);

  private final ListeningExecutorService executor;
  private final SimpleModificationTracker projectModificationTracker;
  protected final Project project;

  public ProjectLoaderImpl(ListeningExecutorService executor, Project project) {
    this.executor = executor;
    this.project = project;
    this.projectModificationTracker = new SimpleModificationTracker();
  }

  @Override
  public @Nullable QuerySyncProject loadProject(BlazeContext context) throws BuildException {
    BlazeImportSettings importSettings =
        Preconditions.checkNotNull(
            BlazeImportSettingsManager.getInstance(project).getImportSettings());

    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);
    // TODO we may need to get the WorkspacePathResolver from the VcsHandler, as the old sync
    // does inside ProjectStateSyncTask.computeWorkspacePathResolverAndProjectView
    // Things will probably work without that, but we should understand why the other
    // implementations of WorkspacePathResolver exists. Perhaps they are performance
    // optimizations?
    WorkspacePathResolver workspacePathResolver = new WorkspacePathResolverImpl(workspaceRoot);

    ProjectViewManager projectViewManager = ProjectViewManager.getInstance(project);
    ProjectViewSet projectViewSet =
        projectViewManager.reloadProjectView(context, workspacePathResolver);
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, importSettings.getBuildSystem())
            .add(projectViewSet)
            .build();
    BuildSystem buildSystem =
        BuildSystemProvider.getBuildSystemProvider(importSettings.getBuildSystem())
            .getBuildSystem();
    WorkspaceLanguageSettings workspaceLanguageSettings =
        LanguageSupport.createWorkspaceLanguageSettings(projectViewSet);

    ImmutableSet<String> testSourceGlobs =
        projectViewSet.listItems(TestSourceSection.KEY).stream()
            .map(Glob::toString)
            .collect(ImmutableSet.toImmutableSet());

    ProjectDefinition latestProjectDef =
        ProjectDefinition.builder()
            .setProjectIncludes(importRoots.rootPaths())
            .setProjectExcludes(importRoots.excludePaths())
            .setLanguageClasses(
                LanguageClasses.toQuerySync(workspaceLanguageSettings.getActiveLanguages()))
            .setTestSources(testSourceGlobs)
            .setSystemExcludes(importRoots.systemExcludes())
            .build();

    Path snapshotFilePath = getSnapshotFilePath(importSettings);

    ImmutableSet<String> handledRules = getHandledRuleKinds();
    Optional<BlazeVcsHandler> vcsHandler =
        Optional.ofNullable(BlazeVcsHandlerProvider.vcsHandlerForProject(project));
    RenderJarBuilder renderJarBuilder = createRenderJarBuilder(workspaceRoot, buildSystem);
    AppInspectorBuilder appInspectorBuilder = createAppInspectorBuilder(buildSystem);

    Path ideProjectBasePath = Paths.get(checkNotNull(project.getBasePath()));
    ProjectPath.Resolver projectPathResolver =
        ProjectPath.Resolver.create(workspaceRoot.path(), ideProjectBasePath);

    ProjectProtoTransform.Registry projectTransformRegistry = new Registry();
    SnapshotHolder graph = new SnapshotHolder();
    graph.addListener((c, i) -> projectModificationTracker.incModificationCount());
    Path buildCachePath = getBuildCachePath(project);
    BuildArtifactCache artifactCache =
        BuildArtifactCache.create(
          buildCachePath,
            createArtifactFetcher(),
            executor,
            QuerySyncManager.getInstance(project).cacheCleanRequest());

    DependencyBuilder dependencyBuilder =
      createDependencyBuilder(
        workspaceRoot, latestProjectDef, buildSystem, vcsHandler, artifactCache, handledRules);

    ArtifactTracker<BlazeContext> artifactTracker;
    RenderJarArtifactTracker renderJarArtifactTracker;
    AppInspectorArtifactTracker appInspectorArtifactTracker;
    NewArtifactTracker<BlazeContext> tracker =
        new NewArtifactTracker<>(
            BlazeDataStorage.getProjectDataDir(importSettings).toPath(),
            artifactCache,
            // don't pass the composed transform directly as it's not fully constructed yet:
            t -> projectTransformRegistry.getComposedTransform().getRequiredArtifactMetadata(t),
            new JavaArtifactMetadata.Factory(),
            executor);
    projectTransformRegistry.add(
        new DependenciesProjectProtoUpdater(
            latestProjectDef,
            projectPathResolver,
            QuerySync.ATTACH_DEP_SRCJARS::getValue));
    projectTransformRegistry.add(new CcProjectProtoTransform());

    artifactTracker = tracker;
    renderJarArtifactTracker = new RenderJarArtifactTrackerImpl();
    appInspectorArtifactTracker =
      new AppInspectorArtifactTrackerImpl(workspaceRoot.path(), artifactCache,
                                          ideProjectBasePath.resolve(ArtifactDirectories.INSPECTORS.relativePath()));
    RenderJarTracker renderJarTracker =
        new RenderJarTrackerImpl(graph, renderJarBuilder, renderJarArtifactTracker);
    AppInspectorTracker appInspectorTracker =
        new AppInspectorTrackerImpl(appInspectorBuilder, appInspectorArtifactTracker);
    ProjectArtifactStore artifactStore =
        new ProjectArtifactStore(
            ideProjectBasePath,
            workspaceRoot.path(),
            artifactCache,
            new FileRefresher(project),
            new GeneratedSourcesStripper(project));
    DependencyTracker dependencyTracker =
        new DependencyTrackerImpl(graph, dependencyBuilder, artifactTracker);
    ProjectRefresher projectRefresher =
        new ProjectRefresher(
            vcsHandler.map(it -> (VcsStateDiffer)it::diffVcsState).orElse(VcsStateDiffer.NONE),
            workspaceRoot.path(),
            enableExperimentalQuery.getValue() ? QueryStrategy.FILTERING_TO_KNOWN_AND_USED_TARGETS : QueryStrategy.PLAIN,
            graph::getCurrent,
            runQueryInWorkspace::getValue);
    SnapshotBuilder snapshotBuilder =
        new SnapshotBuilder(
            executor,
            createWorkspaceRelativePackageReader(),
            workspaceRoot.path(),
            handledRules,
            QuerySync.USE_NEW_RES_DIR_LOGIC::getValue,
            () -> !QuerySync.EXTRACT_RES_PACKAGES_AT_BUILD_TIME.getValue());
    QueryRunner queryRunner = createQueryRunner(buildSystem);
    ProjectQuerier projectQuerier =
        createProjectQuerier(
            projectRefresher,
            queryRunner,
            vcsHandler,
            new BazelVersionHandler(buildSystem, buildSystem.getBuildInvoker(project, context)));
    QuerySyncSourceToTargetMap sourceToTargetMap =
        new QuerySyncSourceToTargetMap(graph, workspaceRoot.path());

    QuerySyncProject querySyncProject =
        new QuerySyncProject(
            project,
            snapshotFilePath,
            graph,
            importSettings,
            workspaceRoot,
            artifactTracker,
            artifactCache,
            artifactStore,
            renderJarArtifactTracker,
            appInspectorArtifactTracker,
            dependencyTracker,
            renderJarTracker,
            appInspectorTracker,
            projectQuerier,
            snapshotBuilder,
            latestProjectDef,
            projectViewSet,
            workspacePathResolver,
            projectPathResolver,
            workspaceLanguageSettings,
            sourceToTargetMap,
            projectViewManager,
            buildSystem,
            projectTransformRegistry);
    QuerySyncProjectListenerProvider.registerListenersFor(querySyncProject);
    projectTransformRegistry.addAll(ProjectProtoTransformProvider.getAll(latestProjectDef));

    return querySyncProject;
  }

  public static Path getBuildCachePath(Project project) {
    return Paths.get(checkNotNull(project.getBasePath())).resolve(".buildcache");
  }

  private ParallelPackageReader createWorkspaceRelativePackageReader() {
    return new ParallelPackageReader(executor, new PackageStatementParser());
  }

  private ProjectQuerierImpl createProjectQuerier(
      ProjectRefresher projectRefresher,
      QueryRunner queryRunner,
      Optional<BlazeVcsHandler> vcsHandler,
      BazelVersionHandler bazelVersionProvider) {
    return new ProjectQuerierImpl(queryRunner, projectRefresher, vcsHandler, bazelVersionProvider);
  }

  protected QueryRunner createQueryRunner(BuildSystem buildSystem) {
    return buildSystem.createQueryRunner(project);
  }

  protected DependencyBuilder createDependencyBuilder(
      WorkspaceRoot workspaceRoot,
      ProjectDefinition projectDefinition,
      BuildSystem buildSystem,
      Optional<BlazeVcsHandler> vcsHandler,
      BuildArtifactCache buildArtifactCache,
      ImmutableSet<String> handledRuleKinds) {
    return new BazelDependencyBuilder(
      project, buildSystem, projectDefinition, workspaceRoot, vcsHandler, buildArtifactCache, handledRuleKinds);
  }

  protected RenderJarBuilder createRenderJarBuilder(
      WorkspaceRoot workspaceRoot, BuildSystem buildSystem) {
    return new BazelRenderJarBuilder(project, buildSystem, workspaceRoot);
  }

  protected AppInspectorBuilder createAppInspectorBuilder(BuildSystem buildSystem) {
    return new BazelAppInspectorBuilder(project, buildSystem);
  }

  private Path getSnapshotFilePath(BlazeImportSettings importSettings) {
    return BlazeDataStorage.getProjectDataDir(importSettings).toPath().resolve("qsyncdata.gz");
  }

  private ArtifactFetcher<OutputArtifact> createArtifactFetcher() {
    return new DynamicallyDispatchingArtifactFetcher(
        ImmutableList.copyOf(ArtifactFetchers.EP_NAME.getExtensions()));
  }

  /**
   * Returns an {@link ImmutableSet} of rule kinds that query sync or plugin know how to resolve
   * symbols for without building. The rules query sync always builds even if they are part of the
   * project are in {@link com.google.idea.blaze.qsync.BlazeQueryParser#ALWAYS_BUILD_RULE_KINDS}
   */
  private ImmutableSet<String> getHandledRuleKinds() {
    ImmutableSet.Builder<String> defaultRules = ImmutableSet.builder();
    for (HandledRulesProvider ep : HandledRulesProvider.EP_NAME.getExtensionList()) {
      defaultRules.addAll(ep.handledRuleKinds(project));
    }
    return defaultRules.build();
  }

  @Override
  public ModificationTracker getProjectModificationTracker() {
    return projectModificationTracker;
  }
}
