/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.project.sync.ng;

import com.android.annotations.NonNull;
import com.android.builder.model.*;
import com.android.ide.common.gradle.model.IdeNativeAndroidProject;
import com.android.ide.common.gradle.model.IdeNativeVariantAbi;
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory;
import com.android.java.model.ArtifactModel;
import com.android.java.model.JavaProject;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.*;
import com.android.tools.idea.gradle.project.sync.GradleModuleModels;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.ng.caching.CachedModuleModels;
import com.android.tools.idea.gradle.project.sync.ng.caching.CachedProjectModels;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.GradleModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.ModuleFinder;
import com.android.tools.idea.gradle.project.sync.setup.module.NdkModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.idea.JavaModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.post.ProjectCleanup;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.util.text.StringUtil;
import org.gradle.tooling.model.GradleProject;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import static com.android.tools.idea.gradle.project.sync.ng.AndroidModuleProcessor.MODULE_GRADLE_MODELS_KEY;
import static com.android.tools.idea.gradle.project.sync.ng.ModuleNameGenerator.deduplicateModuleNames;
import static com.android.tools.idea.gradle.project.sync.setup.Facets.removeAllFacets;
import static com.android.tools.idea.gradle.util.GradleProjects.findModuleRootFolderPath;
import static com.google.common.base.Strings.nullToEmpty;

class SyncProjectModelsSetup extends ModuleSetup<SyncProjectModels> {
  @NotNull private final ModuleFactory myModuleFactory;
  @NotNull private final GradleModuleSetup myGradleModuleSetup;
  @NotNull private final AndroidModuleSetup myAndroidModuleSetup;
  @NotNull private final NdkModuleSetup myNdkModuleSetup;
  @NotNull private final JavaModuleSetup myJavaModuleSetup;
  @NotNull private final AndroidModuleProcessor myAndroidModuleProcessor;
  @NonNull private final AndroidModelFactory myAndroidModelFactory;
  @NotNull private final ProjectCleanup myProjectCleanup;
  @NotNull private final ObsoleteModuleDisposer myModuleDisposer;
  @NotNull private final CachedProjectModels.Factory myCachedProjectModelsFactory;
  @NotNull private final IdeNativeAndroidProject.Factory myNativeAndroidProjectFactory;
  @NotNull private final JavaModuleModelFactory myJavaModuleModelFactory;
  @NotNull private final ExtraGradleSyncModelsManager myExtraModelsManager;
  @NotNull private final IdeDependenciesFactory myDependenciesFactory;
  @NotNull private final ProjectDataNodeSetup myProjectDataNodeSetup;
  @NotNull private final ModuleFinder.Factory myModuleFinderFactory;
  @NotNull private final CompositeBuildDataSetup myCompositeBuildDataSetup;
  @NotNull private final BuildScriptClasspathSetup myBuildScriptClasspathSetup;

  @NotNull private final List<Module> myAndroidModules = new ArrayList<>();

  SyncProjectModelsSetup(@NotNull Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider,
                         @NotNull IdeDependenciesFactory dependenciesFactory,
                         @NotNull ExtraGradleSyncModelsManager extraModelsManager,
                         @NotNull ModuleFactory moduleFactory,
                         @NotNull GradleModuleSetup gradleModuleSetup,
                         @NotNull AndroidModuleSetup androidModuleSetup,
                         @NotNull NdkModuleSetup ndkModuleSetup,
                         @NotNull JavaModuleSetup javaModuleSetup,
                         @NotNull AndroidModuleProcessor androidModuleProcessor,
                         @NonNull AndroidModelFactory androidModelFactory,
                         @NotNull ProjectCleanup projectCleanup,
                         @NotNull ObsoleteModuleDisposer moduleDisposer,
                         @NotNull CachedProjectModels.Factory cachedProjectModelsFactory,
                         @NotNull IdeNativeAndroidProject.Factory nativeAndroidProjectFactory,
                         @NotNull JavaModuleModelFactory javaModuleModelFactory,
                         @NotNull ProjectDataNodeSetup projectDataNodeSetup,
                         @NotNull ModuleSetupContext.Factory moduleSetupFactory,
                         @NotNull ModuleFinder.Factory moduleFinderFactory,
                         @NotNull CompositeBuildDataSetup compositeBuildDataSetup,
                         @NotNull BuildScriptClasspathSetup buildScriptClasspathSetup) {
    super(project, modelsProvider, moduleSetupFactory);
    myModuleFactory = moduleFactory;
    myGradleModuleSetup = gradleModuleSetup;
    myAndroidModuleSetup = androidModuleSetup;
    myAndroidModuleProcessor = androidModuleProcessor;
    myNdkModuleSetup = ndkModuleSetup;
    myAndroidModelFactory = androidModelFactory;
    myProjectCleanup = projectCleanup;
    myModuleDisposer = moduleDisposer;
    myJavaModuleSetup = javaModuleSetup;
    myCachedProjectModelsFactory = cachedProjectModelsFactory;
    myNativeAndroidProjectFactory = nativeAndroidProjectFactory;
    myJavaModuleModelFactory = javaModuleModelFactory;
    myExtraModelsManager = extraModelsManager;
    myDependenciesFactory = dependenciesFactory;
    myProjectDataNodeSetup = projectDataNodeSetup;
    myModuleFinderFactory = moduleFinderFactory;
    myCompositeBuildDataSetup = compositeBuildDataSetup;
    myBuildScriptClasspathSetup = buildScriptClasspathSetup;
  }

  @Override
  public void setUpModules(@NotNull SyncProjectModels projectModels, @NotNull ProgressIndicator indicator) {
    notifyModuleConfigurationStarted(indicator);
    CachedProjectModels cache = myCachedProjectModelsFactory.createNew();
    myCompositeBuildDataSetup.setupCompositeBuildData(projectModels, cache, myProject);
    myDependenciesFactory.setUpGlobalLibraryMap(projectModels.getGlobalLibraryMap());

    // By default, project name is the string entered in Name box when creating new project from wizard.
    // This can be different from the name used by Gradle. For example, entered name is "My Application", Gradle name is "MyApplication10".
    // Make the project use Gradle project name to be consistent with IDEA sync. With IDEA sync, the name was set by ProjectDataServiceImpl::renameProject.
    renameProject(projectModels, myProject);
    // Ensure unique module names.
    deduplicateModuleNames(projectModels, myProject);
    createAndSetUpModules(projectModels, cache);
    myProjectDataNodeSetup.setupProjectDataNode(projectModels, myProject);
    myAndroidModuleProcessor.processAndroidModels(myAndroidModules);
    myProjectCleanup.cleanUpProject(myProject, myModelsProvider, indicator);
    myModuleDisposer.disposeObsoleteModules(indicator);

    cache.saveToDisk(myProject);
  }

  @VisibleForTesting
  static void renameProject(@NotNull SyncProjectModels projectModels, @NotNull Project project) {
    // Rename project if different from project name in Gradle.
    String newName = projectModels.getProjectName();
    if (!StringUtil.equals(newName, project.getName())) {
      ExternalSystemApiUtil.executeProjectChangeAction(true, new DisposeAwareProjectChange(project) {
        @Override
        public void execute() {
          String oldName = project.getName();
          ((ProjectEx)project).setProjectName(newName);
          ExternalSystemApiUtil.getSettings(project, GradleConstants.SYSTEM_ID).getPublisher().onProjectRenamed(oldName, newName);
        }
      });
    }
  }

  // TODO(alruiz): reconcile with https://github.com/JetBrains/intellij-community/commit/6d425f7
  private static final String ROOT_PROJECT_PATH_KEY = "external.root.project.path";

  private void createAndSetUpModules(@NotNull SyncProjectModels projectModels, @NotNull CachedProjectModels cache) {
    populateModuleBuildFolders(projectModels);
    List<ModuleSetupInfo> moduleSetupInfos = new ArrayList<>();

    String projectRootFolderPath = nullToEmpty(myProject.getBasePath());

    ModuleFinder moduleFinder = myModuleFinderFactory.create(myProject);
    for (GradleModuleModels moduleModels : projectModels.getModuleModels()) {
      Module module = myModuleFactory.createModule(moduleModels);

      // This is needed by GradleOrderEnumeratorHandler#addCustomModuleRoots. Without this option, sync will fail.
      //noinspection deprecation
      module.setOption(ROOT_PROJECT_PATH_KEY, projectRootFolderPath);

      CachedModuleModels cachedModels = cache.addModule(module);

      if (!isRootModule(moduleModels)) {
        // Set up GradleFacet right away. This is necessary to set up inter-module dependencies.
        GradleModuleModel gradleModel = myGradleModuleSetup.setUpModule(module, myModelsProvider, moduleModels);
        cachedModels.addModel(gradleModel);
      }
      else {
        removeAllFacets(myModelsProvider.getModifiableFacetModel(module), GradleFacet.getFacetTypeId());
      }
      GradleProject gradleProject = moduleModels.findModel(GradleProject.class);
      assert gradleProject != null;
      moduleFinder.addModule(module, gradleProject.getPath());
      moduleSetupInfos.add(new ModuleSetupInfo(module, moduleModels, cachedModels));
    }

    SetupContextByModuleModel setupContextByModuleModel = new SetupContextByModuleModel();
    // First, create all ModuleModels based on GradleModuleModels.
    for (ModuleSetupInfo moduleSetupInfo : moduleSetupInfos) {
      createModuleModel(moduleSetupInfo, moduleFinder, setupContextByModuleModel);
    }
    // Then, setup the ModuleModels based on the module types.
    setupModuleModels(setupContextByModuleModel, myGradleModuleSetup, myNdkModuleSetup, myAndroidModuleSetup, myJavaModuleSetup,
                      myExtraModelsManager, false /* not skipped */);
    // Setup BuildScript classpath.
    myBuildScriptClasspathSetup.setupBuildScriptClassPath(projectModels, myProject);
  }

  // Returns true if the moduleModel is the one represents root project.
  private static boolean isRootModule(GradleModuleModels moduleModels) {
    ArtifactModel artifactModel = moduleModels.findModel(ArtifactModel.class);
    return artifactModel != null && artifactModel.getArtifactsByConfiguration().isEmpty();
  }

  /**
   * Populate the map from project path to build folder for all modules.
   * It will be used to check if a {@link AndroidLibrary} is sub-module that wraps local aar.
   */
  private void populateModuleBuildFolders(@NotNull SyncProjectModels projectModels) {
    myDependenciesFactory.setRootBuildId(projectModels.getRootBuildId().getRootDir().getAbsolutePath());
    for (GradleModuleModels moduleModels : projectModels.getModuleModels()) {
      GradleProject gradleProject = moduleModels.findModel(GradleProject.class);
      if (gradleProject != null) {
        try {
          String buildId = gradleProject.getProjectIdentifier().getBuildIdentifier().getRootDir().getPath();
          myDependenciesFactory.findAndAddBuildFolderPath(buildId, gradleProject.getPath(), gradleProject.getBuildDirectory());
        }
        catch (UnsupportedOperationException exception) {
          // getBuildDirectory is not available for Gradle older than 2.0.
          // For older versions of gradle, there's no way to get build directory.
        }
      }
    }
  }

  private void createModuleModel(@NotNull ModuleSetupInfo setupInfo,
                                 @NotNull ModuleFinder moduleFinder,
                                 @NotNull SetupContextByModuleModel setupContextByModuleModel) {
    Module module = setupInfo.module;
    GradleModuleModels moduleModels = setupInfo.moduleModels;
    CachedModuleModels cachedModels = setupInfo.cachedModels;

    module.putUserData(MODULE_GRADLE_MODELS_KEY, moduleModels);

    File moduleRootFolderPath = findModuleRootFolderPath(module);
    assert moduleRootFolderPath != null;

    ModuleSetupContext context = myModuleSetupFactory.create(module, myModelsProvider, moduleFinder, moduleModels);

    AndroidProject androidProject = moduleModels.findModel(AndroidProject.class);
    if (androidProject != null) {
      AndroidModuleModel androidModel = myAndroidModelFactory.createAndroidModel(module, androidProject, moduleModels);
      setupContextByModuleModel.androidSetupContexts.put(androidModel, context);
      if (androidModel != null) {
        // "Native" projects also both AndroidProject and AndroidNativeProject
        NativeAndroidProject nativeAndroidProject = moduleModels.findModel(NativeAndroidProject.class);
        if (nativeAndroidProject != null) {
          IdeNativeAndroidProject copy = myNativeAndroidProjectFactory.create(nativeAndroidProject);
          List<NativeVariantAbi> nativeVariantAbi = moduleModels.findModels(NativeVariantAbi.class);
          List<IdeNativeVariantAbi> ideNativeVariantAbi = new ArrayList<>();
          if (nativeVariantAbi != null) {
            ideNativeVariantAbi.addAll(nativeVariantAbi.stream().map(IdeNativeVariantAbi::new).collect(Collectors.toList()));
          }
          NdkModuleModel ndkModel = new NdkModuleModel(module.getName(), moduleRootFolderPath, copy, ideNativeVariantAbi);
          setupContextByModuleModel.ndkSetupContexts.put(ndkModel, context);
          cachedModels.addModel(ndkModel);
        }
        else {
          // Remove any NdkFacet created in previous sync operation.
          removeNdkFacetFrom(module);
        }
        myAndroidModules.add(module);
        cachedModels.addModel(androidModel);
      }
      else {
        // This is an Android module without variants. Treat as a non-buildable Java module.
        removeAndroidFacetFrom(module);
        GradleProject gradleProject = moduleModels.findModel(GradleProject.class);
        assert gradleProject != null;

        Collection<SyncIssue> issues = androidProject.getSyncIssues();
        JavaModuleModel javaModel = myJavaModuleModelFactory.create(gradleProject, androidProject, issues);
        setupContextByModuleModel.javaSetupContexts.put(javaModel, context);
        cachedModels.addModel(javaModel);
      }

      myExtraModelsManager.addAndroidModelsToCache(module, cachedModels);
      return;
    }
    // This is not an Android module. Remove any AndroidFacet set in a previous sync operation.
    removeAndroidFacetFrom(module);
    // This is not an Android module. Remove any AndroidFacet set in a previous sync operation.
    removeAllFacets(myModelsProvider.getModifiableFacetModel(module), NdkFacet.getFacetTypeId());

    // This is a Java module.
    JavaProject javaProject = moduleModels.findModel(JavaProject.class);
    GradleProject gradleProject = moduleModels.findModel(GradleProject.class);
    if (gradleProject != null && javaProject != null) {
      JavaModuleModel javaModel = myJavaModuleModelFactory.create(moduleRootFolderPath, gradleProject,
                                                                  javaProject /* regular Java module */);
      setupContextByModuleModel.javaSetupContexts.put(javaModel, context);
      cachedModels.addModel(javaModel);
      myExtraModelsManager.addJavaModelsToCache(module, cachedModels);
      return;
    }

    // This is a Jar/Aar module or root module.
    ArtifactModel jarAarProject = moduleModels.findModel(ArtifactModel.class);
    if (gradleProject != null && jarAarProject != null) {
      JavaModuleModel javaModel = myJavaModuleModelFactory.create(moduleRootFolderPath, gradleProject, jarAarProject);
      setupContextByModuleModel.javaSetupContexts.put(javaModel, context);
      cachedModels.addModel(javaModel);
    }
  }

  private void removeAndroidFacetFrom(@NotNull Module module) {
    removeAllFacets(myModelsProvider.getModifiableFacetModel(module), AndroidFacet.ID);
    removeNdkFacetFrom(module);
  }

  private void removeNdkFacetFrom(@NotNull Module module) {
    removeAllFacets(myModelsProvider.getModifiableFacetModel(module), NdkFacet.getFacetTypeId());
  }
}
