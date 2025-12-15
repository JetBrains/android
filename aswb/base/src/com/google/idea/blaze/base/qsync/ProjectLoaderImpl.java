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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.idea.blaze.qsync.project.QuerySyncProjectDirectory.BAZEL_ARTIFACTS;
import static com.google.idea.blaze.qsync.project.QuerySyncProjectDirectory.BAZEL_SYSTEM;
import static com.google.idea.blaze.qsync.project.QuerySyncProjectDirectory.EXTERNAL_REPOSITORIES;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.Glob;
import com.google.idea.blaze.base.projectview.section.sections.AutomaticallyDeriveTargetsSection;
import com.google.idea.blaze.base.projectview.section.sections.TargetSection;
import com.google.idea.blaze.base.projectview.section.sections.TestSourceSection;
import com.google.idea.blaze.base.projectview.section.sections.WorkspaceTypeSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.base.vcs.BlazeVcsHandlerProvider;
import com.google.idea.blaze.base.vcs.BlazeVcsHandlerProvider.BlazeVcsHandler;
import com.google.idea.blaze.common.TargetPattern;
import com.google.idea.blaze.common.artifact.BuildArtifactCache;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.DependenciesProjectProtoUpdater;
import com.google.idea.blaze.qsync.ProjectBuilder;
import com.google.idea.blaze.qsync.ProjectRefresher;
import com.google.idea.blaze.qsync.VcsStateDiffer;
import com.google.idea.blaze.qsync.artifacts.ArtifactMetadata;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.deps.ArtifactDirectories;
import com.google.idea.blaze.qsync.deps.ArtifactTracker;
import com.google.idea.blaze.qsync.deps.NewArtifactTracker;
import com.google.idea.blaze.qsync.deps.TargetBuildInfo;
import com.google.idea.blaze.qsync.java.JavaArtifactMetadata;
import com.google.idea.blaze.qsync.java.PackageReader;
import com.google.idea.blaze.qsync.java.PackageStatementParser;
import com.google.idea.blaze.qsync.java.ParallelPackageReader;
import com.google.idea.blaze.qsync.project.BuildGraphData;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.ProjectDirectoryConfigurator;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdateOperation;
import com.google.idea.blaze.qsync.query.QuerySpec.QueryStrategy;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.idea.common.experiments.EnumExperiment;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loads a project, either from saved state or from a {@code .blazeproject} file, yielding a {@link
 * QuerySyncProject} instance.
 *
 * <p>This class also manages injection of external (to querysync) dependencies.
 */
public class ProjectLoaderImpl implements ProjectLoader {

  public static final EnumExperiment<QueryStrategy> enableExperimentalQuery =
      new EnumExperiment<>("query.sync.experimental.query", QueryStrategy.PLAIN_WITH_SAFE_FILTERS);
  public static final BoolExperiment runQueryInWorkspace =
      new BoolExperiment("query.sync.run.query.in.workspace", true);

  protected final ListeningExecutorService executor;

  protected final Project project;

  /** A loaded {@link QuerySyncProject} with all the services it depends on. */
  public record LoadProjectResult(QuerySyncProject result, QuerySyncProjectDeps deps) {}

  /** Services {@link QuerySyncProject} depends on. */
  public record QuerySyncProjectDeps(
      BlazeImportSettings importSettings,
      WorkspaceRoot workspaceRoot,
      WorkspacePathResolver workspacePathResolver,
      QuerySyncLanguageSettings languageSettings,
      BuildSystem buildSystem,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      ProjectDefinition latestProjectDef,
      ProjectPath.Resolver projectPathResolver,
      Collection<? extends ProjectProtoUpdateOperation> projectProtoUpdateOperations,
      SnapshotHolder snapshotHolder,
      BuildArtifactCache artifactCache,
      ArtifactTracker<BlazeContext> artifactTracker,
      RenderJarArtifactTracker renderJarArtifactTracker,
      AppInspectorArtifactTracker appInspectorArtifactTracker,
      AppInspectorTracker appInspectorTracker,
      DependencyBuilder dependencyBuilder,
      DependencyTracker dependencyTracker,
      ProjectBuilder projectBuilder,
      ProjectQuerier projectQuerier,
      QuerySyncSourceToTargetMap sourceToTargetMap,
      ImmutableSet<String> handledRuleKinds,
      BuildGraphData.ProtoRules protoRules) {}

  public ProjectLoaderImpl(Project project) {
    this(
        MoreExecutors.listeningDecorator(
            AppExecutorUtil.createBoundedApplicationPoolExecutor("QuerySync", 128)),
        project);
  }

  protected ProjectLoaderImpl(ListeningExecutorService executor, Project project) {
    this.executor = executor;
    this.project = project;
  }

  @Override
  public QuerySyncProject loadProject() throws BuildException {
    LoadProjectResult loadProjectResult = doLoadProject();
    return loadProjectResult.result();
  }

  public LoadProjectResult doLoadProject() throws BuildException {
    QuerySyncProjectDeps deps = instantiateDeps();
    final var querySyncProject = loadProject(deps);
    return new LoadProjectResult(querySyncProject, deps);
  }

  private QuerySyncProject loadProject(QuerySyncProjectDeps result) {
    QuerySyncProject querySyncProject =
        new QuerySyncProject(
            project,
            result.snapshotHolder(),
            result.importSettings(),
            result.workspaceRoot(),
            result.artifactTracker(),
            result.artifactCache(),
            result.dependencyTracker(),
            result.appInspectorTracker(),
            result.projectQuerier(),
            result.projectBuilder(),
            result.latestProjectDef(),
            result.languageSettings(),
            result.workspacePathResolver(),
            result.projectPathResolver(),
            result.workspaceLanguageSettings(),
            result.sourceToTargetMap(),
            result.buildSystem(),
            ImmutableList.<ProjectProtoUpdateOperation>builder()
                .addAll(result.projectProtoUpdateOperations())
                .addAll(ProjectProtoTransformProvider.getAll(result.latestProjectDef()))
                .build(),
            result.handledRuleKinds(),
            result.protoRules());

    return querySyncProject;
  }

  @Override
  public ProjectToLoadDefinition loadProjectDefinition(ProjectViewSet projectViewSet) {
    BlazeImportSettings importSettings =
        Preconditions.checkNotNull(
            BlazeImportSettingsManager.getInstance(project).getImportSettings());
    WorkspaceRoot workspaceRoot =
        WorkspaceRoot.fromProject(project); // TODO: solodkyy - read from the project view.
    // TODO we may need to get the WorkspacePathResolver from the VcsHandler, as the old sync
    // does inside ProjectStateSyncTask.computeWorkspacePathResolverAndProjectView
    // Things will probably work without that, but we should understand why the other
    // implementations of WorkspacePathResolver exists. Perhaps they are performance
    // optimizations?
    ProjectDefinition projectDefinition =
        createProjectDefinition(workspaceRoot, importSettings.getBuildSystem(), projectViewSet);
    WorkspaceLanguageSettings workspaceLanguageSettings =
        LanguageSupport.createWorkspaceLanguageSettings(projectViewSet);
    QuerySyncLanguageSettings languageSettings =
        QuerySyncLanguageSettings.from(projectViewSet, workspaceLanguageSettings);
    // TODO: solodkyy - read from the project view.
    BuildSystemProvider buildSystemProvider =
        BuildSystemProvider.getBuildSystemProvider(importSettings.getBuildSystem());
    BuildSystem buildSystem = buildSystemProvider.getBuildSystem();
    ProjectDirectoryConfigurator projectDirectoryConfigurator =
        buildSystemProvider.getProjectDirectoryConfigurator(project);

    return new ProjectToLoadDefinition(
        workspaceRoot,
        projectDirectoryConfigurator,
        buildSystem,
        projectDefinition,
        workspaceLanguageSettings,
        languageSettings);
  }

  private QuerySyncProjectDeps instantiateDeps() {
    BlazeImportSettings importSettings =
        Preconditions.checkNotNull(
            BlazeImportSettingsManager.getInstance(project).getImportSettings());
    final var querySyncUserPreferences = QuerySyncUserPreferencesProvider.getInstance(project).getUserPreferences();
    final var projectToLoad =
        loadProjectDefinition(BlazeImportSettingsManager.getInstance(project).getProjectViewSet());
    final var workspaceRoot = projectToLoad.workspaceRoot();
    final var latestProjectDef = projectToLoad.definition();
    final var buildSystem = projectToLoad.buildSystem();
    final var projectDirectoryConfigurator = projectToLoad.projectDirectoryConfigurator();

    WorkspaceLanguageSettings workspaceLanguageSettings = projectToLoad.workspaceLanguageSettings();
    QuerySyncLanguageSettings languageSettings = projectToLoad.languageSettings();

    ImmutableSet<String> handledRules = getHandledRuleKinds();
    Optional<BlazeVcsHandler> vcsHandler =
        Optional.ofNullable(BlazeVcsHandlerProvider.vcsHandlerForProject(project));
    AppInspectorBuilder appInspectorBuilder = createAppInspectorBuilder(buildSystem);

    Path ideProjectBasePath = Paths.get(checkNotNull(project.getBasePath()));
    ProjectPath.Resolver projectPathResolver =
        ProjectPath.Resolver.create(
            workspaceRoot.path(),
            ideProjectBasePath,
            ideProjectBasePath.resolve(EXTERNAL_REPOSITORIES.getDirectoryName()));

    final var projectTransformRegistry = new ArrayList<ProjectProtoUpdateOperation>();
    SnapshotHolder snapshotHolder = QuerySyncManager.getInstance(project).getSnapshotHolder();
    BuildArtifactCache artifactCache = project.getService(BuildArtifactCache.class);

    DependencyBuilder dependencyBuilder =
        createDependencyBuilder(
            workspaceRoot,
            latestProjectDef,
            snapshotHolder,
            buildSystem,
            vcsHandler,
            artifactCache,
            handledRules);

    // This directory is later used without being configured.
    projectDirectoryConfigurator.configureDirectory(BAZEL_ARTIFACTS);

    ArtifactTracker<BlazeContext> artifactTracker;
    RenderJarArtifactTracker renderJarArtifactTracker;
    AppInspectorArtifactTracker appInspectorArtifactTracker;
    projectTransformRegistry.add(
        new DependenciesProjectProtoUpdater(
            latestProjectDef,
            projectPathResolver,
            buildSystem.getEmptyJarDigests(),
            QuerySync.ATTACH_DEP_SRCJARS::getValue));
    NewArtifactTracker<BlazeContext> tracker =
        new NewArtifactTracker<>(
            workspaceRoot.directory().toPath(),
            projectDirectoryConfigurator.configureDirectory(BAZEL_SYSTEM),
            artifactCache,
            // don't pass the composed transform directly as it's not fully constructed yet:
            t -> getRequiredArtifactMetadata(projectTransformRegistry, t),
            new JavaArtifactMetadata.Factory(),
            executor);

    artifactTracker = tracker;
    renderJarArtifactTracker = new RenderJarArtifactTrackerImpl();
    appInspectorArtifactTracker =
        new AppInspectorArtifactTrackerImpl(
            artifactCache,
            ideProjectBasePath.resolve(ArtifactDirectories.INSPECTORS.relativePath()));
    AppInspectorTracker appInspectorTracker =
        new AppInspectorTrackerImpl(appInspectorBuilder, appInspectorArtifactTracker);
    DependencyTracker dependencyTracker =
        new DependencyTrackerImpl(snapshotHolder, dependencyBuilder, artifactTracker, querySyncUserPreferences);
    ProjectRefresher projectRefresher =
        new ProjectRefresher(
            vcsHandler.map(it -> (VcsStateDiffer) it::diffVcsState).orElse(VcsStateDiffer.NONE),
            workspaceRoot.path(),
            enableExperimentalQuery.getValue(),
            snapshotHolder::getCurrent);
    ProjectBuilder snapshotBuilder =
        new ProjectBuilder(
            createPackageReader(), createParallelPackageReader(), workspaceRoot.path());
    QueryRunner queryRunner = createQueryRunner(buildSystem);
    ProjectQuerier projectQuerier =
        createProjectQuerier(
            projectRefresher,
            queryRunner,
            vcsHandler,
            new BazelVersionHandler(buildSystem, buildSystem.getBuildInvoker(project)));
    QuerySyncSourceToTargetMap sourceToTargetMap =
        new QuerySyncSourceToTargetMap(snapshotHolder, workspaceRoot.path());
    return new QuerySyncProjectDeps(
        importSettings,
        workspaceRoot,
        new WorkspacePathResolverImpl(workspaceRoot),
        languageSettings,
        buildSystem,
        workspaceLanguageSettings,
        latestProjectDef,
        projectPathResolver,
        projectTransformRegistry,
        snapshotHolder,
        artifactCache,
        artifactTracker,
        renderJarArtifactTracker,
        appInspectorArtifactTracker,
        appInspectorTracker,
        dependencyBuilder,
        dependencyTracker,
        snapshotBuilder,
        projectQuerier,
        sourceToTargetMap,
        handledRules,
        buildSystem.getProtoRules());
  }

  private static Map<BuildArtifact, ? extends Collection<? extends ArtifactMetadata.Extractor<?>>>
      getRequiredArtifactMetadata(
          Collection<ProjectProtoUpdateOperation> projectTransformRegistry,
          TargetBuildInfo targetInfo) {
    final var result = new HashMap<BuildArtifact, Set<ArtifactMetadata.Extractor<?>>>();
    for (ProjectProtoUpdateOperation op : projectTransformRegistry) {
      for (var entry : op.getRequiredArtifacts(targetInfo).entrySet()) {
        result.computeIfAbsent(entry.getKey(), k -> new HashSet<>()).addAll(entry.getValue());
      }
    }
    return result;
  }

  private PackageReader createPackageReader() {
    return new PackageStatementParser();
  }

  private PackageReader.ParallelReader createParallelPackageReader() {
    return new ParallelPackageReader();
  }

  private ProjectQuerierImpl createProjectQuerier(
      ProjectRefresher projectRefresher,
      QueryRunner queryRunner,
      Optional<BlazeVcsHandler> vcsHandler,
      BazelVersionHandler bazelVersionProvider) {
    return new ProjectQuerierImpl(queryRunner, projectRefresher, vcsHandler, bazelVersionProvider);
  }

  protected QueryRunner createQueryRunner(BuildSystem buildSystem) {
    return new BazelQueryRunner(project, buildSystem);
  }

  protected DependencyBuilder createDependencyBuilder(
      WorkspaceRoot workspaceRoot,
      ProjectDefinition projectDefinition,
      SnapshotHolder snapshotHolder,
      BuildSystem buildSystem,
      Optional<BlazeVcsHandler> vcsHandler,
      BuildArtifactCache buildArtifactCache,
      ImmutableSet<String> handledRuleKinds) {
    return new BazelDependencyBuilder(
        project,
        buildSystem,
        projectDefinition,
        snapshotHolder,
        workspaceRoot,
        vcsHandler,
        buildArtifactCache,
        handledRuleKinds);
  }

  protected AppInspectorBuilder createAppInspectorBuilder(BuildSystem buildSystem) {
    return new BazelAppInspectorBuilder(project, buildSystem);
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

  private static ProjectDefinition createProjectDefinition(
      WorkspaceRoot workspaceRoot, BuildSystemName buildSystem, ProjectViewSet projectViewSet) {
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, buildSystem).add(projectViewSet).build();
    WorkspaceLanguageSettings workspaceLanguageSettings =
        LanguageSupport.createWorkspaceLanguageSettings(projectViewSet);
    ImmutableSet<String> testSourceGlobs =
        projectViewSet.listItems(TestSourceSection.KEY).stream()
            .map(Glob::toString)
            .collect(ImmutableSet.toImmutableSet());
    // derive_targets_from_directories: true in query sync basically means
    final var deriveTargetsFromDirectories =
        projectViewSet.getScalarValue(AutomaticallyDeriveTargetsSection.KEY).orElse(false);
    final var isAndroidWorkspace =
        projectViewSet
            .getScalarValue(WorkspaceTypeSection.KEY)
            .orElse(WorkspaceType.ANDROID)
            .equals(WorkspaceType.ANDROID);
    final var targetPatterns =
        projectViewSet.listItems(TargetSection.KEY).stream()
            .map(it -> TargetPattern.parse(it.toString()))
            .collect(toImmutableList());
    return new ProjectDefinition(
        importRoots.rootPaths(),
        importRoots.excludePaths(),
        deriveTargetsFromDirectories,
        targetPatterns,
        isAndroidWorkspace,
        LanguageClasses.toQuerySync(workspaceLanguageSettings.getActiveLanguages()),
        testSourceGlobs,
        ImmutableSet.<Path>builder()
            .addAll(importRoots.systemExcludes())
            .add(Path.of(BazelDependencyBuilder.INVOCATION_FILES_DIR))
            .build());
  }
}
