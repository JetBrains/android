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

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.ng.caching.CachedModuleModels;
import com.android.tools.idea.gradle.project.sync.ng.caching.CachedProjectModels;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.GradleModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.ModuleFinder;
import com.android.tools.idea.gradle.project.sync.setup.module.NdkModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.idea.JavaModuleSetup;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.testFramework.JavaProjectTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import static com.android.tools.idea.testing.Facets.createAndAddGradleFacet;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link CachedProjectModelsSetup}.
 */
public class CachedProjectModelsSetupTest extends JavaProjectTestCase {
  @Mock private IdeModifiableModelsProvider myModelsProvider;
  @Mock private GradleModuleSetup myGradleModuleSetup;
  @Mock private AndroidModuleSetup myAndroidModuleSetup;
  @Mock private NdkModuleSetup myNdkModuleSetup;
  @Mock private JavaModuleSetup myJavaModuleSetup;
  @Mock private CachedProjectModels myCachedProjectModels;
  @Mock private ModuleFinder.Factory myModulesFinderFactory;
  @Mock private ModuleFinder myModuleFinder;
  @Mock private CompositeBuildDataSetup myCompositeBuildDataSetup;
  @Mock private ExtraGradleSyncModelsManager myExtraSyncModelsManager;

  private CachedProjectModelsSetup myModuleSetup;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    when(myModulesFinderFactory.create(myProject)).thenReturn(myModuleFinder);

    myModuleSetup =
      new CachedProjectModelsSetup(getProject(), myModelsProvider, myExtraSyncModelsManager, myGradleModuleSetup, myAndroidModuleSetup,
                                   myNdkModuleSetup, myJavaModuleSetup, new ModuleSetupContext.Factory(), myModulesFinderFactory,
                                   myCompositeBuildDataSetup);
  }

  public void testSetUpModulesFromCache() throws Exception {
    // create "app" module, which is an Android module.
    Module appModule = createModule("app");
    AndroidModuleModel appAndroidModel = mock(AndroidModuleModel.class);
    makeGradleModule(appModule);
    GradleModuleModel appGradleModel = mock(GradleModuleModel.class);

    CachedModuleModels cachedAppModels = mock(CachedModuleModels.class);
    when(myCachedProjectModels.findCacheForModule("app")).thenReturn(cachedAppModels);
    when(cachedAppModels.findModel(AndroidModuleModel.class)).thenReturn(appAndroidModel);
    when(cachedAppModels.findModel(GradleModuleModel.class)).thenReturn(appGradleModel);

    // create "cpp" module.
    Module cppModule = createModule("cpp");
    NdkModuleModel cppNdkModel = mock(NdkModuleModel.class);
    makeGradleModule(cppModule);
    GradleModuleModel cppGradleModel = mock(GradleModuleModel.class);

    CachedModuleModels cachedCppModels = mock(CachedModuleModels.class);
    when(myCachedProjectModels.findCacheForModule("cpp")).thenReturn(cachedCppModels);
    when(cachedCppModels.findModel(AndroidModuleModel.class)).thenReturn(appAndroidModel);
    when(cachedCppModels.findModel(NdkModuleModel.class)).thenReturn(cppNdkModel);
    when(cachedCppModels.findModel(GradleModuleModel.class)).thenReturn(cppGradleModel);

    // create "java" module
    Module javaModule = createModule("java");
    JavaModuleModel javaModel = mock(JavaModuleModel.class);
    makeGradleModule(javaModule);
    GradleModuleModel javaGradleModel = mock(GradleModuleModel.class);

    CachedModuleModels cachedJavaModels = mock(CachedModuleModels.class);
    when(myCachedProjectModels.findCacheForModule("java")).thenReturn(cachedJavaModels);
    when(cachedJavaModels.findModel(JavaModuleModel.class)).thenReturn(javaModel);
    when(cachedJavaModels.findModel(GradleModuleModel.class)).thenReturn(javaGradleModel);

    EmptyProgressIndicator indicator = new EmptyProgressIndicator();

    // Invoke the method to test.
    myModuleSetup.setUpModules(myCachedProjectModels, indicator);

    // Verify CompositeBuild data is setup.
    verify(myCompositeBuildDataSetup).setupCompositeBuildData(myCachedProjectModels, myProject);

    // Verify that the modules were set up from the models in the cache.
    verify(myGradleModuleSetup).setUpModule(appModule, myModelsProvider, appGradleModel);
    verify(myAndroidModuleSetup).setUpModule(any(), eq(appAndroidModel), eq(true));

    verify(myGradleModuleSetup).setUpModule(cppModule, myModelsProvider, cppGradleModel);
    verify(myAndroidModuleSetup).setUpModule(any(), eq(appAndroidModel), eq(true));
    verify(myNdkModuleSetup).setUpModule(any(), eq(cppNdkModel), eq(true));

    verify(myGradleModuleSetup).setUpModule(javaModule, myModelsProvider, javaGradleModel);
    verify(myJavaModuleSetup).setUpModule(any(), eq(javaModel), eq(true));
  }

  private static void makeGradleModule(@NotNull Module module) {
    GradleFacet gradleFacet = createAndAddGradleFacet(module);
    gradleFacet.getConfiguration().GRADLE_PROJECT_PATH = ":" + module.getName();
  }
}