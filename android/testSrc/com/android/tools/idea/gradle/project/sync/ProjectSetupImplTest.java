/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync;

import com.android.annotations.Nullable;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.ProjectSetup.ProjectSetupImpl;
import com.android.tools.idea.gradle.project.sync.setup.module.ModuleDisposer;
import com.android.tools.idea.gradle.project.sync.setup.module.ModuleSetup;
import com.android.tools.idea.gradle.project.sync.validation.android.AndroidModuleValidator;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.util.ExceptionUtil.rethrowAllAsUnchecked;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link ProjectSetupImpl}.
 */
public class ProjectSetupImplTest extends AndroidGradleTestCase {
  @Mock private IdeModifiableModelsProvider myModelsProvider;
  @Mock private IdeInfo myIdeInfo;
  @Mock private GradleSyncState mySyncState;
  @Mock private ModuleFactory myModuleFactory;
  @Mock private ModuleSetup myModuleSetup;
  @Mock private AndroidModuleValidator.Factory myAndroidModuleValidatorFactory;
  @Mock private AndroidModuleValidator myAndroidModuleValidator;
  @Mock private ModuleDisposer myModuleDisposer;
  @Mock private ProgressIndicator myProgressIndicator;

  private ProjectSetupImpl myProjectSetup;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    when(myAndroidModuleValidatorFactory.create(project)).thenReturn(myAndroidModuleValidator);

    myProjectSetup = new ProjectSetupImpl(project, myModelsProvider, myIdeInfo, mySyncState, myModuleFactory, myModuleSetup,
                                          myAndroidModuleValidatorFactory, myModuleDisposer);
  }

  public void test() {
    // Fake method.
  }

  // Test fails in build server, but not locally.
  public void /*test*/SetUpProjectWithAndroidModule() throws Exception {
    // Obtain models for 'simpleApplication' from Gradle.
    prepareProjectForImport(SIMPLE_APPLICATION);
    Project project = getProject();

    // Sync with Gradle.
    NewGradleSync gradleSync = new NewGradleSync();

    CountDownLatch latch = new CountDownLatch(1);
    NewGradleSync.Callback sync = gradleSync.sync(project);
    sync.doWhenRejected(() -> {
      latch.countDown();
      Throwable error = sync.getSyncError();
      if (error != null) {
        rethrowAllAsUnchecked(error);
      }
      throw new RuntimeException("Sync failed");
    });
    sync.doWhenDone(latch::countDown);
    if (!sync.isProcessed()) {
      latch.await();
    }

    SyncAction.ProjectModels projectModels = sync.getModels();
    assertNotNull("Gradle models", projectModels);
    Map<String, IdeaModule> moduleModelsByName = getModuleModelsByName(projectModels);
    assertThat(moduleModelsByName).hasSize(2); // 2 modules: root and "app"

    // Simulate ModuleFactory create root module from Gradle models.
    Module rootModule = simulateModelCreation(projectModels, moduleModelsByName.get(project.getName()));

    // Simulate ModuleFactory create "app" module from Gradle models.
    Module appModule = simulateModelCreation(projectModels, moduleModelsByName.get("app"));

    Module[] modules = {rootModule, appModule};
    when(myModelsProvider.getModules()).thenReturn(modules);

    // Add AndroidFacet and AndroidGradleModel to "app" module, to ensure that the validator gets invoked.
    AndroidModuleModel appAndroidModel = mock(AndroidModuleModel.class);
    AndroidFacet appAndroidFacet = ApplicationManager.getApplication().runReadAction(new Computable<AndroidFacet>() {
      @Override
      public AndroidFacet compute() {
        FacetManager facetManager = FacetManager.getInstance(appModule);
        AndroidFacet facet = facetManager.createFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME, null);
        facet.setAndroidModel(appAndroidModel);
        return facet;
      }
    });

    // This happens before AndroidModuleValidator gets invoked. AndroidFacet and AndroidGradleModel need to be set in the module to
    // participate in validation.
    simulateAndroidFacetLookup(rootModule, null);
    simulateAndroidFacetLookup(appModule, appAndroidFacet);

    myProjectSetup.setUpProject(projectModels, myProgressIndicator);

    verify(myModuleSetup).setUpModule(rootModule, projectModels.getModels(":"), myProgressIndicator);
    verify(myModuleSetup).setUpModule(appModule, projectModels.getModels(":app"), myProgressIndicator);

    verify(myAndroidModuleValidator).validate(appModule, appAndroidModel);
    verify(myAndroidModuleValidator).fixAndReportFoundIssues();
  }

  @NotNull
  private static Map<String, IdeaModule> getModuleModelsByName(@NotNull SyncAction.ProjectModels projectModels) {
    Map<String, IdeaModule> moduleModelsByName = new HashMap<>();
    for (IdeaModule module : projectModels.getProject().getModules()) {
      moduleModelsByName.put(module.getName(), module);
    }
    return moduleModelsByName;
  }

  @NotNull
  private Module simulateModelCreation(@NotNull SyncAction.ProjectModels projectModels, @NotNull IdeaModule moduleModel) {
    SyncAction.ModuleModels moduleModels = projectModels.getModels(moduleModel);
    assertNotNull(moduleModel);
    Module module = createModule(moduleModel.getName());
    when(myModuleFactory.createModule(moduleModel, moduleModels)).thenReturn(module);
    return module;
  }

  private void simulateAndroidFacetLookup(@NotNull Module module, @Nullable AndroidFacet facetToFind) {
    ModifiableFacetModel facetModel = mock(ModifiableFacetModel.class);
    when(myModelsProvider.getModifiableFacetModel(module)).thenReturn(facetModel);
    when(facetModel.getFacetByType(AndroidFacet.ID)).thenReturn(facetToFind);
  }
}