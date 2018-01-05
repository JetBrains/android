/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.ide.common.gradle.model.IdeNativeAndroidProject;
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.*;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.common.VariantSelector;
import com.android.tools.idea.gradle.project.sync.ng.ModuleSetup.ModuleSetupImpl;
import com.android.tools.idea.gradle.project.sync.ng.caching.CachedModuleModels;
import com.android.tools.idea.gradle.project.sync.ng.caching.CachedProjectModels;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.GradleModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.ModuleFinder;
import com.android.tools.idea.gradle.project.sync.setup.module.NdkModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.idea.JavaModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.post.ProjectCleanup;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import static com.android.tools.idea.testing.Facets.createAndAddGradleFacet;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link ModuleSetupImpl}.
 */
public class ModuleSetupImplTest extends IdeaTestCase {
  @Mock private IdeModifiableModelsProvider myModelsProvider;
  @Mock private ModuleFactory myModuleFactory;
  @Mock private GradleModuleSetup myGradleModuleSetup;
  @Mock private AndroidModuleSetup myAndroidModuleSetup;
  @Mock private NdkModuleSetup myNdkModuleSetup;
  @Mock private JavaModuleSetup myJavaModuleSetup;
  @Mock private AndroidModuleProcessor myAndroidModuleProcessor;
  @Mock private VariantSelector myVariantSelector;
  @Mock private ProjectCleanup myProjectCleanup;
  @Mock private ObsoleteModuleDisposer myModuleDisposer;
  @Mock private CachedProjectModels.Factory myCachedProjectModelsFactory;
  @Mock private CachedProjectModels myCachedProjectModels;
  @Mock private IdeNativeAndroidProject.Factory myNativeAndroidProjectFactory;
  @Mock private JavaModuleModelFactory myJavaModuleModelFactory;
  @Mock private ExtraGradleSyncModelsManager myExtraModelsManager;
  @Mock private IdeDependenciesFactory myDependenciesFactory;
  @Mock private ProjectDataNodeSetup myProjectDataNodeSetup;
  @Mock private ModuleFinder.Factory myModulesFinderFactory;
  @Mock private ModuleSetupContext.Factory myModuleSetupContextFactory;
  @Mock private ModuleFinder myModuleFinder;

  private ModuleSetupImpl myModuleSetup;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    when(myModulesFinderFactory.create()).thenReturn(myModuleFinder);

    myModuleSetup = new ModuleSetupImpl(getProject(), myModelsProvider, myExtraModelsManager, myModuleFactory, myGradleModuleSetup,
                                        myAndroidModuleSetup, myNdkModuleSetup, myJavaModuleSetup, myAndroidModuleProcessor,
                                        myVariantSelector, myProjectCleanup, myModuleDisposer, myCachedProjectModelsFactory,
                                        myNativeAndroidProjectFactory, myJavaModuleModelFactory, myDependenciesFactory,
                                        myProjectDataNodeSetup, myModuleSetupContextFactory, myModulesFinderFactory);
  }

  public void testSetUpModulesFromCache() throws Exception {
    // create "app" module, which is an Android module.
    Module appModule = createModule("app");
    AndroidModuleModel appAndroidModel = mock(AndroidModuleModel.class);
    makeGradleModule(appModule);
    GradleModuleModel appGradleModel = mock(GradleModuleModel.class);

    CachedModuleModels cachedAppModels = mock(CachedModuleModels.class);
    when(myCachedProjectModels.findCacheForModule(":app")).thenReturn(cachedAppModels);
    when(cachedAppModels.findModel(AndroidModuleModel.class)).thenReturn(appAndroidModel);
    when(cachedAppModels.findModel(GradleModuleModel.class)).thenReturn(appGradleModel);

    // create "cpp" module.
    Module cppModule = createModule("cpp");
    NdkModuleModel cppNdkModel = mock(NdkModuleModel.class);
    makeGradleModule(cppModule);
    GradleModuleModel cppGradleModel = mock(GradleModuleModel.class);

    CachedModuleModels cachedCppModels = mock(CachedModuleModels.class);
    when(myCachedProjectModels.findCacheForModule(":cpp")).thenReturn(cachedCppModels);
    when(cachedCppModels.findModel(NdkModuleModel.class)).thenReturn(cppNdkModel);
    when(cachedCppModels.findModel(GradleModuleModel.class)).thenReturn(cppGradleModel);

    // create "java" module
    Module javaModule = createModule("java");
    JavaModuleModel javaModel = mock(JavaModuleModel.class);
    makeGradleModule(javaModule);
    GradleModuleModel javaGradleModel = mock(GradleModuleModel.class);

    CachedModuleModels cachedJavaModels = mock(CachedModuleModels.class);
    when(myCachedProjectModels.findCacheForModule(":java")).thenReturn(cachedJavaModels);
    when(cachedJavaModels.findModel(JavaModuleModel.class)).thenReturn(javaModel);
    when(cachedJavaModels.findModel(GradleModuleModel.class)).thenReturn(javaGradleModel);

    EmptyProgressIndicator indicator = new EmptyProgressIndicator();

    ModuleSetupContext appModuleContext = mock(ModuleSetupContext.class);
    when(myModuleSetupContextFactory.create(appModule, myModelsProvider, myModuleFinder, cachedAppModels))
      .thenReturn(appModuleContext);

    ModuleSetupContext cppModuleContext = mock(ModuleSetupContext.class);
    when(myModuleSetupContextFactory.create(cppModule, myModelsProvider, myModuleFinder, cachedCppModels))
      .thenReturn(cppModuleContext);

    ModuleSetupContext javaModuleContext = mock(ModuleSetupContext.class);
    when(myModuleSetupContextFactory.create(javaModule, myModelsProvider, myModuleFinder, cachedJavaModels))
      .thenReturn(javaModuleContext);

    // Invoke the method to test.
    myModuleSetup.setUpModules(myCachedProjectModels, indicator);

    // Verify that the modules were set up from the models in the cache.
    verify(myGradleModuleSetup).setUpModule(appModule, myModelsProvider, appGradleModel);
    verify(myAndroidModuleSetup).setUpModule(appModuleContext, appAndroidModel, true);

    verify(myGradleModuleSetup).setUpModule(cppModule, myModelsProvider, cppGradleModel);
    verify(myNdkModuleSetup).setUpModule(cppModuleContext, cppNdkModel, true);

    verify(myGradleModuleSetup).setUpModule(javaModule, myModelsProvider, javaGradleModel);
    verify(myJavaModuleSetup).setUpModule(javaModuleContext, javaModel, true);
  }

  private static void makeGradleModule(@NotNull Module module) {
    GradleFacet gradleFacet = createAndAddGradleFacet(module);
    gradleFacet.getConfiguration().GRADLE_PROJECT_PATH = ":" + module.getName();
  }
}