/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Arrays.stream;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.util.PathString;
import com.android.manifmerger.ManifestSystemProperty;
import com.android.projectmodel.ExternalAndroidLibrary;
import com.android.projectmodel.ExternalLibraryImpl;
import com.android.projectmodel.SelectiveResourceFolder;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.CapabilityNotSupported;
import com.android.tools.idea.projectsystem.CapabilityStatus;
import com.android.tools.idea.projectsystem.CapabilitySupported;
import com.android.tools.idea.projectsystem.ClassFileFinder;
import com.android.tools.idea.projectsystem.DependencyManagementException;
import com.android.tools.idea.projectsystem.DependencyScopeType;
import com.android.tools.idea.projectsystem.DependencyType;
import com.android.tools.idea.projectsystem.ManifestOverrides;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.android.tools.idea.projectsystem.SampleDataDirectoryProvider;
import com.android.tools.idea.projectsystem.ScopeType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.idea.blaze.android.compose.ComposeStatusProvider;
import com.google.idea.blaze.android.libraries.UnpackedAars;
import com.google.idea.blaze.android.npw.project.BlazeAndroidModuleTemplate;
import com.google.idea.blaze.android.sync.model.AarLibrary;
import com.google.idea.blaze.android.sync.model.AndroidResourceModule;
import com.google.idea.blaze.android.sync.model.AndroidResourceModuleRegistry;
import com.google.idea.blaze.android.sync.model.BlazeAndroidSyncData;
import com.google.idea.blaze.base.command.buildresult.OutputArtifactResolver;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.Dependency;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.lang.buildfile.references.BuildReferenceManager;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.base.qsync.QuerySyncProject;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.SyncCache;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.libraries.BlazeLibraryCollector;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.targetmaps.ReverseDependencyMap;
import com.google.idea.blaze.base.targetmaps.TransitiveDependencyMap;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.google.idea.blaze.qsync.SnapshotHolder;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import kotlin.Triple;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** Blaze implementation of {@link AndroidModuleSystem}. */
@SuppressWarnings("NullableProblems")
abstract class BlazeModuleSystemBase implements AndroidModuleSystem {

  /**
   * Experiment to toggle returning a simplified view of resource module dependents to work around
   * b/193680790. See {@link #getDirectResourceModuleDependents} for details.
   */
  @VisibleForTesting
  public static final BoolExperiment returnSimpleDirectResourceDependents =
      new BoolExperiment("aswb.return.simple.direct.resource.dependents", true);

  protected static final Logger logger = Logger.getInstance(BazelModuleSystem.class);
  protected Module module;
  protected final Project project;
  private final ProjectPath.Resolver pathResolver;
  SampleDataDirectoryProvider sampleDataDirectoryProvider;
  RenderJarClassFileFinder classFileFinder;
  final boolean isWorkspaceModule;

  BlazeModuleSystemBase(Module module) {
    this.module = module;
    this.project = module.getProject();
    this.pathResolver =
        ProjectPath.Resolver.create(
            WorkspaceRoot.fromProject(project).path(),
            Path.of(
                BlazeImportSettingsManager.getInstance(project)
                    .getImportSettings()
                    .getProjectDataDirectory()));
    classFileFinder = new RenderJarClassFileFinder(module);
    sampleDataDirectoryProvider = new BlazeSampleDataDirectoryProvider(module);
    isWorkspaceModule = module.getName().equals(BlazeDataStorage.WORKSPACE_MODULE_NAME);
  }

  @Override
  public Module getModule() {
    return module;
  }

  @Override
  public ClassFileFinder getModuleClassFileFinder() {
    return classFileFinder;
  }

  @Override
  public ClassFileFinder getClassFileFinderForSourceFile(VirtualFile sourceFile) {
    return classFileFinder;
  }

  @Override
  @Nullable
  public PathString getOrCreateSampleDataDirectory() throws IOException {
    return sampleDataDirectoryProvider.getOrCreateSampleDataDirectory();
  }

  @Override
  @Nullable
  public PathString getSampleDataDirectory() {
    return sampleDataDirectoryProvider.getSampleDataDirectory();
  }

  @Override
  public CapabilityStatus canGeneratePngFromVectorGraphics() {
    return new CapabilitySupported();
  }

  @Override
  public List<NamedModuleTemplate> getModuleTemplates(@Nullable VirtualFile targetDirectory) {
    return BlazeAndroidModuleTemplate.getTemplates(module, targetDirectory);
  }

  @Override
  public CapabilityStatus canRegisterDependency(DependencyType type) {
    return new CapabilityNotSupported();
  }

  @Override
  public void registerDependency(GradleCoordinate coordinate) {
    registerDependency(coordinate, DependencyType.IMPLEMENTATION);
  }

  @Override
  public void registerDependency(GradleCoordinate coordinate, DependencyType type) {
    if (type != DependencyType.IMPLEMENTATION) {
      throw new UnsupportedOperationException("Unsupported dependency type in Blaze: " + type);
    }
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return;
    }
    AndroidResourceModuleRegistry registry = AndroidResourceModuleRegistry.getInstance(project);
    TargetIdeInfo targetIdeInfo =
        blazeProjectData.getTargetMap().get(registry.getTargetKey(module));
    if (targetIdeInfo == null || targetIdeInfo.getBuildFile() == null) {
      return;
    }

    // TODO: automagically edit deps instead of just opening the BUILD file?
    // Need to translate Gradle coordinates into blaze targets.
    // Will probably need to hardcode for each dependency.
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    PsiElement buildTargetPsi =
        BuildReferenceManager.getInstance(project).resolveLabel(targetIdeInfo.getKey().getLabel());
    if (buildTargetPsi != null) {
      // If we can find a PSI for the target,
      // then we can jump straight to the target in the build file.
      fileEditorManager.openTextEditor(
          new OpenFileDescriptor(
              project,
              buildTargetPsi.getContainingFile().getVirtualFile(),
              buildTargetPsi.getTextOffset()),
          true);
    } else {
      // If not, just the build file is good enough.
      ArtifactLocation buildFile = targetIdeInfo.getBuildFile();
      File buildIoFile =
          Preconditions.checkNotNull(
              OutputArtifactResolver.resolve(
                  project, blazeProjectData.getArtifactLocationDecoder(), buildFile),
              "Fail to find file %s",
              buildFile.getRelativePath());
      VirtualFile buildVirtualFile =
          VfsUtils.resolveVirtualFile(buildIoFile, /* refreshIfNeeded= */ true);
      if (buildVirtualFile != null) {
        fileEditorManager.openFile(buildVirtualFile, true);
      }
    }
  }

  @Nullable
  @Override
  public GradleCoordinate getRegisteredDependency(GradleCoordinate coordinate) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(module.getProject()).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }

    TargetKey resourceModuleKey =
        AndroidResourceModuleRegistry.getInstance(module.getProject()).getTargetKey(module);
    if (resourceModuleKey == null) {
      // TODO: decide what constitutes a registered dependency for the .workspace module
      return null;
    }

    TargetIdeInfo resourceModuleTarget = projectData.getTargetMap().get(resourceModuleKey);
    if (resourceModuleTarget == null) {
      return null;
    }

    ImmutableSet<TargetKey> firstLevelDeps =
        resourceModuleTarget.getDependencies().stream()
            .map(Dependency::getTargetKey)
            .collect(toImmutableSet());

    return locateArtifactsFor(coordinate).anyMatch(firstLevelDeps::contains) ? coordinate : null;
  }

  @Nullable
  private Label getResolvedLabel(GradleCoordinate coordinate) {
    return MavenArtifactLocator.forBuildSystem(Blaze.getBuildSystemName(module.getProject()))
        .stream()
        .map(locator -> locator.labelFor(coordinate))
        .map(l -> Label.of(l.toString()))
        .findFirst()
        .orElse(null);
  }

  @Nullable
  private TargetKey getResolvedTarget(GradleCoordinate coordinate) {
    if (Blaze.getProjectType(project) == ProjectType.QUERY_SYNC) {
      // TODO (b/262289199): While there is a way of mapping a gradle coordinate to a target,
      //  that is a very tricky practice that while it could be supported with Query Sync, we
      //  should try to avoid it.
      return null;
    }
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(module.getProject()).getBlazeProjectData();

    if (projectData == null) {
      return null;
    }

    TargetKey resourceModuleKey =
        AndroidResourceModuleRegistry.getInstance(module.getProject()).getTargetKey(module);
    TransitiveDependencyMap transitiveDependencyMap =
        TransitiveDependencyMap.getInstance(module.getProject());

    return locateArtifactsFor(coordinate)
        .filter(
            artifactKey ->
                resourceModuleKey == null
                    // If this isn't a resource module, then it must be the .workspace module,
                    // which
                    // transitively depends on everything in the project. So we can just check
                    // to see
                    // if the artifact is included in the project by checking the keys of the
                    // target map.
                    ? projectData.getTargetMap().contains(artifactKey)
                    // Otherwise, we actually need to search the transitive dependencies of the
                    // resource module.
                    : transitiveDependencyMap.hasTransitiveDependency(
                        resourceModuleKey, artifactKey))
        .findFirst()
        .orElse(null);
  }

  @Nullable
  @Override
  public GradleCoordinate getResolvedDependency(GradleCoordinate coordinate) {
    return getResolvedDependency(coordinate, DependencyScopeType.MAIN);
  }

  @Override
  @Nullable
  public GradleCoordinate getResolvedDependency(
      GradleCoordinate gradleCoordinate, DependencyScopeType dependencyScopeType)
      throws DependencyManagementException {
    TargetKey target = getResolvedTarget(gradleCoordinate);
    return target != null ? gradleCoordinate : null;
  }

  private Stream<TargetKey> locateArtifactsFor(GradleCoordinate coordinate) {
    // External dependencies can be imported into the project via many routes (e.g. maven_jar,
    // local_repository, custom repo paths, etc). Within the project these dependencies are all
    // referenced by their TargetKey. Here we use a locator to convert coordinates to TargetKey
    // labels in order to find them.
    return MavenArtifactLocator.forBuildSystem(Blaze.getBuildSystemName(module.getProject()))
        .stream()
        .map(locator -> locator.labelFor(coordinate))
        .filter(Objects::nonNull)
        .map(TargetKey::forPlainTarget);
  }

  /**
   * Currently, the ordering of the returned list of modules is meaningless for the Blaze
   * implementation of this API. This may break legacy callers of {@link
   * org.jetbrains.android.util.AndroidUtils#getAndroidResourceDependencies(Module)}, who may be
   * assuming that the facets are returned in overlay order.
   */
  @Override
  public List<Module> getResourceModuleDependencies() {
    if (Blaze.getProjectType(project) == ProjectType.QUERY_SYNC) {
      return ImmutableList.of();
    }
    AndroidResourceModuleRegistry resourceModuleRegistry =
        AndroidResourceModuleRegistry.getInstance(project);

    if (isWorkspaceModule) {
      // The workspace module depends on every resource module.
      return stream(ModuleManager.getInstance(project).getModules())
          .filter(module -> resourceModuleRegistry.get(module) != null)
          .collect(toImmutableList());
    }
    AndroidResourceModule resourceModule = resourceModuleRegistry.get(module);
    if (resourceModule == null) {
      return ImmutableList.of();
    }

    return resourceModule.transitiveResourceDependencies.stream()
        .map(resourceModuleRegistry::getModuleContainingResourcesOf)
        .filter(Objects::nonNull)
        .collect(toImmutableList());
  }

  @Override
  public List<Module> getDirectResourceModuleDependents() {
    if (returnSimpleDirectResourceDependents.getValue()) {
      // Returns a simplified view of resource dependencies to work around b/193680790. AS2020.3
      // assumes an acyclic graph when iterating over dependents of a module, but ASwB has cyclic
      // module dependents. This implementation returns the workspace module as the only dependent
      // of resource modules and no module is exposed as a dependent of the workspace module,
      // effectively creating a star graph with workspace module in the center.
      // #as203: This can be removed when as203 is paved.
      if (isWorkspaceModule) {
        return ImmutableList.of();
      }
      Module workspaceModule =
          ModuleManager.getInstance(project)
              .findModuleByName(BlazeDataStorage.WORKSPACE_MODULE_NAME);
      return workspaceModule == null ? ImmutableList.of() : ImmutableList.of(workspaceModule);
    }

    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(module.getProject()).getBlazeProjectData();
    if (projectData == null) {
      return ImmutableList.of();
    }

    AndroidResourceModuleRegistry resourceModuleRegistry =
        AndroidResourceModuleRegistry.getInstance(module.getProject());
    TargetKey resourceModuleKey = resourceModuleRegistry.getTargetKey(module);
    if (resourceModuleKey == null) {
      return ImmutableList.of();
    }

    return ReverseDependencyMap.get(module.getProject()).get(resourceModuleKey).stream()
        .map(projectData.getTargetMap()::get)
        .filter(Objects::nonNull)
        .map(TargetIdeInfo::getKey)
        .map(resourceModuleRegistry::getModuleContainingResourcesOf)
        .filter(Objects::nonNull)
        .collect(toImmutableList());
  }

  @Override
  public Triple<List<GradleCoordinate>, List<GradleCoordinate>, String>
      analyzeDependencyCompatibility(List<GradleCoordinate> dependenciesToAdd) {
    return new Triple<>(ImmutableList.of(), dependenciesToAdd, "");
  }

  @Override
  @Nullable
  public String getPackageName() {
    return PackageNameUtils.getPackageName(module);
  }

  @Override
  public ManifestOverrides getManifestOverrides() {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return new ManifestOverrides();
    }
    TargetKey targetKey = AndroidResourceModuleRegistry.getInstance(project).getTargetKey(module);
    if (targetKey == null) {
      return new ManifestOverrides();
    }
    TargetIdeInfo target = projectData.getTargetMap().get(targetKey);

    if (target == null || target.getAndroidIdeInfo() == null) {
      return new ManifestOverrides();
    }
    Map<String, String> manifestValues = target.getAndroidIdeInfo().getManifestValues();
    ImmutableMap.Builder<ManifestSystemProperty, String> directOverrides = ImmutableMap.builder();
    ImmutableMap.Builder<String, String> placeholders = ImmutableMap.builder();
    manifestValues.forEach(
        (key, value) ->
            ManifestValueProcessor.processManifestValue(key, value, directOverrides, placeholders));
    return new ManifestOverrides(directOverrides.buildOrThrow(), placeholders.buildOrThrow());
  }

  @Override
  public GlobalSearchScope getResolveScope(ScopeType scopeType) {
    // Bazel projects have either a workspace module, or a resource module. In both cases, we just
    // want to ignore the currently specified module level dependencies and use the global set of
    // dependencies. This is because when we artificially split up the Java code (into workspace
    // module) and resources (into a separate module each), we introduce a circular dependency,
    // which essentially means that all modules end up depending on all other modules. If we
    // expressed this circular dependency, IntelliJ blows up due to the large heavily connected
    // dependency graph. Instead, we just redirect the scopes in the few places that we need.
    return ProjectScope.getAllScope(module.getProject());
  }

  @Override
  public boolean getUsesCompose() {
    return ComposeStatusProvider.isComposeEnabled(project);
  }

  public Collection<ExternalAndroidLibrary> getDependentLibraries() {
    if (Blaze.getProjectType(project) == ProjectType.QUERY_SYNC) {
      ProjectProto.Project projectProto =
          QuerySyncManager.getInstance(project)
              .getLoadedProject()
              .map(QuerySyncProject::getSnapshotHolder)
              .flatMap(SnapshotHolder::getCurrent)
              .map(QuerySyncProjectSnapshot::project)
              .orElse(null);
      if (projectProto == null) {
        return ImmutableList.of();
      }
      ImmutableList<ProjectProto.Module> matchingModules =
          projectProto.getModulesList().stream()
              .filter(m -> m.getName().equals(module.getName()))
              .collect(ImmutableList.toImmutableList());
      if (matchingModules.isEmpty()) {
        return ImmutableList.of();
      }
      return Iterables.getOnlyElement(matchingModules).getAndroidExternalLibrariesList().stream()
          .map(this::fromProto)
          .collect(toImmutableList());
    }
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();

    if (blazeProjectData == null) {
      return ImmutableList.of();
    }

    if (isWorkspaceModule) {
      return SyncCache.getInstance(project)
          .get(BazelModuleSystem.class, BlazeModuleSystemBase::getLibrariesForWorkspaceModule);
    }

    AndroidResourceModuleRegistry registry = AndroidResourceModuleRegistry.getInstance(project);
    TargetIdeInfo target = blazeProjectData.getTargetMap().get(registry.getTargetKey(module));
    if (target == null) {
      // this can happen if the module points to the <android-resources>, <project-data-dir>
      // <project-data-dir> does not contain any resource
      // <android-resources> contains all external resources as module's local resources, so there's
      // no dependent libraries
      return ImmutableList.of();
    }

    BlazeAndroidSyncData androidSyncData =
        blazeProjectData.getSyncState().get(BlazeAndroidSyncData.class);
    if (androidSyncData == null) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<ExternalAndroidLibrary> libraries = ImmutableList.builder();
    ArtifactLocationDecoder decoder = blazeProjectData.getArtifactLocationDecoder();
    ExternalLibraryInterner externalLibraryInterner = ExternalLibraryInterner.getInstance(project);
    for (String libraryKey : registry.get(module).resourceLibraryKeys) {
      ImmutableMap<String, AarLibrary> aarLibraries = androidSyncData.importResult.aarLibraries;
      ExternalAndroidLibrary externalLibrary =
          toExternalLibrary(project, aarLibraries.get(libraryKey), decoder);
      if (externalLibrary != null) {
        libraries.add(externalLibraryInterner.intern(externalLibrary));
      }
    }
    return libraries.build();
  }

  private ExternalAndroidLibrary fromProto(ProjectProto.ExternalAndroidLibrary proto) {
    ExternalLibraryImpl lib =
        new ExternalLibraryImpl(proto.getName())
            .withLocation(toPathString(proto.getLocation()))
            .withManifestFile(toPathString(proto.getManifestFile()))
            .withResFolder(new SelectiveResourceFolder(toPathString(proto.getResFolder()), null))
            .withSymbolFile(toPathString(proto.getSymbolFile()));
    if (!proto.getPackageName().isEmpty()) {
      lib = lib.withPackageName(proto.getPackageName());
    }
    return lib;
  }

  private PathString toPathString(ProjectProto.ProjectPath projectPath) {
    return new PathString(pathResolver.resolve(ProjectPath.create(projectPath)));
  }

  private static ImmutableList<ExternalAndroidLibrary> getLibrariesForWorkspaceModule(
      Project project, BlazeProjectData blazeProjectData) {
    ArtifactLocationDecoder decoder = blazeProjectData.getArtifactLocationDecoder();
    ExternalLibraryInterner externalLibraryInterner = ExternalLibraryInterner.getInstance(project);
    ImmutableList.Builder<ExternalAndroidLibrary> libraries = ImmutableList.builder();
    for (BlazeLibrary library :
        BlazeLibraryCollector.getLibraries(
            ProjectViewManager.getInstance(project).getProjectViewSet(), blazeProjectData)) {
      if (library instanceof AarLibrary) {
        ExternalAndroidLibrary externalLibrary =
            toExternalLibrary(project, (AarLibrary) library, decoder);
        if (externalLibrary != null) {
          libraries.add(externalLibraryInterner.intern(externalLibrary));
        }
      }
    }
    return libraries.build();
  }

  @Nullable
  static ExternalAndroidLibrary toExternalLibrary(
      Project project, @Nullable AarLibrary library, ArtifactLocationDecoder decoder) {
    if (library == null) {
      return null;
    }
    UnpackedAars unpackedAars = UnpackedAars.getInstance(project);
    File aarFile = unpackedAars.getAarDir(decoder, library);
    if (aarFile == null) {
      logger.warn(
          String.format(
              "Fail to locate AAR file %s. Re-sync the project may solve the problem",
              library.aarArtifact));
      return null;
    }
    File resFolder = unpackedAars.getResourceDirectory(decoder, library);
    PathString resFolderPathString = resFolder == null ? null : new PathString(resFolder);
    return new ExternalLibraryImpl(library.key.toString())
        .withLocation(new PathString(aarFile))
        .withManifestFile(
            resFolderPathString == null
                ? null
                : resFolderPathString.getParentOrRoot().resolve("AndroidManifest.xml"))
        .withResFolder(
            resFolderPathString == null
                ? null
                : new SelectiveResourceFolder(resFolderPathString, null))
        .withSymbolFile(
            resFolderPathString == null
                ? null
                : resFolderPathString.getParentOrRoot().resolve("R.txt"))
        .withPackageName(library.resourcePackage);
  }

  @Override
  public Collection<ExternalAndroidLibrary> getAndroidLibraryDependencies(
      DependencyScopeType dependencyScopeType) {
    if (dependencyScopeType == DependencyScopeType.MAIN) {
      return getDependentLibraries();
    } else {
      return Collections.emptyList();
    }
  }

  @TestOnly
  public static BazelModuleSystem create(Module module) {
    Preconditions.checkState(ApplicationManager.getApplication().isUnitTestMode());
    return new BazelModuleSystem(module);
  }

  public static BazelModuleSystem getInstance(Module module) {
    return module.getService(BazelModuleSystem.class);
  }
}
