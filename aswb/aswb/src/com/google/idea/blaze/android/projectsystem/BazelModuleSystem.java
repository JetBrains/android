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
import static com.google.idea.blaze.qsync.project.QuerySyncProjectDirectory.EXTERNAL_REPOSITORIES;

import com.android.ide.common.repository.WellKnownMavenArtifactId;
import com.android.ide.common.util.PathString;
import com.android.projectmodel.ExternalAndroidLibrary;
import com.android.projectmodel.ExternalLibraryImpl;
import com.android.projectmodel.SelectiveResourceFolder;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.ClassFileFinder;
import com.android.tools.idea.projectsystem.DependencyManagementException;
import com.android.tools.idea.projectsystem.DependencyScopeType;
import com.android.tools.idea.projectsystem.DependencyType;
import com.android.tools.idea.projectsystem.ManifestOverrides;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.android.tools.idea.projectsystem.RegisteredDependencyCompatibilityResult;
import com.android.tools.idea.projectsystem.RegisteredDependencyId;
import com.android.tools.idea.projectsystem.RegisteredDependencyQueryId;
import com.android.tools.idea.projectsystem.RegisteringModuleSystem;
import com.android.tools.idea.projectsystem.SampleDataDirectoryProvider;
import com.android.tools.idea.projectsystem.ScopeType;
import com.android.tools.module.ModuleDependencies;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.android.compose.ComposeStatusProvider;
import com.google.idea.blaze.android.npw.project.BlazeAndroidModuleTemplate;
import com.google.idea.blaze.android.projectsystem.BazelModuleSystem.BlazeRegisteredDependencyId;
import com.google.idea.blaze.android.projectsystem.BazelModuleSystem.BlazeRegisteredDependencyQueryId;
import com.google.idea.blaze.android.sync.model.idea.BlazeAndroidModel;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** Blaze implementation of {@link AndroidModuleSystem}. */
@SuppressWarnings("NullableProblems")
public final class BazelModuleSystem implements AndroidModuleSystem, RegisteringModuleSystem<BlazeRegisteredDependencyQueryId,BlazeRegisteredDependencyId>
{

  /**
   * Experiment to toggle returning a simplified view of resource module dependents to work around
   * b/193680790. See {@link #getDirectResourceModuleDependents} for details.
   */
  @VisibleForTesting
  public static final BoolExperiment returnSimpleDirectResourceDependents =
      new BoolExperiment("aswb.return.simple.direct.resource.dependents", true);

  private static final Logger logger = Logger.getInstance(BazelModuleSystem.class);
  private final Module module;
  private final Project project;
  private final ProjectPath.Resolver pathResolver;
  private volatile BlazeAndroidModel androidModel;
  SampleDataDirectoryProvider sampleDataDirectoryProvider;
  final boolean isWorkspaceModule;

  BazelModuleSystem(Module module) {
    this.module = module;
    this.project = module.getProject();
    Path ideProjectRoot = Path.of(
      BlazeImportSettingsManager.getInstance(project)
        .getImportSettings()
        .getProjectDataDirectory());
    this.pathResolver =
        ProjectPath.Resolver.create(
            WorkspaceRoot.fromProject(project).path(),
            ideProjectRoot,
            ideProjectRoot.resolve(EXTERNAL_REPOSITORIES.getDirectoryName()));
    sampleDataDirectoryProvider = new BlazeSampleDataDirectoryProvider(module);
    isWorkspaceModule = module.getName().equals(BlazeDataStorage.WORKSPACE_MODULE_NAME);
  }

  @Override
  public Module getModule() {
    return module;
  }

  public void setAndroidModel(BlazeAndroidModel androidModel) {
    this.androidModel = androidModel;
  }

  @Override
  public @Nullable AndroidModel getAndroidModel() {
    return androidModel;
  }

  @Override
  public ClassFileFinder getModuleClassFileFinder() {
    return fqcn -> null;
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
  public List<NamedModuleTemplate> getModuleTemplates(@Nullable VirtualFile targetDirectory) {
    return BlazeAndroidModuleTemplate.getTemplates(module, targetDirectory);
  }

  private void doRegisterDependency(DependencyType type) {
    if (type != DependencyType.IMPLEMENTATION) {
      throw new UnsupportedOperationException("Unsupported dependency type in Blaze: " + type);
    }
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return;
    }
    //AndroidResourceModuleRegistry registry = AndroidResourceModuleRegistry.getInstance(project);
    // TODO: Implement for query sync.
    return;
    //
    //// TODO: automagically edit deps instead of just opening the BUILD file?
    //// Need to translate Gradle coordinates into blaze targets.
    //// Will probably need to hardcode for each dependency.
    //FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    //PsiElement buildTargetPsi =
    //    BuildReferenceManager.getInstance(project).resolveLabel(targetIdeInfo.getKey().getLabel());
    //if (buildTargetPsi != null) {
    //  // If we can find a PSI for the target,
    //  // then we can jump straight to the target in the build file.
    //  fileEditorManager.openTextEditor(
    //      new OpenFileDescriptor(
    //          project,
    //          buildTargetPsi.getContainingFile().getVirtualFile(),
    //          buildTargetPsi.getTextOffset()),
    //      true);
    //} else {
    //  // If not, just the build file is good enough.
    //  ArtifactLocation buildFile = targetIdeInfo.getBuildFile();
    //  File buildIoFile =
    //      Preconditions.checkNotNull(
    //          OutputArtifactResolver.resolve(
    //              project, blazeProjectData.getArtifactLocationDecoder(), buildFile),
    //          "Fail to find file %s",
    //          buildFile.getRelativePath());
    //  VirtualFile buildVirtualFile =
    //      VfsUtils.resolveVirtualFile(buildIoFile, /* refreshIfNeeded= */ true);
    //  if (buildVirtualFile != null) {
    //    fileEditorManager.openFile(buildVirtualFile, true);
    //  }
    //}
  }

  @Override
  public void registerDependency(BlazeRegisteredDependencyId id, DependencyType type) {
    // TODO: maybe do something different if this id is an -Unknown- vs -Target-
    doRegisterDependency(type);
  }

  @Nullable
  @Override
  public BlazeRegisteredDependencyId getRegisteredDependency(BlazeRegisteredDependencyQueryId id) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(module.getProject()).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }

    // implement for query sync (not possible now without modules).
    return null;
    //TargetKey resourceModuleKey =
    //    AndroidResourceModuleRegistry.getInstance(module.getProject()).getTargetKey(module);
    //if (resourceModuleKey == null) {
    //  // TODO: decide what constitutes a registered dependency for the .workspace module
    //  return null;
    //}
    //
    //TargetIdeInfo resourceModuleTarget = projectData.getTargetMap().get(resourceModuleKey);
    //if (resourceModuleTarget == null) {
    //  return null;
    //}
    //
    //ImmutableSet<TargetKey> firstLevelDeps =
    //    resourceModuleTarget.getDependencies().stream()
    //        .map(Dependency::getTargetKey)
    //        .collect(toImmutableSet());
    //
    //return id.keys.stream()
    //    .filter(it -> firstLevelDeps.contains(it))
    //    .findFirst()
    //    .map(it -> new BlazeTargetRegisteredDependencyId(it))
    //    .orElse(null);
  }

  @Override
  public BlazeRegisteredDependencyQueryId getRegisteredDependencyQueryId(WellKnownMavenArtifactId id) {
    return new BlazeRegisteredDependencyQueryId(id, locateArtifactsFor(id).toList());
  }

  @Override
  public BlazeRegisteredDependencyId getRegisteredDependencyId(WellKnownMavenArtifactId id) {
    return locateArtifactsFor(id)
        .findFirst()
        .map(it -> (BlazeRegisteredDependencyId) new BlazeTargetRegisteredDependencyId(it))
        .orElse((BlazeRegisteredDependencyId) new BlazeUnknownRegisteredDependencyId(id));
  }

  @Override
  public ListenableFuture<RegisteredDependencyCompatibilityResult<BlazeRegisteredDependencyId>> analyzeDependencyCompatibility(List<? extends BlazeRegisteredDependencyId> dependencies) {
    ImmutableMap<BlazeRegisteredDependencyId,BlazeRegisteredDependencyId> compatible = dependencies.stream()
        .filter(it -> it instanceof BlazeTargetRegisteredDependencyId)
        .collect(ImmutableMap.toImmutableMap(it -> it, it -> it));
    ImmutableList<BlazeRegisteredDependencyId> missing = dependencies.stream()
        .filter(it -> !(it instanceof BlazeTargetRegisteredDependencyId))
        .collect(ImmutableList.toImmutableList());
    RegisteredDependencyCompatibilityResult<BlazeRegisteredDependencyId> result;
    if (missing.isEmpty()) {
      result = new RegisteredDependencyCompatibilityResult<>(compatible, missing, "");
    }
    else {
      result = new RegisteredDependencyCompatibilityResult<>(compatible, missing, "One or more dependencies could not be identified");
    }
    return Futures.immediateFuture(result);
  }

  @Override
  public boolean hasResolvedDependency(WellKnownMavenArtifactId id, DependencyScopeType scope) throws DependencyManagementException {
    return getResolvedTarget(id) != null;
  }

  @Nullable
  private Object getResolvedTarget(WellKnownMavenArtifactId id) {
    // TODO (b/262289199): While there is a way of mapping a gradle coordinate to a target,
    //  that is a very tricky practice that while it could be supported with Query Sync, we
    //  should try to avoid it.  (Maybe we should revisit this now that we do not need to
    //  support arbitrary Gradle coordinates?)
    return null;
  }

  private Stream<Label> locateArtifactsFor(WellKnownMavenArtifactId id) {
    return MavenArtifactLocator.forBuildSystem(Blaze.getBuildSystemName(module.getProject()))
        .stream()
        .map(locator -> locator.labelFor(id))
        .filter(Objects::nonNull);
  }
  /**
   * Currently, the ordering of the returned list of modules is meaningless for the Blaze
   * implementation of this API. This may break legacy callers of {@link
   * org.jetbrains.android.util.AndroidUtils#getAndroidResourceDependencies(Module)}, who may be
   * assuming that the facets are returned in overlay order.
   */
  @Override
  public List<Module> getResourceModuleDependencies() {
    return ImmutableList.of();
    // TODO: no modules in query sync.
    //AndroidResourceModuleRegistry resourceModuleRegistry =
    //    AndroidResourceModuleRegistry.getInstance(project);
    //
    //if (isWorkspaceModule) {
    //  // The workspace module depends on every resource module.
    //  return stream(ModuleManager.getInstance(project).getModules())
    //      .filter(module -> resourceModuleRegistry.get(module) != null)
    //      .collect(toImmutableList());
    //}
    //AndroidResourceModule resourceModule = resourceModuleRegistry.get(module);
    //if (resourceModule == null) {
    //  return ImmutableList.of();
    //}
    //
    //return resourceModule.transitiveResourceDependencies.stream()
    //    .map(resourceModuleRegistry::getModuleContainingResourcesOf)
    //    .filter(Objects::nonNull)
    //    .collect(toImmutableList());
  }

  @Override
  public List<Module> getDirectResourceModuleDependents() {
    return ImmutableList.of();
    // TODO: no modules in query sync.
    //if (returnSimpleDirectResourceDependents.getValue()) {
    //  // Returns a simplified view of resource dependencies to work around b/193680790. AS2020.3
    //  // assumes an acyclic graph when iterating over dependents of a module, but ASwB has cyclic
    //  // module dependents. This implementation returns the workspace module as the only dependent
    //  // of resource modules and no module is exposed as a dependent of the workspace module,
    //  // effectively creating a star graph with workspace module in the center.
    //  // #as203: This can be removed when as203 is paved.
    //  if (isWorkspaceModule) {
    //    return ImmutableList.of();
    //  }
    //  Module workspaceModule =
    //      ModuleManager.getInstance(project)
    //          .findModuleByName(BlazeDataStorage.WORKSPACE_MODULE_NAME);
    //  return workspaceModule == null ? ImmutableList.of() : ImmutableList.of(workspaceModule);
    //}
    //
    //BlazeProjectData projectData =
    //    BlazeProjectDataManager.getInstance(module.getProject()).getBlazeProjectData();
    //if (projectData == null) {
    //  return ImmutableList.of();
    //}
    //
    //AndroidResourceModuleRegistry resourceModuleRegistry =
    //    AndroidResourceModuleRegistry.getInstance(module.getProject());
    //TargetKey resourceModuleKey = resourceModuleRegistry.getTargetKey(module);
    //if (resourceModuleKey == null) {
    //  return ImmutableList.of();
    //}
    //
    //return ReverseDependencyMap.get(module.getProject()).get(resourceModuleKey).stream()
    //    .map(projectData.getTargetMap()::get)
    //    .filter(Objects::nonNull)
    //    .map(TargetIdeInfo::getKey)
    //    .map(resourceModuleRegistry::getModuleContainingResourcesOf)
    //    .filter(Objects::nonNull)
    //    .collect(toImmutableList());
  }

  @Override
  @Nullable
  public String getPackageName() {
    return PackageNameUtils.getPackageName(module);
  }

  @Override
  public ManifestOverrides getManifestOverrides() {
    // TODO: b/466506204 - consider implementing when multi-modules.
    return new ManifestOverrides();
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
    ProjectProto.Project projectProto =
      QuerySyncManager.getInstance(project)
        .getCurrentSnapshot()
        .map(QuerySyncProjectSnapshot::getProject)
        .orElse(null);
    if (projectProto == null) {
      return ImmutableList.of();
    }
    ImmutableList<ProjectProto.Module> matchingModules =
        projectProto.getModules().stream()
            .filter(m -> m.getName().equals(module.getName()))
            .collect(ImmutableList.toImmutableList());
    if (matchingModules.isEmpty()) {
      return ImmutableList.of();
    }
    return Iterables.getOnlyElement(matchingModules).getAndroidExternalLibraries().stream()
        .map(this::fromProto)
        .collect(toImmutableList());
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

  private PathString toPathString(ProjectPath projectPath) {
    return new PathString(pathResolver.resolve(projectPath));
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

  @Override
  public ModuleDependencies getModuleDependencies() {
    return new BazelModuleDependencies(getModule());
  }

  public String blazeTargetNameToKotlinModuleName(String blazeTargetName) {
    // Before: //third_party/java_src/android_app/compose_samples/Rally:lib
    // After: third_party_java_src_android_app_compose_samples_Rally_lib
    assert blazeTargetName.substring(0, 2).equals("//");
    return blazeTargetName.substring(2).replaceAll("['/',':']", "_");
  }

  /** Check every supporting extension point if they contain desugaring library config files */
  @Override
  public boolean getDesugarLibraryConfigFilesKnown() {
    return DesugaringLibraryConfigFilesLocator.forBuildSystem(
        Blaze.getBuildSystemName(module.getProject()))
      .stream()
      .anyMatch(provider -> provider.getDesugarLibraryConfigFilesKnown());
  }

  /** Collect desugaring library config files from every supporting extension and return the list */
  public ImmutableList<Path> getDesugarLibraryConfigFiles() {
    return DesugaringLibraryConfigFilesLocator.forBuildSystem(
        Blaze.getBuildSystemName(module.getProject()))
      .stream()
      .flatMap(provider -> provider.getDesugarLibraryConfigFiles(project).stream())
      .collect(toImmutableList());
  }

  @TestOnly
  public static BazelModuleSystem create(Module module) {
    Preconditions.checkState(ApplicationManager.getApplication().isUnitTestMode());
    return new BazelModuleSystem(module);
  }

  public static BazelModuleSystem getInstance(Module module) {
    return module.getService(BazelModuleSystem.class);
  }

  public static class BlazeRegisteredDependencyQueryId implements RegisteredDependencyQueryId {
    WellKnownMavenArtifactId id;
    List<Label> keys;
    BlazeRegisteredDependencyQueryId(WellKnownMavenArtifactId id, List<Label> keys) {
      super();
      this.id = id;
      this.keys = keys;
    }
  }

  abstract public static class BlazeRegisteredDependencyId implements RegisteredDependencyId {
  }
  public static class BlazeTargetRegisteredDependencyId extends BlazeRegisteredDependencyId {
    Label key;
    BlazeTargetRegisteredDependencyId(Label key) {
      super();
      this.key = key;
    }
  }
  public static class BlazeUnknownRegisteredDependencyId extends BlazeRegisteredDependencyId {
    WellKnownMavenArtifactId id;
    BlazeUnknownRegisteredDependencyId(WellKnownMavenArtifactId id) {
      super();
      this.id = id;
    }
  }
}
