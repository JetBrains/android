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

import static com.android.tools.idea.projectsystem.SourceProvidersKt.emptySourceProvider;
import static com.google.idea.blaze.base.sync.data.BlazeDataStorage.WORKSPACE_MODULE_NAME;
import static org.jetbrains.android.facet.SourceProviderUtil.createSourceProvidersForLegacyModule;

import com.android.tools.apk.analyzer.AaptInvoker;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.ClassJarProvider;
import com.android.tools.idea.projectsystem.AndroidProjectSystem;
import com.android.tools.idea.projectsystem.CommonTestType;
import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider;
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.ScopeType;
import com.android.tools.idea.projectsystem.SourceProviderManager;
import com.android.tools.idea.projectsystem.SourceProviders;
import com.android.tools.idea.projectsystem.SourceProvidersFactory;
import com.android.tools.idea.projectsystem.SourceProvidersImpl;
import com.android.tools.idea.res.AndroidInnerClassFinder;
import com.android.tools.idea.res.AndroidResourceClassPsiElementFinder;
import com.android.tools.idea.sdk.AndroidSdks;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.android.resources.BlazeLightResourceClassService;
import com.google.idea.blaze.android.sync.model.idea.BlazeAndroidModel;
import com.google.idea.blaze.android.sync.model.idea.BlazeClassJarProvider;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.search.GlobalSearchScope;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatforms;
import org.jetbrains.annotations.NotNull;

/**
 * Base class to implement common methods in {@link AndroidProjectSystem} for blaze with different
 * sdk
 */
public class BlazeProjectSystem implements AndroidProjectSystem {
  protected final Project project;
  protected final ProjectSystemSyncManager syncManager;
  protected final List<PsiElementFinder> myFinders;
  private final BlazeProjectSystemBuildManager buildManager;

  public BlazeProjectSystem(Project project) {
    this.project = project;
    syncManager = new BlazeProjectSystemSyncManager(project);
    buildManager = new BlazeProjectSystemBuildManager(project);

    myFinders =
        Arrays.asList(
            AndroidInnerClassFinder.INSTANCE,
            new AndroidResourceClassPsiElementFinder(getLightResourceClassService()));
  }

  @Override
  public Project getProject() {
    return project;
  }

  @Override
  public boolean allowsFileCreation() {
    return true;
  }

  @Nullable
  @Override
  public VirtualFile getDefaultApkFile() {
    return null;
  }

  @Override
  public Path getPathToAapt() {
    return AaptInvoker.getPathToAapt(
        AndroidSdks.getInstance().tryToChooseSdkHandler(),
        new LogWrapper(BlazeProjectSystem.class));
  }

  @Override
  public ProjectSystemBuildManager getBuildManager() {
    return buildManager;
  }

  @Override
  public BlazeModuleSystem getModuleSystem(Module module) {
    return BlazeModuleSystem.getInstance(module);
  }

  @Override
  public ProjectSystemSyncManager getSyncManager() {
    return syncManager;
  }

  @Override
  public Collection<PsiElementFinder> getPsiElementFinders() {
    return myFinders;
  }

  @Override
  public BlazeLightResourceClassService getLightResourceClassService() {
    return BlazeLightResourceClassService.getInstance(project);
  }

  @Override
  public SourceProvidersFactory getSourceProvidersFactory() {
    return new SourceProvidersFactory() {
      @Override
      public SourceProviders createSourceProvidersFor(AndroidFacet facet) {
        BlazeAndroidModel model = ((BlazeAndroidModel) AndroidModel.get(facet));
        if (model != null) {
          return createForModel(model);
        } else {
          return createSourceProvidersForLegacyModule(facet);
        }
      }

      private SourceProviders createForModel(BlazeAndroidModel model) {
        NamedIdeaSourceProvider mainSourceProvider = model.getDefaultSourceProvider();
        if (Blaze.getProjectType(project).equals(ProjectType.QUERY_SYNC)) {
          return new SourceProvidersImpl(
              mainSourceProvider,
              ImmutableList.of(mainSourceProvider),
              ImmutableMap.of(CommonTestType.UNIT_TEST, ImmutableList.of()),
              ImmutableMap.of(CommonTestType.ANDROID_TEST, ImmutableList.of()),
              ImmutableList.of(),
              ImmutableList.of(mainSourceProvider),
              ImmutableList.of(mainSourceProvider),
              emptySourceProvider(ScopeType.MAIN),
              ImmutableMap.of(CommonTestType.UNIT_TEST, emptySourceProvider(ScopeType.UNIT_TEST)),
              ImmutableMap.of(
                  CommonTestType.ANDROID_TEST, emptySourceProvider(ScopeType.ANDROID_TEST)),
              emptySourceProvider(ScopeType.TEST_FIXTURES));
        } else {
          return new SourceProvidersImpl(
              mainSourceProvider,
              ImmutableList.of(mainSourceProvider),
              ImmutableMap.of(CommonTestType.UNIT_TEST, ImmutableList.of(mainSourceProvider)),
              ImmutableMap.of(CommonTestType.ANDROID_TEST, ImmutableList.of(mainSourceProvider)),
              ImmutableList.of(mainSourceProvider),
              ImmutableList.of(mainSourceProvider),
              ImmutableList.of(mainSourceProvider),
              emptySourceProvider(ScopeType.MAIN),
              ImmutableMap.of(CommonTestType.UNIT_TEST, emptySourceProvider(ScopeType.UNIT_TEST)),
              ImmutableMap.of(
                  CommonTestType.ANDROID_TEST, emptySourceProvider(ScopeType.ANDROID_TEST)),
              emptySourceProvider(ScopeType.TEST_FIXTURES));
        }
      }
    };
  }

  @Override
  public ClassJarProvider getClassJarProvider() {
    return new BlazeClassJarProvider(project);
  }

  @Override
  public Collection<AndroidFacet> getAndroidFacetsWithPackageName(String packageName) {
    return getAndroidFacetsWithPackageName(
        project, packageName, GlobalSearchScope.projectScope(project));
  }

  private Collection<AndroidFacet> getAndroidFacetsWithPackageName(
      Project project, String packageName, GlobalSearchScope scope) {
    List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
    return facets.stream()
        .filter(facet -> hasPackageName(facet, packageName))
        .filter(
            facet -> {
              VirtualFile file = SourceProviderManager.getInstance(facet).getMainManifestFile();
              if (file == null) {
                return false;
              } else {
                return scope.contains(file);
              }
            })
        .collect(Collectors.toList());
  }

  @Override
  public boolean isNamespaceOrParentPackage(@NotNull String packageName) {
    List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
    GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
    for (AndroidFacet facet : facets) {
      String moduleNamespace = PackageNameUtils.getPackageName(facet.getModule());
      if (moduleNamespace == null) {
        continue;
      }
      // Check if the moduleNamespace is exactly the package name, or is a subpackage
      if (!moduleNamespace.startsWith(packageName)) {
        continue;
      }
      // packageName=com.example should not match moduleNamespace=com.example2
      if (moduleNamespace.length() > packageName.length()
          && moduleNamespace.charAt(packageName.length()) != '.') {
        continue;
      }
      VirtualFile file = SourceProviderManager.getInstance(facet).getMainManifestFile();
      if (file == null || !scope.contains(file)) {
        continue;
      }
      return true;
    }
    return false;
  }

  @NotNull
  @Override
  public Set<String> getKnownApplicationIds() {
    List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
    Set<String> applicationIds = new HashSet<>(facets.size());
    for (AndroidFacet facet : facets) {
      AndroidModel model = AndroidModel.get(facet);
      if (model == null) {
        continue;
      }
      applicationIds.addAll(model.getAllApplicationIds());
    }
    return Collections.unmodifiableSet(applicationIds);
  }

  @NotNull
  @Override
  public Collection<Module> findModulesWithApplicationId(@NotNull String applicationId) {
    if (Blaze.getProjectType(project).equals(ProjectType.QUERY_SYNC)) {
      Module workspaceModule =
          ModuleManager.getInstance(project).findModuleByName(WORKSPACE_MODULE_NAME);
      if (workspaceModule != null) {
        return ImmutableList.of(workspaceModule);
      } else {
        return ImmutableList.of();
      }
    }
    List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
    ImmutableSet.Builder<Module> resultBuilder = ImmutableSet.builder();
    for (AndroidFacet facet : facets) {
      AndroidModel model = AndroidModel.get(facet);
      if (model != null && model.getApplicationId().equals(applicationId)) {
        resultBuilder.add(facet.getModule());
      }
    }
    return resultBuilder.build();
  }

  @Override
  public Collection<Module> getSubmodules() {
    return ImmutableList.of();
  }

  @Override
  public Collection<String> getBootClasspath(@NotNull Module module) {
    return AndroidPlatforms.getInstance(module).getTarget().getBootClasspath();
  }

  @Override
  public boolean isAndroidProject() {
    return ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID);
  }

  private static boolean hasPackageName(AndroidFacet facet, String packageName) {
    String nameFromFacet = PackageNameUtils.getPackageName(facet.getModule());
    if (nameFromFacet == null) {
      return false;
    }
    return nameFromFacet.equals(packageName);
  }
}
